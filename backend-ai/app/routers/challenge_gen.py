import json
import logging
import os

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel

from app.services.answer_generator import _call_llm
from app.utils.json_repair import parse_loose_json

router = APIRouter()
logger = logging.getLogger(__name__)
INTERNAL_SECRET = os.getenv("INTERNAL_SECRET", "dev-internal-secret-change-me")
REPO_CHALLENGE_MODEL = os.getenv("REPO_CHALLENGE_MODEL", "llama-3.3-70b-versatile")


class RepoChallengeRequest(BaseModel):
    code_context: str
    file_reference: str
    language: str
    challenge_type: str = "REPO_BUG_FIX"
    variation_seed: int | None = None


CHALLENGE_GEN_PROMPT = """
You are a senior software engineer creating a coding challenge from a developer's own code.

CODE:
{code_context}

FILE: {file_reference}
LANGUAGE: {language}
CHALLENGE TYPE: {challenge_type}
VARIATION SEED: {variation_seed}

Create a challenge that tests understanding of this exact code.
For REPO_BUG_FIX: introduce exactly one subtle bug into the provided function.
For REPO_COMPLETION: remove implementation body but keep the signature.
IMPORTANT: Ensure all JSON string values are properly escaped. Do not emit raw newlines or control characters inside JSON strings.
IMPORTANT: Never use JavaScript template literals/backticks (`...`) for JSON values. Use standard JSON double-quoted strings only.
IMPORTANT: Keep JSON compact. Do not include unnecessary prose. Keep starter_code and original_code focused to essential code only.
IMPORTANT: The response must be complete and valid JSON under token limits.

Return ONLY valid JSON with this shape:
{{
  "challenge_title": "string",
  "challenge_description": "string",
  "starter_code": "buggy or incomplete code",
  "original_code": "the correct original code",
  "target_function": "name of the function being challenged",
  "test_cases": [
    {{"name": "Test 1", "input": "...", "expectedOutput": "...", "isVisible": true}},
    {{"name": "Test 2", "input": "...", "expectedOutput": "...", "isVisible": false}}
  ],
  "language": "{language}"
}}
"""


def _extract_json(content: str) -> dict:
    return parse_loose_json(
        content=content,
        logger=logger,
        context="Repo challenge",
        fields_with_backticks=(
            "challenge_title",
            "challenge_description",
            "starter_code",
            "original_code",
            "target_function",
        ),
    )


def _build_retry_prompt(base_prompt: str) -> str:
    return (
        base_prompt
        + "\n\nYour previous response was invalid or truncated."
        + " Return ONLY strict JSON (no markdown fences)."
        + " Escape all newlines in strings as \\n."
        + " Keep starter_code <= 120 lines and original_code <= 120 lines."
        + " Keep test_cases between 2 and 5 items."
    )


@router.post("/generate-repo-challenge")
async def generate_repo_challenge(
    request: RepoChallengeRequest,
    x_internal_secret: str = Header(alias="X-Internal-Secret"),
):
    if x_internal_secret != INTERNAL_SECRET:
        raise HTTPException(status_code=401, detail="Unauthorized")

    prompt = CHALLENGE_GEN_PROMPT.format(
        code_context=(request.code_context or "")[:2200],
        file_reference=request.file_reference,
        language=request.language,
        challenge_type=request.challenge_type,
        variation_seed=request.variation_seed if request.variation_seed is not None else 0,
    )

    try:
        raw = _call_llm(
            prompt,
            max_tokens=3600,
            temperature=0.2,
            model=REPO_CHALLENGE_MODEL,
        )
        try:
            parsed = _extract_json(raw)
        except ValueError:
            retry_prompt = _build_retry_prompt(prompt)
            retry_raw = _call_llm(
                retry_prompt,
                max_tokens=3600,
                temperature=0.1,
                model=REPO_CHALLENGE_MODEL,
            )
            parsed = _extract_json(retry_raw)

        if not str(parsed.get("starter_code", "")).strip():
            raise ValueError("starter_code missing")
        if not isinstance(parsed.get("test_cases"), list) or len(parsed.get("test_cases", [])) == 0:
            raise ValueError("test_cases missing")
        return parsed
    except Exception as exc:
        logger.error("Repo challenge generation failed: %s", exc)
        raise HTTPException(status_code=500, detail=f"Generation failed: {str(exc)}")
