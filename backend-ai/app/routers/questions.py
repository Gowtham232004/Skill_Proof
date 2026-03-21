import logging
from fastapi import APIRouter, HTTPException

from app.models.question_models import GenerateQuestionsRequest, GenerateQuestionsResponse
from app.services.question_generator import generate_questions

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
            repo_name=request.repo_name
        )

        return GenerateQuestionsResponse(
            session_id=request.session_id,
            questions=questions,
            status="success"
        )

    except Exception as e:
        logger.error(f"Question generation failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
