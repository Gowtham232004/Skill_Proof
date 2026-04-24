# SkillProof Testing Guide

Date: 2026-04-24

This guide defines the minimum reliable validation flow for demo and release checks.

## 1. Service Health Prerequisites

Verify all required services before testing:
1. backend-core running on 8080,
2. backend-ai running on 8000,
3. frontend running on 3000,
4. MySQL reachable,
5. Docker Desktop running for challenge execution.

## 2. Minimal Health Commands (PowerShell)

Check backend-ai health:

```powershell
Invoke-WebRequest http://localhost:8000/health -UseBasicParsing | Select-Object -ExpandProperty Content
```

Check backend-core auth endpoint:

```powershell
Invoke-WebRequest http://localhost:8080/api/auth/github -UseBasicParsing | Select-Object -ExpandProperty Content
```

Check frontend route:

```powershell
Invoke-WebRequest http://localhost:3000 -UseBasicParsing | Select-Object -ExpandProperty StatusCode
```

## 3. End-to-End Smoke Test Checklist

Run this exact sequence before demo:
1. Login through GitHub.
2. Start verification on one known repo.
3. Submit answers and confirm badge generation.
4. Open recruiter dashboard and candidate detail.
5. Trigger Start Live Session and confirm redirect to recruiter live page.
6. Reveal one question and submit one candidate answer.
7. Send quick challenge and submit from candidate link.
8. Send PR review and submit from candidate link.
9. Create coding challenge and submit a passing solution.
10. If using video interview, open both recruiter and candidate links once each.

## 4. Expected Result Matrix

1. Verification start returns session with questions.
2. Verification submit returns badge token and scores.
3. Recruiter detail shows evidence cards and integrity/confidence signals.
4. Live session status transitions from PENDING to ACTIVE to COMPLETED.
5. Quick challenge status transitions from PENDING after generation to completed after submit.
6. PR review status transitions from PENDING after generation to completed after submit.
7. Coding challenge returns PASSED or FAILED with diagnostics.

## 5. Known Failure Patterns and Fast Fix

### Live session start seems to do nothing
1. Check POST /api/live/sessions response code.
2. Confirm valid JWT token in browser storage.
3. Confirm response includes sessionCode.

### Quick challenge or PR review stays pending
1. Confirm candidate link token is correct.
2. Confirm candidate submit API call succeeded.
3. Refresh recruiter result endpoint.

### Coding challenge returns ERROR
1. Confirm Docker daemon is running.
2. Confirm docker images are available.
3. Check backend-core logs for execution runner error.

## 6. Regression Test Scope for Every Major Change

Mandatory regression scope:
1. auth and repo fetch,
2. verify start and submit,
3. recruiter dashboard and detail,
4. live session start/reveal/submit,
5. quick challenge generate/open/submit/result,
6. pr review generate/open/submit/result,
7. challenge create/submit/submission review.

Recommended automation:
1. backend-core unit tests via Maven,
2. backend-ai API contract tests for generation and evaluation routes,
3. frontend smoke tests for critical routes.

## 7. Pre-Demo Final Gate

A demo is considered ready only if:
1. all core feature flows above were run once successfully,
2. no blocking errors in service logs,
3. at least one fallback candidate and badge token is prepared,
4. live session and challenge links open correctly in a fresh browser session.
