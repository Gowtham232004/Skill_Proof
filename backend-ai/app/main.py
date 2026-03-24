import logging
import os
from contextlib import asynccontextmanager

from google import genai
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import questions, evaluation

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Try Gemini, but don't fail if quota exhausted
    gemini_key = os.getenv("GEMINI_API_KEY")
    if gemini_key:
        try:
            client = genai.Client(api_key=gemini_key)
            test = client.models.generate_content(
                model="gemini-2.0-flash-lite",
                contents="Say OK in one word"
            )
            logger.info(f"✓ Gemini verified. Response: {test.text.strip()}")
        except Exception as e:
            error_msg = str(e)
            if "429" in error_msg or "RESOURCE_EXHAUSTED" in error_msg:
                logger.warning(f"⚠️  Gemini free tier quota exhausted. Using Groq Llama 3.1 for AI tasks.")
            else:
                logger.warning(f"Gemini unavailable: {str(e)[:100]}. Using Groq Llama 3.1 for AI tasks.")
    else:
        logger.info("✓ Gemini API key not set. Using Groq Llama 3.1 for AI tasks.")

    # Verify Groq is working
    try:
        from groq import Groq
        groq_client = Groq(api_key=os.getenv("GROQ_API_KEY"))
        groq_test = groq_client.chat.completions.create(
            model="llama-3.1-8b-instant",
            messages=[{"role": "user", "content": "Say OK in one word"}],
            max_tokens=5
        )
        logger.info(f"✓ Groq verified. Response: {groq_test.choices[0].message.content.strip()}")
    except Exception as e:
        logger.error(f"❌ Groq key invalid: {e}")
        raise RuntimeError(f"GROQ_API_KEY not set or invalid in .env")

    logger.info("✓ SkillProof AI Service ready on port 8000")
    yield
    logger.info("Shutting down")


app = FastAPI(
    title="SkillProof AI Service",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)

app.include_router(questions.router, prefix="/internal", tags=["Questions"])
app.include_router(evaluation.router, prefix="/internal", tags=["Evaluation"])


@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "service": "skillproof-ai",
        "model": "gemini-2.0-flash-lite"
    }
