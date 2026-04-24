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


class SnippetQuestionRequest(BaseModel):
    code_snippet: str
    file_reference: str
    language: str = "JAVA"


SNIPPET_QUESTION_PROMPT = """
You are a senior software engineer conducting a code comprehension interview.

You are shown a code snippet from a developer's real project:

FILE: {file_reference}
LANGUAGE: {language}

CODE SNIPPET:
{code_snippet}

Generate ONE specific question about this code that:
1. Can ONLY be answered by someone who has read and understood THIS specific code
2. References at least one specific identifier (function name, variable, annotation, class)
3. Asks WHY a decision was made, not just WHAT the code does
4. Is answerable in 2-5 sentences by someone who wrote it

Return ONLY JSON:
{{"question": "your question here"}}
"""


@router.post("/generate-snippet-question")
async def generate_snippet_question(
    request: SnippetQuestionRequest,
    x_internal_secret: str = Header(alias="X-Internal-Secret"),
):
    if x_internal_secret != INTERNAL_SECRET:
        raise HTTPException(status_code=401, detail="Unauthorized")

    prompt = SNIPPET_QUESTION_PROMPT.format(
        file_reference=request.file_reference,
        language=request.language,
        code_snippet=request.code_snippet[:3000],
    )

    try:
        content = _call_llm(prompt).strip()
        result = parse_loose_json(
            content=content,
            logger=logger,
            context="Quick challenge",
            fields_with_backticks=("question",),
        )
        question = str(result.get("question", "")).strip()
        if not question:
            raise ValueError("Missing question field")
        return {"question": question}
    except Exception as ex:
        logger.warning("Snippet question generation failed: %s", ex)
        return {
            "question": "Explain what this specific code does, why it is written this way, and one edge case you considered."
        }
