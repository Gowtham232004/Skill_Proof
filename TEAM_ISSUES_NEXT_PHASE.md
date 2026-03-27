# Team Issues - Next Phase Backlog

## Done in this patch
- Recruiter detail now has a manual review Evidence Panel with collapsed accordion cards for each question.
- Each evidence card includes question text, file reference, first 15 lines of code context, developer answer text, AI feedback, and score pills (accuracy/depth/specificity).
- Verification flow copy telemetry is stronger by counting both clipboard copy/cut events and keyboard copy shortcut usage outside input fields.

## Issue 1: Add execution-based verification signal (high priority)
Problem:
- Current trust signals are mostly behavioral text signals (tab, paste, copy, timing).
- Recruiters still lack proof that a candidate can execute and debug code under constraints.

Scope:
- Add optional mini execution tasks for selected questions.
- Candidate submits code output or test result from a controlled prompt.
- Store execution outcome and pass/fail metadata in badge evidence.

Acceptance criteria:
- Recruiter can see execution status per question (pass/fail/timeout/error).
- At least one execution-backed signal is included in integrity scoring.
- Badge/recruiter UI clearly labels execution-backed evidence separately from text scoring.

## Issue 2: Hardening against same-tab assistant usage (medium priority)
Problem:
- Extension/second-screen/phone help is still hard to detect reliably in browser-only mode.

Scope:
- Add lightweight risk heuristics:
  - large instant answer jumps (possible assistant insertion)
  - long idle + sudden high-quality completion
  - repeated low-depth/high-accuracy response pattern
- Route these to an "assist-likelihood" indicator, not a hard fail.

Acceptance criteria:
- Recruiter page shows assist-likelihood level with explanation.
- Integrity penalty logic treats this as advisory, not deterministic cheating proof.
- False positives are minimized and documented.

## Issue 3: Recruiter manual rubric actions (medium priority)
Problem:
- Recruiter sees AI scoring but cannot add final human decision context in-system.

Scope:
- Add recruiter actions per candidate:
  - Mark answer set as Verified / Needs Live Interview / Reject
  - Add notes and decision reason
  - Export review summary

Acceptance criteria:
- Recruiter decision status is stored and visible in dashboard list.
- Decision notes are audit-tracked with reviewer and timestamp.
- Export includes question evidence, AI feedback, and recruiter decision.
