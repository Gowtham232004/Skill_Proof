# Team Issues - Next Phase Backlog

Date: 2026-04-24

## Delivery Strategy

1. Keep current skill folder stable for demos and evaluations.
2. Build new direction in separate skill2 copy.
3. Use feature flags so existing workflows never break.

## Current Status Snapshot

Implemented now:
1. Verification pipeline and badge issuance.
2. Integrity and confidence signals.
3. Recruiter dashboard and evidence panel.
4. Live session flows.
5. Quick challenge and PR review challenge.
6. Video room link generation.
7. Manual and repo-grounded coding challenges with Docker evaluation.

Known pain points:
1. Randomized question trust concerns from panel feedback.
2. Live session troubleshooting still needed in some runs.
3. Need clearer unique value versus separate tools.

## Priority Backlog for Skill2

## Issue 1: Scenario-Centered Evaluation Engine (highest)
Problem:
Current flows can feel feature-fragmented and random-question heavy.

Scope:
1. Introduce Scenario as first-class object.
2. Support safe context modes:
   - PUBLIC_REPO
   - SNIPPET
   - SYNTHETIC
   - JD_ONLY
3. Make quick challenge, PR review, live tasks, and report consume same scenario.

Acceptance criteria:
1. One scenario id links all downstream tasks and scores.
2. No-repo case works without crash.
3. Existing legacy flow still works with feature flag off.

## Issue 2: Deterministic Evaluation Overlay (highest)
Problem:
Pure open-text AI scoring is difficult to defend under harsh viva questioning.

Scope:
1. Persist expected answer keys per generated task.
2. Add deterministic checks for concept coverage and identifier references.
3. Keep LLM rubric as bounded component, not sole judge.

Acceptance criteria:
1. Score response includes explainable factor breakdown.
2. Generic answers are consistently capped.
3. Policy version appears in results for traceability.

## Issue 3: Live Proof Workspace (high)
Problem:
Video and coding currently feel like separate experiences.

Scope:
1. Integrate video + shared editor + run output in one recruiter-candidate session view.
2. Persist event timeline for audit.
3. Add deterministic run checks where possible.

Acceptance criteria:
1. Recruiter can watch edits in near real-time.
2. Run outcomes are visible to both sides.
3. Session report includes code-run evidence trace.

## Issue 4: Pre-Interview Intelligence (high)
Problem:
Recruiters need a pre-filter signal before deeper assessment.

Scope:
1. Resume + GitHub + JD fit analysis report.
2. Explainable authenticity and fit scores.
3. Recruiter actions: Proceed, Manual Review, Reject.

Acceptance criteria:
1. No hard candidate auto-blocking.
2. Missing GitHub handled as limited evidence, not auto-fail.
3. Recruiter sees score reasons, not black-box output.

## Issue 5: Live Session Reliability Hardening (medium)
Problem:
Some demo runs report live session start or answer loading inconsistencies.

Scope:
1. Add stronger endpoint logging and correlation ids.
2. Improve API error propagation to UI.
3. Add pre-demo health checks in one script.

Acceptance criteria:
1. Failures are diagnosable within 5 minutes.
2. Frontend shows actionable error messages.
3. Repro checklist exists and is documented.

## Issue 6: Trust Signal Calibration (medium)
Problem:
Assist-likelihood heuristics can create false positives without calibration.

Scope:
1. Add benchmark answer sets.
2. Track false positive and false negative outcomes.
3. Adjust thresholds with versioned policy notes.

Acceptance criteria:
1. Calibration report documented per iteration.
2. Recruiter can see indicator explanation text.
3. Policy changes are auditable over time.
