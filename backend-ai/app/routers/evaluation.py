import logging
from fastapi import APIRouter, HTTPException

from app.models.evaluation_models import (
    EvaluateAnswersRequest,
    EvaluateAnswersResponse,
    GenerateReferenceAnswerRequest,
    GenerateReferenceAnswerResponse,
)
from app.services.answer_generator import evaluate_answers, generate_reference_answer

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/evaluate-answers", response_model=EvaluateAnswersResponse)
async def evaluate_answers_endpoint(request: EvaluateAnswersRequest):
    """
    Called by Spring Boot after developer submits all 5 answers.
    Evaluates each answer against the code context and returns scores.
    """
    logger.info(
        f"Evaluating {len(request.answers)} answers "
        f"for session {request.session_id}"
    )

    try:
        # Evaluate each answer against its own code_context
        results, skill_scores = evaluate_answers(request.answers)

        return EvaluateAnswersResponse(
            session_id=request.session_id,
            results=results,
            skill_scores=skill_scores,
            overall_score=skill_scores.overall_score,
            status="success"
        )

    except Exception as e:
        logger.error(f"Answer evaluation failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/generate-reference-answer", response_model=GenerateReferenceAnswerResponse)
async def generate_reference_answer_endpoint(request: GenerateReferenceAnswerRequest):
    logger.info("Generating reference answer for file %s", request.file_reference)

    try:
        result = generate_reference_answer(
            question_text=request.question_text,
            file_reference=request.file_reference,
            code_context=request.code_context,
        )

        return GenerateReferenceAnswerResponse(
            reference_answer=result["reference_answer"],
            review_checkpoints=result["review_checkpoints"],
            status="success",
        )
    except Exception as e:
        logger.error("Reference answer generation failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))