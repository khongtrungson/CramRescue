# Technical Debt / Product Decisions

## Pass Probability

1. Empty RED/YELLOW tier
PRD defines 70% RED + 30% YELLOW but does not specify an empty tier.
Current implementation redistributes weight to the existing tier.

2. Partial mastery
"Tạm ổn" does not contribute to completion.
Only "Đã thuộc" counts as completed.
This follows the current interpretation of FR-9 completion rate.

## Micro-Missions

3. Resumed mission summary
The completion summary currently covers only cards studied in the
current resumed sitting, not all cards completed across the entire
mission lifecycle.

Future improvement:
Associate study attempts with mission/session identity if whole-mission
summary is required.

4. Study-attempt provenance
Study attempts do not currently record whether they came from:
- full review
- micro-mission
- single-card review

Therefore studying a mission card through "Ôn tất cả" can also advance
an unfinished mission.

Current MVP semantics:
Any qualifying study attempt after mission creation may count toward
mission progress.

Future improvement:
Add explicit study source / missionId if mission-specific progress is
required.