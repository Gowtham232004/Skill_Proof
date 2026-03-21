import os
import json
import logging
import re
import time
from groq import Groq
from app.models.evaluation_models import SingleAnswerResult, SkillScores

logger = logging.getLogger(__name__)

client = Groq(api_key=os.getenv("GROQ_API_KEY"))

# Rate limiting: wait between API calls
REQUEST_DELAY = 0.3  # 300ms between requests


def detect_gibberish(text: str) -> bool:
    """Detect if answer is gibberish (random characters, no real words)."""
    if len(text) < 10:
        return False
    
    # Count letters vs non-letters
    letters = sum(1 for c in text if c.isalpha())
    letter_ratio = letters / len(text) if len(text) > 0 else 0
    
    # If less than 30% letters, likely gibberish
    if letter_ratio < 0.3:
        return True
    
    # Check for repeated patterns (e.g. "asdfasdfasdf" or "aaaaaaa")
    if re.search(r'(.{1,3})\1{3,}', text):  # Same 1-3 chars repeated 4+ times
        return True
    
    # Check for very poor spelling patterns (mostly consonants without vowels)
    vowels = sum(1 for c in text.lower() if c in 'aeiou')
    vowel_ratio = vowels / letters if letters > 0 else 0
    
    # Real text has at least 20% vowels among letters
    if vowel_ratio < 0.2 and letters > 20:
        return True
    
    return False


def evaluate_answers(questions_and_answers: list, code_summary: str):
    """Evaluate answers and return (results, skill_scores) tuple."""
    results = []
    scores = {
        'backend_score': [],
        'api_design_score': [],
        'error_handling_score': [],
        'code_quality_score': [],
        'documentation_score': []
    }

    for qa in questions_and_answers:
        # Handle both dict and Pydantic object
        if isinstance(qa, dict):
            answer_text = qa.get('answerText', '').strip()
            question_id = qa.get('questionId', qa.get('question_id', 1))
            difficulty = qa.get('difficulty', 'MEDIUM')
            question_text = qa.get('questionText', qa.get('question_text', ''))
            file_reference = qa.get('fileReference', qa.get('file_reference', 'unknown'))
        else:
            # Pydantic AnswerToEvaluate object
            answer_text = getattr(qa, 'answer_text', '').strip()
            question_id = getattr(qa, 'question_id', 1)
            difficulty = getattr(qa, 'difficulty', 'MEDIUM')
            question_text = getattr(qa, 'question_text', '')
            file_reference = getattr(qa, 'file_reference', 'unknown')

        answer_len = len(answer_text)

        # Hard reject: too short
        if answer_len < 10:
            accuracy, depth, specificity = 1, 1, 1
            feedback = "Answer too short to evaluate."
        # Detect gibberish
        elif detect_gibberish(answer_text):
            accuracy, depth, specificity = 1, 1, 1
            feedback = "Your answer appears to be gibberish or random characters. Please provide a real answer."
            logger.warning(f"Q{question_id}: Gibberish detected - '{answer_text[:30]}'")
        else:
            prompt = f"""Grade this code answer strictly 1-10 on three criteria.

QUESTION: {question_text}
ANSWER: {answer_text}
CODE: {code_summary[:1500]}

Return ONLY JSON:
{{"accuracyScore": 5, "depthScore": 5, "specificityScore": 5, "feedback": "brief comment"}}"""

            accuracy, depth, specificity = 5, 5, 5  # Changed default to 5 (neutral)
            feedback = "Unable to evaluate with AI."

            for attempt in range(2):
                try:
                    # Add delay to avoid rate limiting
                    if attempt == 0:
                        time.sleep(REQUEST_DELAY)
                    
                    response = client.chat.completions.create(
                        model="llama-3.1-8b-instant",
                        messages=[{"role": "user", "content": prompt}],
                        temperature=0.2,
                        max_tokens=80,
                    )
                    raw = response.choices[0].message.content.strip()

                    if not raw or len(raw) < 5:
                        logger.debug(f"Q{question_id} attempt {attempt + 1}: empty/short response")
                        continue

                    # Extract JSON object
                    start = raw.find('{')
                    end = raw.rfind('}') + 1
                    
                    if start < 0 or end <= start:
                        logger.debug(f"Q{question_id} attempt {attempt + 1}: no JSON in response")
                        continue

                    json_str = raw[start:end].strip()
                    scored = json.loads(json_str)

                    accuracy = min(10, max(1, int(scored.get("accuracyScore", 5))))
                    depth = min(10, max(1, int(scored.get("depthScore", 5))))
                    specificity = min(10, max(1, int(scored.get("specificityScore", 5))))
                    feedback = str(scored.get("feedback", "Evaluated."))[:100]

                    logger.debug(f"Q{question_id}: acc={accuracy} dep={depth} spec={specificity}")
                    break  # Success

                except json.JSONDecodeError:
                    if attempt < 1:
                        continue
                except Exception as e:
                    if attempt < 1:
                        logger.debug(f"Q{question_id} retry: {type(e).__name__}")
                        continue
                    logger.warning(f"Q{question_id}: Could not evaluate, using neutral score")

        # Create result object
        result = SingleAnswerResult(
            question_id=question_id,
            accuracy_score=accuracy,
            depth_score=depth,
            specificity_score=specificity,
            composite_score=(accuracy * 0.4 + depth * 0.3 + specificity * 0.3),
            ai_feedback=feedback
        )
        results.append(result)

        # Distribute scores across skill categories
        if question_id == 1:
            scores['backend_score'].append(accuracy)
        elif question_id == 2:
            scores['api_design_score'].append(accuracy)
        elif question_id == 3:
            scores['error_handling_score'].append(accuracy)
        elif question_id == 4:
            scores['code_quality_score'].append(accuracy)
        else:
            scores['documentation_score'].append(accuracy)

    # Calculate skill scores
    backend_score = int(sum(scores['backend_score']) / max(len(scores['backend_score']), 1)) if scores['backend_score'] else 3
    api_design_score = int(sum(scores['api_design_score']) / max(len(scores['api_design_score']), 1)) if scores['api_design_score'] else 3
    error_handling_score = int(sum(scores['error_handling_score']) / max(len(scores['error_handling_score']), 1)) if scores['error_handling_score'] else 3
    code_quality_score = int(sum(scores['code_quality_score']) / max(len(scores['code_quality_score']), 1)) if scores['code_quality_score'] else 3
    documentation_score = int(sum(scores['documentation_score']) / max(len(scores['documentation_score']), 1)) if scores['documentation_score'] else 3

    overall = int((backend_score + api_design_score + error_handling_score + code_quality_score + documentation_score) / 5)

    skill_scores = SkillScores(
        backend_score=backend_score,
        api_design_score=api_design_score,
        error_handling_score=error_handling_score,
        code_quality_score=code_quality_score,
        documentation_score=documentation_score,
        overall_score=overall
    )

    logger.info(f"Evaluation complete: {len(results)} answers, overall={overall}")
    return results, skill_scores