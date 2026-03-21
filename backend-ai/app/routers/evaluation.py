import logging
from fastapi import APIRouter, HTTPException

from app.models.evaluation_models import EvaluateAnswersRequest, EvaluateAnswersResponse
from app.services.answer_generator import evaluate_answers

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
        # Use code_context from first answer as the summary for all evaluations
        code_summary = request.answers[0].code_context if request.answers else ""
        results, skill_scores = evaluate_answers(request.answers, code_summary)

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