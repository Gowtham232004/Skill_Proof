import os
import json
import logging
import re
from groq import Groq
from dotenv import load_dotenv

from app.utils.prompt_templates import HYBRID_QUESTION_GENERATION_PROMPT, FOLLOWUP_QUESTION_PROMPT

load_dotenv()

logger = logging.getLogger(__name__)

client = Groq(api_key=os.getenv("GROQ_API_KEY"))

ALLOWED_TYPES = {"CODE_GROUNDED", "CONCEPTUAL"}


def _safe_json_loads(raw_json: str):
    try:
        return json.loads(raw_json)
    except json.JSONDecodeError:
        repaired = re.sub(r'\\(?!["\\/bfnrtu])', r'\\\\', raw_json)
        repaired = re.sub(r',\s*([}\]])', r'\1', repaired)
        return json.loads(repaired)


def _extract_json_object(raw: str) -> dict:
    text = raw.strip()
    if "```" in text:
        start = text.find("```") + 3
        end = text.rfind("```")
        text = text[start:end].strip()
        if text.startswith("json"):
            text = text[4:].strip()

    left = text.find("{")
    right = text.rfind("}")
    if left == -1 or right == -1 or left >= right:
        raise ValueError("No JSON object found in Groq response")

    return _safe_json_loads(text[left:right + 1])

def _expected_difficulty(index: int, total_questions: int) -> str:
    if total_questions <= 5:
        if index <= 2:
            return "EASY"
        if index <= 4:
            return "MEDIUM"
        return "HARD"

    if index <= 2:
        return "EASY"
    if index <= 5:
        return "MEDIUM"
    return "HARD"


def _validate_questions(questions: list, total_questions: int, conceptual_questions: int) -> bool:
    """Check if questions are specific and follow the requested hybrid mix."""
    # Forbidden generic patterns that indicate weak questions
    forbidden_phrases = [
        "walk me through the overall architecture",
        "walk me through the architecture",
        "what design patterns have you used",
        "what design patterns did you use",
        "what was the most challenging part",
        "how does data flow through your application",
        "if this application needed to handle",
        "what are the main limitations",
        "what would you do to improve",
        "what would you change about",
    ]
    
    if not questions or len(questions) != total_questions:
        logger.warning(
            "Validation failed: Expected %s questions, got %s",
            total_questions,
            len(questions) if questions else 0,
        )
        return False

    expected_code_grounded = max(0, total_questions - max(0, conceptual_questions))
    actual_conceptual = 0
    actual_code_grounded = 0
    
    for i, q in enumerate(questions):
        q_text = q.get("question_text", "").lower()
        q_number = q.get("question_number", i + 1)
        q_type = str(q.get("question_type", "CODE_GROUNDED")).upper()

        if q_type not in ALLOWED_TYPES:
            logger.warning("Question %s has invalid type: %s", q_number, q_type)
            return False

        if q_type == "CONCEPTUAL":
            actual_conceptual += 1
        else:
            actual_code_grounded += 1

        difficulty = str(q.get("difficulty", "")).upper()
        expected_difficulty = _expected_difficulty(q_number, total_questions)
        if difficulty != expected_difficulty:
            logger.warning(
                "Question %s has difficulty %s but expected %s",
                q_number,
                difficulty,
                expected_difficulty,
            )
            return False
        
        # Check for forbidden patterns
        for phrase in forbidden_phrases:
            if phrase in q_text:
                logger.warning(f"Question {i+1} contains forbidden phrase: '{phrase}'")
                return False
        
        # Check for file reference specificity
        file_ref = q.get("file_reference", "").strip()
        if q_type == "CODE_GROUNDED" and (
            not file_ref or file_ref.lower() in ["project", "unknown", "file.txt", "code"]
        ):
            logger.warning(f"Question {i+1} has vague file reference: '{file_ref}'")
            return False
        
        # Check minimum length (generic short questions are less likely)
        if len(q_text) < 30:
            logger.warning(f"Question {i+1} is too short ({len(q_text)} chars)")
            return False

    if actual_conceptual != conceptual_questions:
        logger.warning(
            "Validation failed: expected %s conceptual questions, got %s",
            conceptual_questions,
            actual_conceptual,
        )
        return False

    if actual_code_grounded != expected_code_grounded:
        logger.warning(
            "Validation failed: expected %s code-grounded questions, got %s",
            expected_code_grounded,
            actual_code_grounded,
        )
        return False
    
    logger.info(
        "✓ Questions passed validation: total=%s, code_grounded=%s, conceptual=%s",
        len(questions),
        actual_code_grounded,
        actual_conceptual,
    )
    return True


def generate_questions(
    code_summary: str,
    repo_name: str,
    frameworks: list,
    primary_language: str = "Unknown",
    total_questions: int = 5,
    conceptual_questions: int = 0,
) -> list:
    total_questions = max(5, min(total_questions, 7))
    conceptual_questions = max(0, min(conceptual_questions, total_questions - 1))
    code_grounded_questions = total_questions - conceptual_questions

    prompt = HYBRID_QUESTION_GENERATION_PROMPT.format(
        total_questions=total_questions,
        code_grounded_questions=code_grounded_questions,
        conceptual_questions=conceptual_questions,
        code_summary=code_summary[:8000],
        repo_name=repo_name,
        primary_language=primary_language,
        frameworks=', '.join(frameworks) if frameworks else 'Unknown',
    )

    max_retries = 2
    for attempt in range(max_retries):
        try:
            response = client.chat.completions.create(
                model="llama-3.1-8b-instant",
                messages=[{"role": "user", "content": prompt}],
                temperature=0.9,  # Increased from 0.7 to encourage more variation in questions
                max_tokens=1500,
            )
            raw = response.choices[0].message.content.strip()
            logger.info(f"Raw Groq response (attempt {attempt+1}): {raw[:200]}...")
            
            # Extract JSON (handle both markdown code fences and plain text)
            if "```" in raw:
                # Extract content between triple backticks
                start = raw.find("```") + 3
                end = raw.rfind("```")
                raw = raw[start:end].strip()
                
                # Remove 'json' language identifier if at the start
                if raw.startswith("json"):
                    raw = raw[4:].strip()
            else:
                # No code fences, find JSON array directly
                json_start = raw.find("[")
                json_end = raw.rfind("]")
                if json_start != -1 and json_end != -1 and json_start < json_end:
                    raw = raw[json_start:json_end+1]
                else:
                    logger.error(f"Could not find JSON array in response: {raw[:100]}")
                    raise ValueError("No JSON array found in Groq response")
            
            logger.info(f"Parsed JSON: {raw[:200]}...")
            parsed = _safe_json_loads(raw.strip())
            
            # Normalize field names: camelCase → snake_case (Groq returns camelCase)
            for q in parsed:
                # Map Groq's camelCase field names to snake_case for consistent handling
                if 'questionNumber' in q:
                    q['question_number'] = q.pop('questionNumber')
                if 'fileReference' in q:
                    q['file_reference'] = q.pop('fileReference')
                if 'questionText' in q:
                    q['question_text'] = q.pop('questionText')
                if 'questionType' in q:
                    q['question_type'] = q.pop('questionType')
                
                # Ensure all required fields exist with defaults
                q.setdefault('question_number', 0)
                q.setdefault('file_reference', '')
                q.setdefault('question_text', '')
                q.setdefault('difficulty', 'MEDIUM')
                q.setdefault('question_type', 'CODE_GROUNDED')

                q['question_type'] = str(q['question_type']).upper()
                q['difficulty'] = str(q['difficulty']).upper()
            
            # Validate question quality
            if _validate_questions(parsed, total_questions, conceptual_questions):
                logger.info(f"✓ Successfully generated {len(parsed)} questions (attempt {attempt+1})")
                return parsed
            else:
                # Generic questions detected, retry if attempts remaining
                if attempt < max_retries - 1:
                    logger.warning(f"Generic questions detected, retrying... (attempt {attempt+1}/{max_retries})")
                    continue
                else:
                    logger.warning("Questions failed validation on final attempt, returning anyway")
                    return parsed
                    
        except json.JSONDecodeError as e:
            logger.error(f"Groq question generation failed - JSON parse error (attempt {attempt+1}): {e}")
            logger.error(f"Attempted to parse: {raw[:500]}")
            if attempt == max_retries - 1:
                return []
            continue
        except Exception as e:
            logger.error(f"Groq question generation failed (attempt {attempt+1}): {e}")
            if attempt == max_retries - 1:
                return []
            continue
    
    return []


def generate_followup_question(
    original_question: str,
    file_reference: str,
    code_context: str,
    developer_answer: str,
) -> dict:
    prompt = FOLLOWUP_QUESTION_PROMPT.format(
        original_question=original_question.strip(),
        file_reference=file_reference.strip() or "unknown",
        code_context=(code_context or "")[:3000],
        developer_answer=developer_answer.strip(),
    )

    response = client.chat.completions.create(
        model="llama-3.1-8b-instant",
        messages=[{"role": "user", "content": prompt}],
        temperature=0.2,
        max_tokens=220,
    )

    raw = response.choices[0].message.content.strip()
    parsed = _extract_json_object(raw)

    followup = str(parsed.get("followup_question", "")).strip()
    target = str(parsed.get("targets_identifier", "")).strip()

    if len(followup) < 20:
        raise ValueError("Follow-up question too short or missing")

    if not target:
        # Best-effort fallback extraction from follow-up text to keep contract strict.
        tokens = re.findall(r"\b[A-Za-z_][A-Za-z0-9_]{2,}\b", followup)
        target = tokens[0] if tokens else "unknown_identifier"

    return {
        "followup_question": followup,
        "targets_identifier": target,
    }