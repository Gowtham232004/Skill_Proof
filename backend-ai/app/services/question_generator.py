import os
import json
import logging
from groq import Groq
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

client = Groq(api_key=os.getenv("GROQ_API_KEY"))

def _validate_questions(questions: list) -> bool:
    """Check if questions are specific and code-grounded, not generic."""
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
    
    if not questions or len(questions) < 5:
        logger.warning(f"Validation failed: Expected 5 questions, got {len(questions)}")
        return False
    
    for i, q in enumerate(questions):
        q_text = q.get("question_text", "").lower()
        
        # Check for forbidden patterns
        for phrase in forbidden_phrases:
            if phrase in q_text:
                logger.warning(f"Question {i+1} contains forbidden phrase: '{phrase}'")
                return False
        
        # Check for file reference specificity
        file_ref = q.get("file_reference", "").strip()
        if not file_ref or file_ref.lower() in ["project", "unknown", "file.txt", "code"]:
            logger.warning(f"Question {i+1} has vague file reference: '{file_ref}'")
            return False
        
        # Check minimum length (generic short questions are less likely)
        if len(q_text) < 30:
            logger.warning(f"Question {i+1} is too short ({len(q_text)} chars)")
            return False
    
    logger.info(f"✓ Questions passed validation: {len(questions)} specific, code-grounded questions")
    return True


def generate_questions(code_summary: str, repo_name: str, frameworks: list, primary_language: str = "Unknown") -> list:
    prompt = f"""You are a senior technical interviewer. Analyze this code and generate exactly 5 interview questions.

Repository: {repo_name}
Primary Language: {primary_language}
Frameworks: {', '.join(frameworks) if frameworks else 'Unknown'}

CODE SUMMARY:
{code_summary[:8000]}

Generate 5 questions that test genuine understanding of THIS specific code.
Each question must reference a specific file, function, or decision visible in the code above.

Return ONLY valid JSON array, no other text:
[
  {{
    "questionNumber": 1,
    "difficulty": "EASY",
    "fileReference": "specific_file.ts",
    "questionText": "question about specific code..."
  }}
]
Difficulties: 2 EASY, 2 MEDIUM, 1 HARD."""

    max_retries = 2
    for attempt in range(max_retries):
        try:
            response = client.chat.completions.create(
                model="llama-3.1-8b-instant",
                messages=[{"role": "user", "content": prompt}],
                temperature=0.7,
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
            parsed = json.loads(raw.strip())
            
            # Normalize field names: camelCase → snake_case (Groq returns camelCase)
            for q in parsed:
                # Map Groq's camelCase field names to snake_case for consistent handling
                if 'questionNumber' in q:
                    q['question_number'] = q.pop('questionNumber')
                if 'fileReference' in q:
                    q['file_reference'] = q.pop('fileReference')
                if 'questionText' in q:
                    q['question_text'] = q.pop('questionText')
                
                # Ensure all required fields exist with defaults
                q.setdefault('question_number', 0)
                q.setdefault('file_reference', '')
                q.setdefault('question_text', '')
                q.setdefault('difficulty', 'MEDIUM')
            
            # Validate question quality
            if _validate_questions(parsed):
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