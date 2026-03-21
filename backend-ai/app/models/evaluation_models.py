from pydantic import BaseModel
from typing import List, Optional


class AnswerToEvaluate(BaseModel):
    question_id: int
    question_text: str
    file_reference: str
    code_context: str          # Actual code the question was about
    answer_text: str           # Developer's answer


class EvaluateAnswersRequest(BaseModel):
    session_id: int
    answers: List[AnswerToEvaluate]
    primary_language: str


class SingleAnswerResult(BaseModel):
    question_id: int
    accuracy_score: int        # 0-10
    depth_score: int           # 0-10
    specificity_score: int     # 0-10
    composite_score: float     # weighted average
    ai_feedback: str           # explanation of score


class SkillScores(BaseModel):
    backend_score: int         # 0-100
    api_design_score: int
    error_handling_score: int
    code_quality_score: int
    documentation_score: int
    overall_score: int


class EvaluateAnswersResponse(BaseModel):
    session_id: int
    results: List[SingleAnswerResult]
    skill_scores: SkillScores
    overall_score: int
    status: str
    error: Optional[str] = None