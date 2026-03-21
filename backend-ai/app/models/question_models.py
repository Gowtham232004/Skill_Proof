from pydantic import BaseModel
from typing import List, Optional


class GenerateQuestionsRequest(BaseModel):
    session_id: int
    code_summary: str          # Full code summary from Spring Boot
    primary_language: str
    frameworks_detected: List[str]
    repo_name: str


class GeneratedQuestion(BaseModel):
    question_number: int       # 1-5
    difficulty: str            # EASY, MEDIUM, HARD
    file_reference: str        # Specific file this question targets
    question_text: str         # The actual question


class GenerateQuestionsResponse(BaseModel):
    session_id: int
    questions: List[GeneratedQuestion]
    status: str                # "success" or "error"
    error: Optional[str] = None