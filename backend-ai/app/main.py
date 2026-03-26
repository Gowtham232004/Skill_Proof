import logging
import os
from contextlib import asynccontextmanager

from google import genai
from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.routers import questions, evaluation

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


def _is_tls_error(error: Exception) -> bool:
    text = str(error).lower()
    return (
        "certificate_verify_failed" in text
        or "unable to get local issuer certificate" in text
        or "ssl" in text
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    strict_startup = os.getenv("AI_STARTUP_STRICT", "false").lower() == "true"

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

    # Verify Groq, but allow non-strict startup to continue when network/TLS fails.
    groq_key = os.getenv("GROQ_API_KEY")
    if not groq_key:
        message = "GROQ_API_KEY is missing in environment"
        if strict_startup:
            logger.error("❌ %s", message)
            raise RuntimeError("GROQ_API_KEY not set in .env")
        logger.warning("⚠️  %s. Service may fail on AI endpoints until key is configured.", message)
    else:
        try:
            from groq import Groq
            groq_client = Groq(api_key=groq_key)
            groq_test = groq_client.chat.completions.create(
                model="llama-3.1-8b-instant",
                messages=[{"role": "user", "content": "Say OK in one word"}],
                max_tokens=5,
            )
            logger.info(f"✓ Groq verified. Response: {groq_test.choices[0].message.content.strip()}")
        except Exception as e:
            if strict_startup:
                logger.error("❌ Groq validation failed in strict mode: %s", e)
                raise RuntimeError("GROQ_API_KEY not set or invalid in .env")

            if _is_tls_error(e):
                logger.warning(
                    "⚠️  Groq validation skipped due to TLS certificate issue: %s. "
                    "On Windows/corporate networks, configure a trusted CA bundle via "
                    "SSL_CERT_FILE or REQUESTS_CA_BUNDLE.",
                    e,
                )
            else:
                logger.warning(
                    "⚠️  Groq could not be validated at startup: %s. "
                    "Service will continue and retry on requests.",
                    e,
                )

    logger.info("✓ SkillProof AI Service ready on port 8000")
    yield
    logger.info("Shutting down")


app = FastAPI(
    title="SkillProof AI Service",
    version="1.0.0",
    lifespan=lifespan
)


@app.middleware("http")
async def verify_internal_secret(request: Request, call_next):
    if request.url.path.startswith("/internal"):
        expected_secret = os.getenv("INTERNAL_SECRET", "dev-internal-secret-change-me")
        provided_secret = request.headers.get("X-Internal-Secret")
        if provided_secret != expected_secret:
            return JSONResponse(status_code=401, content={"error": "Unauthorized"})
    return await call_next(request)

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
