import os
import json
import logging
import re
import time
import ast
from groq import Groq
from app.models.evaluation_models import SingleAnswerResult, SkillScores
from app.utils.prompt_templates import ANSWER_EVALUATION_PROMPT, REFERENCE_ANSWER_PROMPT

logger = logging.getLogger(__name__)

_groq_client = None


def _get_groq_client() -> Groq:
    global _groq_client
    if _groq_client is not None:
        return _groq_client

    api_key = os.getenv("GROQ_API_KEY")
    if not api_key:
        raise RuntimeError("GROQ_API_KEY is not set")

    _groq_client = Groq(api_key=api_key)
    return _groq_client


def _call_llm(
    prompt: str,
    max_tokens: int = 220,
    temperature: float = 0.2,
    model: str = "llama-3.1-8b-instant",
) -> str:
    response = _get_groq_client().chat.completions.create(
        model=model,
        messages=[{"role": "user", "content": prompt}],
        temperature=temperature,
        max_tokens=max_tokens,
    )
    return response.choices[0].message.content or ""

# Rate limiting: wait between API calls
REQUEST_DELAY = 0.3  # 300ms between requests

MIN_MEANINGFUL_ANSWER_CHARS = 50
MIN_WORD_COUNT_FOR_DEPTH = 12


def detect_gibberish(text: str) -> bool:
    """Detect if answer is gibberish (random characters, no real words)."""
    text = text.strip()
    
    # Hard reject: too short to be meaningful
    if len(text) < MIN_MEANINGFUL_ANSWER_CHARS:
        return True

    words = text.split()
    if len(words) < 8:
        return True
    
    # Count letters vs non-letters
    letters = sum(1 for c in text if c.isalpha())
    letter_ratio = letters / len(text) if len(text) > 0 else 0
    
    # If less than 40% letters, likely gibberish or garbage
    if letter_ratio < 0.4:
        return True
    
    # Check for repeated patterns (e.g "asdfasdfasdf" or "aaaaaaa")
    if re.search(r'(.{1,3})\1{3,}', text):  # Same 1-3 chars repeated 4+ times
        return True
    
    # Check for very poor spelling patterns (mostly consonants without vowels)
    vowels = sum(1 for c in text.lower() if c in 'aeiou')
    vowel_ratio = vowels / letters if letters > 0 else 0
    
    # Real text has at least 25% vowels among letters (lowered from 30%)
    if vowel_ratio < 0.25 and letters > 10:
        return True
    
    # Check for lack of common words/patterns - if mostly consonant clusters, likely gibberish
    consonant_clusters = len(re.findall(r'[bcdfghjklmnprstvwxz]{3,}', text.lower()))
    if consonant_clusters > len(text) / 20:  # More than 5% of text is consonant clusters
        return True
    
    return False


def extract_code_identifiers(code_context: str) -> set[str]:
    """Extract potential identifiers from code context for specificity checks."""
    if not code_context:
        return set()

    identifiers = set(re.findall(r'\b[A-Za-z_][A-Za-z0-9_]{2,}\b', code_context))
    common_tokens = {
        "public", "private", "class", "function", "return", "const", "let", "var",
        "import", "from", "static", "void", "string", "number", "boolean", "null",
        "true", "false", "this", "that", "with", "your", "answer", "code", "file"
    }
    return {token for token in identifiers if token.lower() not in common_tokens}


def count_identifier_references(answer_text: str, code_context: str) -> int:
    """Count unique code identifiers referenced in the answer text."""
    identifiers = extract_code_identifiers(code_context)
    if not identifiers:
        return 0

    answer_lower = answer_text.lower()
    matched = set()
    for token in identifiers:
        if re.search(rf'\b{re.escape(token.lower())}\b', answer_lower):
            matched.add(token)
    return len(matched)


def apply_quality_guards(accuracy: int,
                         depth: int,
                         specificity: int,
                         answer_text: str,
                         code_context: str) -> tuple[int, int, int, int, int]:
    """Apply deterministic caps so generic answers cannot score too high."""
    word_count = len(answer_text.split())
    identifier_refs = count_identifier_references(answer_text, code_context)

    # Mandatory identifier rule:
    # 0 identifiers => specificity 1-2, 1 identifier => specificity max 4, 2+ normal.
    if identifier_refs == 0:
        specificity = min(specificity, 2)
        depth = min(depth, 3)
        accuracy = min(accuracy, 4)
    elif identifier_refs == 1:
        specificity = min(specificity, 4)
    elif identifier_refs < 2:
        specificity = min(specificity, 4)

    if word_count < MIN_WORD_COUNT_FOR_DEPTH:
        depth = min(depth, 4)

    if word_count < 8:
        accuracy = min(accuracy, 3)
        depth = min(depth, 3)
        specificity = min(specificity, 3)

    return accuracy, depth, specificity, word_count, identifier_refs


def evaluate_answers(questions_and_answers: list):
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
            code_context = qa.get('codeContext', qa.get('code_context', ''))
        else:
            # Pydantic AnswerToEvaluate object
            answer_text = getattr(qa, 'answer_text', '').strip()
            question_id = getattr(qa, 'question_id', 1)
            difficulty = getattr(qa, 'difficulty', 'MEDIUM')
            question_text = getattr(qa, 'question_text', '')
            file_reference = getattr(qa, 'file_reference', 'unknown')
            code_context = getattr(qa, 'code_context', '')

        answer_len = len(answer_text)

        # Hard reject: too short (score 0)
        if answer_len < 10:
            accuracy, depth, specificity = 0, 0, 0
            feedback = "Answer too short to evaluate."
        # Detect gibberish (score 0)
        elif detect_gibberish(answer_text):
            accuracy, depth, specificity = 0, 0, 0
            feedback = "Your answer appears to be gibberish or random characters. Please provide a real answer."
            logger.warning(f"Q{question_id}: Gibberish detected - '{answer_text[:30]}'")
        else:
            prompt = ANSWER_EVALUATION_PROMPT.format(
                question_text=question_text,
                file_reference=file_reference,
                code_context=code_context[:3000],
                answer_text=answer_text,
            )

            accuracy, depth, specificity = 2, 2, 2
            feedback = "AI evaluation failed. Response scored conservatively."

            for attempt in range(2):
                try:
                    # Add delay to avoid rate limiting
                    if attempt == 0:
                        time.sleep(REQUEST_DELAY)
                    
                    response = _get_groq_client().chat.completions.create(
                        model="llama-3.1-8b-instant",
                        messages=[{"role": "user", "content": prompt}],
                        temperature=0.2,
                        max_tokens=160,
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

                    accuracy_raw = scored.get("accuracy_score", scored.get("accuracyScore", 2))
                    depth_raw = scored.get("depth_score", scored.get("depthScore", 2))
                    specificity_raw = scored.get("specificity_score", scored.get("specificityScore", 2))
                    feedback_raw = scored.get("ai_feedback", scored.get("feedback", "Evaluated."))

                    accuracy = min(10, max(0, int(accuracy_raw)))
                    depth = min(10, max(0, int(depth_raw)))
                    specificity = min(10, max(0, int(specificity_raw)))
                    feedback = str(feedback_raw)[:100]

                    accuracy, depth, specificity, word_count, identifier_refs = apply_quality_guards(
                        accuracy,
                        depth,
                        specificity,
                        answer_text,
                        code_context,
                    )
                    feedback = (
                        f"{feedback} "
                        f"(refs={identifier_refs}, words={word_count})"
                    )[:180]

                    logger.debug(f"Q{question_id}: acc={accuracy} dep={depth} spec={specificity}")
                    break  # Success

                except json.JSONDecodeError:
                    if attempt < 1:
                        continue
                except Exception as e:
                    if attempt < 1:
                        logger.debug(f"Q{question_id} retry: {type(e).__name__}")
                        continue
                    logger.warning(f"Q{question_id}: Could not evaluate, using conservative score")

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

    # Calculate skill scores (convert 1-10 scale to 0-100 scale)
    # Each skill gets the average accuracy score from its assigned question(s), scaled to 0-100
    backend_score = int((sum(scores['backend_score']) / max(len(scores['backend_score']), 1)) * 10) if scores['backend_score'] else 0
    api_design_score = int((sum(scores['api_design_score']) / max(len(scores['api_design_score']), 1)) * 10) if scores['api_design_score'] else 0
    error_handling_score = int((sum(scores['error_handling_score']) / max(len(scores['error_handling_score']), 1)) * 10) if scores['error_handling_score'] else 0
    code_quality_score = int((sum(scores['code_quality_score']) / max(len(scores['code_quality_score']), 1)) * 10) if scores['code_quality_score'] else 0
    documentation_score = int((sum(scores['documentation_score']) / max(len(scores['documentation_score']), 1)) * 10) if scores['documentation_score'] else 0

    # Cap all scores at 100
    backend_score = min(100, backend_score)
    api_design_score = min(100, api_design_score)
    error_handling_score = min(100, error_handling_score)
    code_quality_score = min(100, code_quality_score)
    documentation_score = min(100, documentation_score)

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


def generate_reference_answer(question_text: str, file_reference: str, code_context: str) -> dict:
    prompt = REFERENCE_ANSWER_PROMPT.format(
        question_text=(question_text or "").strip(),
        file_reference=(file_reference or "unknown").strip(),
        code_context=(code_context or "")[:3500],
    )

    response = _get_groq_client().chat.completions.create(
        model="llama-3.1-8b-instant",
        messages=[{"role": "user", "content": prompt}],
        temperature=0.2,
        max_tokens=320,
    )

    raw = response.choices[0].message.content.strip()
    start = raw.find('{')
    end = raw.rfind('}') + 1

    parsed = {}
    if start >= 0 and end > start:
        json_str = raw[start:end].strip()
        try:
            parsed = json.loads(json_str)
        except json.JSONDecodeError:
            repaired = re.sub(r'\\(?!["\\/bfnrtu])', r'\\\\', json_str)
            repaired = re.sub(r',\s*([}\]])', r'\1', repaired)
            # Best effort: tolerate python-dict style output using single quotes.
            try:
                parsed = json.loads(repaired)
            except json.JSONDecodeError:
                try:
                    literal = ast.literal_eval(json_str)
                    parsed = literal if isinstance(literal, dict) else {}
                except (ValueError, SyntaxError):
                    parsed = {}

    reference_answer = str(parsed.get("reference_answer", "")).strip()
    checkpoints = parsed.get("review_checkpoints", [])
    if not isinstance(checkpoints, list):
        checkpoints = []

    sanitized_checkpoints = [str(item).strip() for item in checkpoints if str(item).strip()][:5]
    if not reference_answer:
        reference_answer = (
            "A precise reference answer could not be generated automatically. "
            "Use the shown code context and ask the candidate to explain implementation intent, "
            "data flow, and failure handling for this question."
        )

    if not sanitized_checkpoints:
        sanitized_checkpoints = [
            "Did the answer reference real identifiers from the shown code?",
            "Did the answer explain why this implementation was chosen?",
            "Did the answer cover edge cases or failure behavior?",
        ]

    return {
        "reference_answer": reference_answer,
        "review_checkpoints": sanitized_checkpoints,
    }