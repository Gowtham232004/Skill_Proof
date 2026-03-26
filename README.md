# SkillProof — Developer Credibility Platform

> **AI-powered portfolio verification. Prove you understand what you built.**

SkillProof connects to your GitHub, analyzes your actual source code, generates questions grounded in your specific codebase, evaluates your answers, and issues a cryptographically signed badge — all in under 2 minutes.

**Live Demo:** `http://localhost:3000`  
**Badge Example:** `http://localhost:3000/badge/sp_1baa55015d1b1a12bc4356967eea53cf`

---

## What It Does

| Pillar | Description | Status |
|---|---|---|
| Portfolio Verification | GitHub OAuth → code analysis → AI questions → HMAC badge | ✅ Built |
| Confidence & Integrity Signals | Confidence tier + tab switches + paste count + avg answer time on badge | ✅ Built |
| Live Coding Challenges | Docker-sandboxed task execution with automated test scoring | 🔧 Prototype |
| Peer Code Review | Expert reviewer matching by tech stack | 🔧 Prototype |
| Skill Gap Analysis | 24 code pattern checks → personalized roadmap | 🔧 Prototype |
| Hiring Dashboard | Recruiter/company candidate pipeline | ✅ Built |

---

## Phase 2 + Trust Scoring Update (Implemented)

- Mixed interview mode is supported behind feature flags:
  - 7 total questions with a 4 code-grounded + 3 conceptual split
  - Typed question metadata (`CODE_GROUNDED`, `CONCEPTUAL`) propagated through APIs
  - Type-level score aggregation (`scoreByQuestionType`) visible to candidates and recruiters
- Weighted technical scoring is supported behind feature flags:
  - default policy: 60% code-grounded + 40% conceptual
  - scoring mode and weights are shown in verify, badge, and recruiter views
- Confidence scoring is computed server-side (`High`/`Medium`/`Low`) and persisted on each badge.
- Behavioral transparency metrics are captured during verification:
  - tab switches
  - paste count
  - average answer time (seconds)
- Trust metadata is visible in both:
  - Public badge page (`/badge/{token}`)
  - Recruiter dashboard candidate views
- OAuth callback handling is hardened to use trusted redirect payload/error handling only.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 14 + TypeScript + Framer Motion + Lenis |
| Backend (Core) | Spring Boot 4.0 + Java 21 + JWT + HMAC-SHA256 |
| Backend (AI) | Python 3.11 + FastAPI + Groq (Llama 3.1) |
| Database | MySQL 8.0 |
| Auth | GitHub OAuth 2.0 + JWT (HMAC-SHA256) |

---

## Project Structure

```
skillproof/
├── backend-core/          ← Spring Boot Java backend
│   └── src/main/java/com/skillproof/backend_core/
│       ├── controller/    ← AuthController, VerificationController, BadgeController
│       ├── service/       ← GitHubService, VerificationService, BadgeService, AiGatewayService
│       ├── model/         ← User, VerificationSession, Question, Answer, Badge
│       ├── repository/    ← JPA repositories
│       ├── config/        ← SecurityConfig, JwtAuthFilter, CorsConfig
│       └── util/          ← JwtUtil, HmacUtil
│
├── backend-ai/            ← Python FastAPI AI microservice
│   └── app/
│       ├── routers/       ← questions.py, evaluation.py
│       ├── services/      ← question_generator.py, answer_evaluator.py
│       └── models/        ← Pydantic models
│
└── frontend/              ← Next.js application
    └── src/
        ├── app/
        │   ├── page.tsx              ← Landing page
        │   ├── verify/page.tsx       ← Verification wizard
        │   ├── badge/[token]/        ← Public badge page
        │   ├── recruiter/page.tsx    ← Recruiter dashboard
        │   └── auth/callback/        ← OAuth callback
        ├── components/
        │   └── OrbitalPipeline.tsx   ← Interactive pipeline visualization
        ├── lib/api.ts                ← Axios API client
        └── hooks/useAuth.ts          ← Auth state hook
```

---

## Setup & Running

### Prerequisites

- Java 21+
- Python 3.11+
- Node.js 18+
- MySQL 8.0
- Maven

### 1. Database Setup

```sql
CREATE DATABASE skillproof;
CREATE USER 'skillproof_user'@'localhost' IDENTIFIED BY 'skillproof123';
GRANT ALL PRIVILEGES ON skillproof.* TO 'skillproof_user'@'localhost';
```

### 2. Backend Core (Spring Boot)

```bash
cd backend-core

# Configure environment (update application.yml or set env vars)
# GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, JWT_SECRET,
# DB_PASSWORD, AI_SERVICE_SECRET

# Example: set DB password in shell before running
# Windows PowerShell:
#   $env:DB_PASSWORD="skillproof123"
# Windows CMD:
#   set DB_PASSWORD=skillproof123
# Mac/Linux:
#   export DB_PASSWORD="skillproof123"

mvn spring-boot:run
# Runs on http://localhost:8080
```

If startup fails with MySQL error 1045 (Access denied for user), run this in MySQL as root/admin:

```sql
ALTER USER 'skillproof_user'@'localhost' IDENTIFIED BY 'skillproof123';
GRANT ALL PRIVILEGES ON skillproof.* TO 'skillproof_user'@'localhost';
FLUSH PRIVILEGES;
```

Then verify login manually:

```bash
mysql -u skillproof_user -p -h localhost skillproof
```

### 3. AI Service (Python)

```bash
cd backend-ai

python -m venv venv
venv\Scripts\activate          # Windows
# source venv/bin/activate     # Mac/Linux

pip install -r requirements.txt

# Create .env file:
# GROQ_API_KEY=your_groq_api_key_here
# AI_SERVICE_SECRET=match_backend_core_ai_service_secret

python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
# Runs on http://localhost:8000
```

### 4. Frontend (Next.js)

```bash
cd frontend
npm install
npm run dev
# Runs on http://localhost:3000
```

---

## Environment Configuration

### `backend-core/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/skillproof
    username: skillproof_user
    password: ${DB_PASSWORD}

github:
  client-id: YOUR_GITHUB_CLIENT_ID
  client-secret: YOUR_GITHUB_CLIENT_SECRET
  redirect-uri: http://localhost:8080/api/auth/github/callback

jwt:
  secret: YOUR_JWT_SECRET_MIN_32_CHARS

ai-service:
  url: http://localhost:8000
  secret: YOUR_SHARED_AI_SERVICE_SECRET
```

### `backend-ai/.env`

```
GROQ_API_KEY=your_groq_api_key_here
AI_SERVICE_SECRET=YOUR_SHARED_AI_SERVICE_SECRET
```

### GitHub OAuth App Settings

Go to `github.com/settings/developers` → New OAuth App:
- Homepage URL: `http://localhost:3000`
- Callback URL: `http://localhost:8080/api/auth/github/callback`

---

## API Endpoints

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/auth/github` | None | Get GitHub OAuth URL |
| GET | `/api/auth/github/callback` | None | OAuth callback → JWT |
| GET | `/api/auth/repos` | JWT | List user's GitHub repos |
| POST | `/api/verify/start` | JWT | Start verification session |
| POST | `/api/verify/submit` | JWT | Submit answers + integrity metadata → generate badge |
| GET | `/api/badge/{token}` | None | Public badge data |
| GET | `/api/recruiter/candidates` | JWT | All verified candidates |

---

## How Verification Works

```
1. Developer connects GitHub (OAuth)
2. Selects a repository
3. Spring Boot fetches source files, filters irrelevant files
4. Code structure extracted (functions, classes, annotations)
5. Groq AI generates 5-7 questions grounded in the actual code (mixed mode can include conceptual prompts)
6. Developer answers all questions in the verification wizard
7. Groq AI evaluates each answer against the code context
8. Scores computed: Backend, API Design, Error Handling, Code Quality, Documentation + type-level scoring (code vs concept)
9. Confidence tier computed from skip count, answer length, and score consistency
10. Integrity signals recorded (tab switches, paste count, average answer time)
11. Badge created and HMAC-SHA256 signed
12. Public badge URL generated — shareable with recruiters
```

---

## Security

- **JWT**: HS384 signed tokens, 24-hour expiry
- **HMAC Badges**: SHA-256 signed with server secret — tamper-evident
- **GitHub OAuth**: Read-only scope (`public_repo, read:user`) — no write access ever
- **Internal Service Auth**: Core → AI internal routes protected with `X-Internal-Secret`
- **CORS**: Configured per environment — localhost only in dev

---

## Badge Trust Metadata

- **Confidence Tier** (`High`/`Medium`/`Low`):
  - High: no skips, avg answer length > 100 chars, score spread < 20
  - Medium: 1 skip or score spread >= 20
  - Low: 2 skips or very short answers
- **Integrity Signals** (transparency only):
  - Tab switches
  - Paste count
  - Average answer time (seconds)
- Signals are informational for recruiters and do not auto-fail a verification.

---

## Deployment

| Service | Platform | Notes |
|---|---|---|
| Frontend | Vercel | `npm run build` → deploy |
| Spring Boot | Railway | Add env vars, deploy jar |
| Python AI | Render | Deploy with Dockerfile |
| Database | PlanetScale / Railway MySQL | Update DB URL in env |

---



---

## License

MIT — for educational purposes.
