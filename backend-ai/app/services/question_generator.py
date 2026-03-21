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
        # Clean markdown if present
        if "```" in raw:
            raw = raw.split("```")[1]
            if raw.startswith("json"):
                raw = raw[4:]
        return json.loads(raw.strip())
    except Exception as e:
        logger.error(f"Groq question generation failed: {e}")
        return []