from pydantic import BaseModel, Field, ConfigDict
from typing import List, Optional


class GenerateQuestionsRequest(BaseModel):
    session_id: int
    code_summary: str          # Full code summary from Spring Boot
    primary_language: str
    frameworks_detected: List[str]
    repo_name: str


class GeneratedQuestion(BaseModel):
    # Pydantic v2: Use validation_alias for input (camelCase from Groq)
    # Serialization uses field names (snake_case for Java)
    model_config = ConfigDict(populate_by_name=True)
    
    question_number: int = Field(..., validation_alias='questionNumber')       # 1-5
    difficulty: str            # EASY, MEDIUM, HARD
    file_reference: str = Field(..., validation_alias='fileReference')        # Specific file this question targets
    question_text: str = Field(..., validation_alias='questionText')         # The actual question


class GenerateQuestionsResponse(BaseModel):
    session_id: int
    questions: List[GeneratedQuestion]
    status: str                # "success" or "error"
    error: Optional[str] = None