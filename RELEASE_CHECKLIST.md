# SkillProof Release Checklist

Use this checklist before sharing this folder or creating a release tag.

## 1. Code and docs sanity

- [ ] Pull latest changes and confirm correct branch.
- [ ] Confirm handoff docs are present and updated:
  - `HANDOFF_README.md`
  - `docs/HANDOFF_DOCUMENTATION.md`
  - `START_AND_INTERACTION_GUIDE.md`
- [ ] Confirm no temporary/debug files are included.

## 2. Environment and startup verification

- [ ] `docker compose up -d` starts MySQL successfully.
- [ ] Backend core starts without startup errors.
- [ ] Backend AI starts and `/health` returns healthy.
- [ ] Frontend starts and landing page loads at `http://localhost:3000`.

## 3. Critical flow checks

- [ ] Verification start works with a real repo.
- [ ] Question answering and submit flow works end-to-end.
- [ ] Badge page renders with score and trust signals.
- [ ] Recruiter dashboard loads candidate list.
- [ ] Compare page loads and shows technical/integrity/execution metrics.
- [ ] Live session can be created and completed.
- [ ] Video room creation returns a valid room link.

## 4. CI and quality gates

- [ ] CI workflow file exists: `.github/workflows/skillproof-ci.yml`.
- [ ] Backend core tests pass locally (or latest run is green).
- [ ] Frontend lint passes.
- [ ] Frontend build passes.
- [ ] Backend AI compile check passes.

## 5. Security and config checks

- [ ] No real secrets hardcoded in tracked files.
- [ ] Local defaults are safe for sharing.
- [ ] `.env` files are not committed with production secrets.
- [ ] Demo tokens in docs are non-production only.

## 6. Handoff completeness

- [ ] Pending backlog is clearly listed in `docs/HANDOFF_DOCUMENTATION.md`.
- [ ] Next implementation order is clearly listed.
- [ ] Friend can follow startup steps without additional explanation.
- [ ] Friend can identify where to continue Phase work immediately.

## 7. Final share package

- [ ] Optional: remove large generated artifacts not needed for handoff.
- [ ] Optional: include DB schema/setup SQL used for local setup.
- [ ] Zip folder and share with this order for reading:
  1. `HANDOFF_README.md`
  2. `START_AND_INTERACTION_GUIDE.md`
  3. `docs/HANDOFF_DOCUMENTATION.md`
  4. `README.md`
