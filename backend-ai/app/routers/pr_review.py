import json
import logging
import re
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services.answer_generator import _call_llm
from app.utils.json_repair import parse_loose_json

router = APIRouter()
logger = logging.getLogger(__name__)


class CodeBugRequest(BaseModel):
    code_context: str
    file_reference: str
    language: str = "JAVA"


class PrEvalRequest(BaseModel):
    original_code: str
    modified_code: str
    bug_description: str
    candidate_comments: str


BUG_INTRO_PROMPT = """
You are a senior engineer creating a code review exercise.

Given this real code from a developer's project:
FILE: {file_reference}
LANGUAGE: {language}

ORIGINAL CODE:
{code_context}

Introduce EXACTLY ONE subtle, realistic bug that:
1. Would be caught in a real code review by a senior engineer
2. Does NOT cause a compilation/syntax error (must still be valid {language})
3. Is subtle and realistic

Return ONLY JSON:
{{
  "modified_code": "the complete code with exactly one bug introduced",
  "bug_description": "what line changed and expected behavior",
  "bug_line_hint": 1,
  "bug_type": "comparison_operator|null_check|wrong_variable|off_by_one|missing_return|wrong_status|logic_negation"
}}
"""

PR_EVAL_PROMPT = """
You are a senior engineer evaluating a developer's code review quality.

ORIGINAL (CORRECT) CODE:
{original_code}

MODIFIED CODE (shown to candidate — has one bug):
{modified_code}

GROUND TRUTH BUG:
{bug_description}

CANDIDATE COMMENTS:
{candidate_comments}

Return ONLY JSON:
{{
  "score": 0,
  "bugs_identified": 0,
  "feedback": "2-3 sentences with concrete rationale"
}}
"""


def _build_retry_prompt(prompt: str) -> str:
    return (
        prompt
        + "\n\nYour previous output was invalid JSON."
        + " Return strict JSON only (no markdown)."
        + " Escape all newlines and quotes inside JSON strings."
        + " Keep modified_code concise and syntactically valid."
    )


def _build_eval_retry_prompt(prompt: str) -> str:
    return (
        prompt
        + "\n\nYour previous output was invalid."
        + " Return strict JSON only with keys: score, bugs_identified, feedback."
        + " Do not include markdown, code fences, or extra keys."
        + " score must be integer 0-100; bugs_identified must be integer >= 0."
    )


def _extract_keywords(text: str) -> set[str]:
    tokens = re.findall(r"[A-Za-z_][A-Za-z0-9_]{3,}", (text or "").lower())
    stop = {
        "line", "lines", "null", "true", "false", "this", "that", "with", "from", "have", "should",
        "would", "could", "there", "their", "about", "return", "returns", "class", "method", "code",
        "issue", "minor", "major", "critical", "comment", "comments", "review", "candidate", "bug",
    }
    return {token for token in tokens if token not in stop}


def _heuristic_pr_eval(candidate_comments: str, bug_description: str) -> dict:
    comments = candidate_comments or ""
    lines = [line.strip() for line in comments.splitlines() if line.strip()]
    meaningful = [line for line in lines if len(line) >= 12]

    bug_terms = _extract_keywords(bug_description)
    comment_terms = _extract_keywords("\n".join(meaningful))
    overlap = len(bug_terms.intersection(comment_terms))

    base = min(40, len(meaningful) * 12)
    relevance = min(40, overlap * 10)
    quality_bonus = 15 if any("line" in line.lower() for line in meaningful) else 0
    score = max(0, min(100, base + relevance + quality_bonus))

    bugs_identified = 1 if overlap > 0 else 0
    if not meaningful:
        return {
            "score": 0,
            "bugs_identified": 0,
            "feedback": "No meaningful review comments were provided. Add specific comments that explain the defect and expected behavior.",
        }

    if bugs_identified > 0:
        return {
            "score": score,
            "bugs_identified": bugs_identified,
            "feedback": "Fallback evaluation used because model output was invalid JSON. Your comments show partially relevant bug understanding, but add more precise root-cause details.",
        }

    return {
        "score": min(score, 35),
        "bugs_identified": 0,
        "feedback": "Fallback evaluation used because model output was invalid JSON. The submitted comments do not clearly identify the intended bug.",
    }


def _extract_json(content: str) -> dict:
    return parse_loose_json(
        content=content,
        logger=logger,
        context="PR review",
        fields_with_backticks=("modified_code", "bug_description", "feedback"),
    )


@router.post("/generate-code-bug")
async def generate_code_bug(request: CodeBugRequest):
    prompt = BUG_INTRO_PROMPT.format(
        file_reference=request.file_reference,
        language=request.language,
        code_context=(request.code_context or "")[:4000],
    )

    try:
        raw = _call_llm(prompt, max_tokens=1800, temperature=0.3)
        try:
            parsed = _extract_json(raw)
        except ValueError:
            retry_raw = _call_llm(_build_retry_prompt(prompt), max_tokens=1800, temperature=0.1)
            parsed = _extract_json(retry_raw)
        modified_code = str(parsed.get("modified_code", "")).strip()
        if not modified_code:
            raise ValueError("modified_code missing")
        return parsed
    except Exception as exc:
        logger.error("PR bug generation failed: %s", exc)
        raise HTTPException(status_code=500, detail=f"Bug generation failed: {str(exc)}")


@router.post("/evaluate-pr-review")
async def evaluate_pr_review(request: PrEvalRequest):
    prompt = PR_EVAL_PROMPT.format(
        original_code=(request.original_code or "")[:2200],
        modified_code=(request.modified_code or "")[:2200],
        bug_description=(request.bug_description or "")[:1200],
        candidate_comments=(request.candidate_comments or "")[:1800],
    )

    try:
        raw = _call_llm(prompt, max_tokens=500, temperature=0.2)
        try:
            parsed = _extract_json(raw)
        except ValueError:
            retry_raw = _call_llm(_build_eval_retry_prompt(prompt), max_tokens=260, temperature=0.0)
            parsed = _extract_json(retry_raw)
        return {
            "score": int(parsed.get("score", 0)),
            "bugs_identified": int(parsed.get("bugs_identified", 0)),
            "feedback": str(parsed.get("feedback", "Evaluation unavailable")),
        }
    except Exception as exc:
        logger.warning("PR evaluation fallback activated: %s", exc)
        return _heuristic_pr_eval(request.candidate_comments, request.bug_description)
