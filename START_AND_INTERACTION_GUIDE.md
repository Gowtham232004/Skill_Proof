# SkillProof Start and Interaction Guide

This guide explains how to start the project and how to use each major flow as recruiter and candidate.

## 1. Services in this project

- Frontend: Next.js app on port `3000`.
- Backend core: Spring Boot app on port `8080`.
- Backend AI: FastAPI app on port `8000`.
- Database: MySQL (via Docker, host port `3307`).

## 2. Quick start (local)

## Step A: Start MySQL

From project root:

```bash
docker compose up -d
```

## Step B: Start backend core

Windows:

```powershell
cd backend-core
.\mvnw.cmd spring-boot:run
```

Mac/Linux:

```bash
cd backend-core
./mvnw spring-boot:run
```

## Step C: Start backend AI

```bash
cd backend-ai
python -m venv venv
```

Activate venv and install deps:

```bash
pip install -r requirements.txt
```

Run AI service:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Step D: Start frontend

```bash
cd frontend
npm install
npm run dev
```

Open:

- `http://localhost:3000`

## 3. Health check (quick)

- Frontend opens at `http://localhost:3000`.
- Backend auth endpoint responds at `http://localhost:8080/api/auth/github`.
- AI health responds at `http://localhost:8000/health`.

## 4. How to interact with the project

## A. Candidate verification flow

1. Login with GitHub.
2. Choose repo owner and repo name.
3. Start verification.
4. Answer generated code-grounded and conceptual questions.
5. Submit answers.
6. Open generated badge page and verify scores/signals.

## B. Recruiter dashboard flow

1. Open recruiter dashboard.
2. Review candidate list and filters.
3. Open candidate detail page.
4. Review evidence, trust signals, and decision status.
5. Use compare page to contrast candidates.

## C. Live interview flow

1. Recruiter starts live session from candidate detail/dashboard.
2. Recruiter gets room/session links.
3. Candidate joins shared link.
4. Recruiter reveals/questions or conducts live evaluation.
5. Recruiter checks final live result and evidence.

## D. Video room flow

1. Recruiter clicks start video interview.
2. System creates room URL (Daily or Jitsi fallback).
3. Share candidate link.
4. Both participants join the same room URL.

## E. Challenge flow (if enabled in current build)

1. Recruiter creates challenge.
2. Candidate opens challenge URL.
3. Candidate submits code.
4. Recruiter reviews execution/test result.

## 5. Common issues and fixes

- Backend not reachable: confirm backend core is running on `8080`.
- AI errors in question/evaluation: confirm AI service on `8000` and env vars are set.
- DB issues: confirm Docker MySQL container is healthy and mapped to `3307`.
- Frontend API failures: verify frontend is pointed to correct backend URL.

### Low-memory Windows crash (paging file too small)

If backend crashes with native memory allocation errors, this project already includes
`backend-core/.mvn/jvm.config` with conservative heap settings.

If your machine is still tight on memory, run backend with lower max heap:

```powershell
cd backend-core
$env:MAVEN_OPTS='-Xms128m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC'
.\mvnw.cmd spring-boot:run
```

## 6. Recommended reading order for new contributor

1. `HANDOFF_README.md`
2. `START_AND_INTERACTION_GUIDE.md`
3. `docs/HANDOFF_DOCUMENTATION.md`
4. `TEAM_ISSUES_NEXT_PHASE.md`
5. `README.md`
