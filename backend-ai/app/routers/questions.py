import logging
from fastapi import APIRouter, HTTPException

from app.models.question_models import (
    GenerateFollowUpRequest,
    GenerateFollowUpResponse,
    GenerateQuestionsRequest,
    GenerateQuestionsResponse,
)
from app.services.question_generator import generate_followup_question, generate_questions

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/generate-questions", response_model=GenerateQuestionsResponse)
async def generate_questions_endpoint(request: GenerateQuestionsRequest):
    """
    Called by Spring Boot after fetching repo files.
    Receives code summary, returns 5 grounded questions.
    This endpoint is internal — only Spring Boot calls it.
    """
    logger.info(
        f"Generating questions for session {request.session_id}, "
        f"repo: {request.repo_name}, language: {request.primary_language}"
    ) 

    try:
        questions = generate_questions(
            code_summary=request.code_summary,
            primary_language=request.primary_language,
            frameworks=request.frameworks_detected,
            repo_name=request.repo_name,
            total_questions=request.total_questions,
            conceptual_questions=request.conceptual_questions,
        )

        return GenerateQuestionsResponse(
            session_id=request.session_id,
            questions=questions,
            status="success"
        )

    except Exception as e:
        logger.error(f"Question generation failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/generate-followup", response_model=GenerateFollowUpResponse)
async def generate_followup_endpoint(request: GenerateFollowUpRequest):
    """Generate one targeted follow-up question for weak code-specific answers."""
    logger.info("Generating follow-up question for file %s", request.file_reference)

    try:
        followup = generate_followup_question(
            original_question=request.original_question,
            file_reference=request.file_reference,
            code_context=request.code_context,
            developer_answer=request.developer_answer,
        )

        return GenerateFollowUpResponse(
            followup_question=followup["followup_question"],
            targets_identifier=followup["targets_identifier"],
            status="success",
        )
    except Exception as e:
        logger.error("Follow-up generation failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))
