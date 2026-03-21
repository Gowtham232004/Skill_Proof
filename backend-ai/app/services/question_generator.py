import os
import json
import logging
from groq import Groq
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

client = Groq(api_key=os.getenv("GROQ_API_KEY"))

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

    try:
        response = client.chat.completions.create(
            model="llama-3.1-8b-instant",
            messages=[{"role": "user", "content": prompt}],
            temperature=0.7,
            max_tokens=1500,
        )
        raw = response.choices[0].message.content.strip()
        logger.info(f"Raw Groq response: {raw[:200]}...")  # Log first 200 chars for debugging
        
        # Clean markdown if present
        if "```" in raw:
            # Extract content between first and last backticks
            start = raw.find("```") + 3
            end = raw.rfind("```")
            raw = raw[start:end].strip()
            
            # Remove 'json' language identifier if at the start
            if raw.startswith("json"):
                raw = raw[4:].strip()
        
        logger.info(f"Parsed JSON: {raw[:200]}...")  # Log parsed content
        parsed = json.loads(raw.strip())
        logger.info(f"Successfully generated {len(parsed)} questions")
        
        # Validate and fix field names if needed
        for q in parsed:
            # Ensure all required fields exist
            if 'questionNumber' not in q and 'question_number' in q:
                q['questionNumber'] = q.pop('question_number')
            if 'fileReference' not in q and 'file_reference' in q:
                q['fileReference'] = q.pop('file_reference')
            if 'questionText' not in q and 'question_text' in q:
                q['questionText'] = q.pop('question_text')
        
        return parsed
    except json.JSONDecodeError as e:
        logger.error(f"Groq question generation failed - JSON parse error: {e}")
        logger.error(f"Attempted to parse: {raw[:500]}")
        return []
    except Exception as e:
        logger.error(f"Groq question generation failed: {e}")
        return []