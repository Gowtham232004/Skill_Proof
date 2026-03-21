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
| Live Coding Challenges | Docker-sandboxed task execution with automated test scoring | 🔧 Prototype |
| Peer Code Review | Expert reviewer matching by tech stack | 🔧 Prototype |
| Skill Gap Analysis | 24 code pattern checks → personalized roadmap | 🔧 Prototype |
| Hiring Dashboard | Recruiter/company candidate pipeline | ✅ Built |

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
# GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, JWT_SECRET, DB credentials

mvn spring-boot:run
# Runs on http://localhost:8080
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

### Step 1: GitHub OAuth App Setup

1. Go to `https://github.com/settings/developers`
2. Click **New OAuth App**
3. Fill in:
   - **Application name:** SkillProof
   - **Homepage URL:** `http://localhost:3000`
   - **Authorization callback URL:** `http://localhost:8080/api/auth/github/callback`
4. Click **Create**
5. Copy your **Client ID** and **Client Secret**

### Step 2: Backend Core Configuration

1. Create `backend-core/src/main/resources/application-local.yml` (never commit this file):

```yaml
github:
  client-id: YOUR_GITHUB_CLIENT_ID
  client-secret: YOUR_GITHUB_CLIENT_SECRET

jwt:
  secret: YOUR_JWT_SECRET_MIN_64_CHARS

spring:
  datasource:
    password: skillproof123
```

**Note:** This file overrides values in `application.yml` at runtime. It's listed in `.gitignore` and never committed for security.

### Step 3: AI Service Configuration

Create `backend-ai/.env`:

```
GROQ_API_KEY=your_groq_api_key_from_console.groq.com
```

Get your Groq API key from: `https://console.groq.com/keys`

### Step 4: Frontend Configuration (Optional)

If needed, create `frontend/.env.local`:

```
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_GITHUB_CLIENT_ID=YOUR_GITHUB_CLIENT_ID
```

---

## API Endpoints

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/auth/github` | None | Get GitHub OAuth URL |
| GET | `/api/auth/github/callback` | None | OAuth callback → JWT |
| GET | `/api/auth/repos` | JWT | List user's GitHub repos |
| POST | `/api/verify/start` | JWT | Start verification session |
| POST | `/api/verify/submit` | JWT | Submit answers → generate badge |
| GET | `/api/badge/{token}` | None | Public badge data |
| GET | `/api/recruiter/candidates` | JWT | All verified candidates |

---

## How Verification Works

```
1. Developer connects GitHub (OAuth)
2. Selects a repository
3. Spring Boot fetches source files, filters irrelevant files
4. Code structure extracted (functions, classes, annotations)
5. Groq AI generates 5 questions grounded in the actual code
6. Developer answers 5 questions (wizard UI)
7. Groq AI evaluates each answer against the code context
8. Scores computed: Backend, API Design, Error Handling, Code Quality, Documentation
9. Badge created and HMAC-SHA256 signed
10. Public badge URL generated — shareable with recruiters
```

---

## Security

- **JWT**: HS384 signed tokens, 24-hour expiry
- **HMAC Badges**: SHA-256 signed with server secret — tamper-evident
- **GitHub OAuth**: Read-only scope (`public_repo, read:user`) — no write access ever
- **CORS**: Configured per environment — localhost only in dev

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
