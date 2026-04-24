# SkillProof

Developer credibility platform for technical hiring.

SkillProof helps recruiters validate whether candidates truly understand the code they claim to have built. It combines repo-grounded assessment, recruiter review, live validation, and coding challenge execution evidence.

## Live URLs (Local)

- Frontend: http://localhost:3000
- Backend core: http://localhost:8080
- Backend AI: http://localhost:8000

## Problem Statement

Hiring teams face a trust gap:

1. Portfolio and resume inflation is increasing.
2. Generic AI-generated answers can look convincing.
3. Technical interview bandwidth is expensive.
4. Recruiters need evidence, not only claims.

SkillProof addresses this by turning repository claims into auditable technical evidence.

## What Is Implemented (Current)

### Candidate verification pipeline

1. GitHub OAuth login.
2. Repository selection and code ingestion.
3. Repo-aware question generation.
4. Candidate answer submission.
5. AI evaluation with rubric and guardrails.
6. Badge generation with tokenized shareable result.

### Trust and scoring

1. Mixed question support (code-grounded + conceptual).
2. Weighted scoring policy support.
3. Confidence tier (High, Medium, Low).
4. Integrity telemetry in verification flow:
   - tab switches
   - paste count
   - answer timing
   - copy signal support

### Recruiter workflows

1. Recruiter dashboard and candidate listing.
2. Candidate detail with evidence and score visibility.
3. Recruiter decision actions:
   - VERIFIED
   - NEEDS_LIVE_INTERVIEW
   - REJECT
4. Compare candidates flow.

### Live validation

1. Live session creation from recruiter candidate detail.
2. Recruiter reveal-next question flow.
3. Candidate live answer submission.
4. Recruiter live answers review.

### Challenge and validation extensions

1. Quick challenge generation and candidate link flow.
2. PR review challenge generation and evaluation flow.
3. Video room generation flow (Jitsi and provider abstraction).
4. Coding challenge module with hidden tests and Docker execution.
5. Repo-grounded challenge mode.

## Architecture

SkillProof has a three-service architecture.

1. Frontend (Next.js + TypeScript): candidate and recruiter UI flows.
2. Backend core (Spring Boot): auth, orchestration, persistence, role security, recruiter operations.
3. Backend AI (FastAPI): generation and evaluation services.

### Backend core owns

1. JWT auth and role enforcement.
2. Verification sessions, answers, badges.
3. Live session and recruiter APIs.
4. Challenge lifecycle and execution orchestration.

### Backend AI owns

1. Question generation.
2. Answer evaluation.
3. Quick challenge and PR review model-assisted logic.
4. Output sanitation and parsing resilience.

## Tech Stack

- Frontend: Next.js 14, TypeScript
- Backend core: Spring Boot, Java 21
- Backend AI: FastAPI, Python 3.11
- Database: MySQL 8.0
- Execution sandbox: Docker
- Auth: GitHub OAuth + JWT

## Project Structure

- backend-core: Spring Boot service
- backend-ai: FastAPI service
- frontend: Next.js app
- docs: handoff and presentation docs
- scripts: setup and environment helper scripts

## Setup

### Prerequisites

1. Java 21+
2. Python 3.11+
3. Node.js 18+
4. MySQL 8+
5. Maven
6. Docker Desktop (for coding challenge execution)

### 1) Database

```sql
CREATE DATABASE skillproof;
CREATE USER 'skillproof_user'@'localhost' IDENTIFIED BY 'skillproof123';
GRANT ALL PRIVILEGES ON skillproof.* TO 'skillproof_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2) Start backend core

```bash
cd backend-core
./mvnw spring-boot:run
```

Windows:

```powershell
cd backend-core
.\mvnw.cmd spring-boot:run
```

### 3) Start backend AI

```bash
cd backend-ai
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Windows PowerShell:

```powershell
cd backend-ai
python -m venv venv
venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 4) Start frontend

```bash
cd frontend
npm install
npm run dev
```

### 5) Optional Docker quick setup

- Windows: run scripts/setup-docker.ps1
- Mac/Linux: run scripts/setup-docker.sh

## Environment Notes

### Backend core settings

Set these values through environment variables or application configuration:

1. DB_PASSWORD
2. GITHUB_CLIENT_ID
3. GITHUB_CLIENT_SECRET
4. JWT_SECRET
5. AI service URL and secret

### Backend AI settings

Set in backend-ai/.env:

1. GROQ_API_KEY
2. AI service internal secret

## API Surface (High-Level)

### Auth and verification

1. GET /api/auth/github
2. GET /api/auth/github/callback
3. GET /api/auth/repos
4. POST /api/verify/start
5. POST /api/verify/submit

### Recruiter and badge

1. GET /api/badge/{token}
2. GET /api/recruiter/candidates
3. GET /api/recruiter/candidates/{badgeToken}
4. PUT /api/recruiter/candidates/{badgeToken}/decision

### Live session

1. POST /api/live/sessions
2. POST /api/live/{sessionCode}/reveal-next
3. GET /api/live/{sessionCode}/status
4. POST /api/live/{sessionCode}/questions/{questionNumber}/answer
5. GET /api/live/{sessionCode}/answers

### Quick challenge and PR review

1. POST /api/quick-challenge/generate
2. GET /api/quick-challenge/{token}
3. POST /api/quick-challenge/{token}/submit
4. POST /api/pr-review/generate
5. GET /api/pr-review/{token}
6. POST /api/pr-review/{token}/submit

### Coding challenge

1. POST /api/challenges
2. POST /api/challenges/repo-grounded/generate
3. GET /api/challenges/{challengeId}
4. POST /api/challenges/{challengeId}/submit
5. GET /api/challenges/{challengeId}/submissions

## Testing and Validation

Use the operational testing guide:

- [TESTING_GUIDE.md](TESTING_GUIDE.md)

This includes:

1. service health checks,
2. smoke test sequence,
3. regression scope,
4. pre-demo release gate checklist.

## Handoff and Operational Docs

- [QUICK_START_FOR_FRIEND.md](QUICK_START_FOR_FRIEND.md)
- [HANDOFF_README.md](HANDOFF_README.md)
- [START_AND_INTERACTION_GUIDE.md](START_AND_INTERACTION_GUIDE.md)
- [docs/HANDOFF_DOCUMENTATION.md](docs/HANDOFF_DOCUMENTATION.md)
- [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md)

## Next Implementation Direction (Skill2 Plan)

Current demo flow remains stable in this repository. The next implementation direction is planned as a controlled extension.

### Planned next milestone themes

1. Scenario-centered evaluation engine.
2. Deterministic evaluation overlay with expected answer keys.
3. Live proof workspace (video + shared coding + run trace).
4. Pre-interview intelligence (resume and GitHub fit analysis).
5. Reliability hardening and better calibration governance.

### Detailed planning docs

- [TEAM_ISSUES_NEXT_PHASE.md](TEAM_ISSUES_NEXT_PHASE.md)
- [docs/skill2-presentation-kit/01_PROJECT_REALITY_AND_VIVA_DEFENSE.md](docs/skill2-presentation-kit/01_PROJECT_REALITY_AND_VIVA_DEFENSE.md)
- [docs/skill2-presentation-kit/02_IDEA_VALIDATION_CHATGPT_DEEPSEEK_AND_FINAL_DECISION.md](docs/skill2-presentation-kit/02_IDEA_VALIDATION_CHATGPT_DEEPSEEK_AND_FINAL_DECISION.md)
- [docs/skill2-presentation-kit/03_SKILL2_14_DAY_IMPLEMENTATION_BLUEPRINT.md](docs/skill2-presentation-kit/03_SKILL2_14_DAY_IMPLEMENTATION_BLUEPRINT.md)
- [docs/skill2-presentation-kit/04_LIVE_SESSION_AND_DEMO_TROUBLESHOOTING_PLAYBOOK.md](docs/skill2-presentation-kit/04_LIVE_SESSION_AND_DEMO_TROUBLESHOOTING_PLAYBOOK.md)
- [docs/skill2-presentation-kit/05_COPY_PASTE_PROMPTS_FOR_COPILOT_SKILL2.md](docs/skill2-presentation-kit/05_COPY_PASTE_PROMPTS_FOR_COPILOT_SKILL2.md)
- [docs/skill2-presentation-kit/06_UNIQUE_VALUE_BUSINESS_AND_REAL_WORLD_ADOPTION.md](docs/skill2-presentation-kit/06_UNIQUE_VALUE_BUSINESS_AND_REAL_WORLD_ADOPTION.md)
- [docs/skill2-presentation-kit/07_SKILL2_MASTER_IMPLEMENTATION_DOCUMENT.md](docs/skill2-presentation-kit/07_SKILL2_MASTER_IMPLEMENTATION_DOCUMENT.md)
- [docs/skill2-presentation-kit/08_INVIGILATOR_QA_AND_DEFENSE_PLAYBOOK.md](docs/skill2-presentation-kit/08_INVIGILATOR_QA_AND_DEFENSE_PLAYBOOK.md)
- [docs/skill2-presentation-kit/09_SKILL2_TWO_WEEK_EXECUTION_PLAN.md](docs/skill2-presentation-kit/09_SKILL2_TWO_WEEK_EXECUTION_PLAN.md)
- [docs/skill2-presentation-kit/10_SKILLPROOF_COMPLETE_WORKFLOW_AND_PHASE_GUIDE.md](docs/skill2-presentation-kit/10_SKILLPROOF_COMPLETE_WORKFLOW_AND_PHASE_GUIDE.md)

## License

MIT (educational and project demonstration use).
