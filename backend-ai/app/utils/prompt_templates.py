QUESTION_GENERATION_PROMPT = """
You are a senior software engineer conducting a technical interview.
You have been given a code summary of a developer's GitHub project.

Your task: Generate exactly 5 technical interview questions about this specific codebase.

STRICT RULES — violating these makes the output useless:
1. Every question MUST reference a specific file name, class name, function name, or variable
   that actually appears in the code summary below. No generic questions.
2. Questions must test UNDERSTANDING of implementation decisions, not definitions.
   BAD: "What is JWT?" 
   GOOD: "In your AuthService.java, you call jwtUtil.generateToken() after saving the user.
          Why do you generate the token at that specific point in the flow?"
3. Difficulty distribution: Question 1=EASY, Question 2=EASY, Question 3=MEDIUM,
   Question 4=HARD, Question 5=HARD
4. EASY questions: Ask about what a specific component does and why it was structured that way.
5. MEDIUM questions: Ask about how two components interact, or about a specific implementation choice.
6. HARD questions: Ask about edge cases, failure scenarios, scalability, or security implications
   of specific code decisions.
7. Return ONLY valid JSON. No explanation text before or after. No markdown code blocks.

CODE SUMMARY:
{code_summary}

PROJECT: {repo_name}
PRIMARY LANGUAGE: {primary_language}
FRAMEWORKS: {frameworks}

Return this exact JSON structure:
{{
  "questions": [
    {{
      "question_number": 1,
      "difficulty": "EASY",
      "file_reference": "ExactFileName.java",
      "question_text": "Your specific question referencing actual code here"
    }},
    {{
      "question_number": 2,
      "difficulty": "EASY", 
      "file_reference": "AnotherFile.ts",
      "question_text": "Another specific question"
    }},
    {{
      "question_number": 3,
      "difficulty": "MEDIUM",
      "file_reference": "FileName.py",
      "question_text": "Medium difficulty question"
    }},
    {{
      "question_number": 4,
      "difficulty": "HARD",
      "file_reference": "FileName.java",
      "question_text": "Hard question about edge cases or design decisions"
    }},
    {{
      "question_number": 5,
      "difficulty": "HARD",
      "file_reference": "AnotherFile.java",
      "question_text": "Hard question about security or scalability"
    }}
  ]
}}
"""


ANSWER_EVALUATION_PROMPT = """
You are a senior software engineer evaluating a developer's answer to a code-specific interview question.

QUESTION ASKED: {question_text}
FILE REFERENCED: {file_reference}

ACTUAL CODE (the question was about this specific code):
{code_context}

DEVELOPER'S ANSWER:
{answer_text}

═══════════════════════════════════════════════════
MANDATORY PRE-SCORING CHECKS — Run these BEFORE scoring:
═══════════════════════════════════════════════════

PRE-CHECK 1 — Word count:
Count words in the developer's answer.
If word count < 15: Set ALL scores to 1. Skip remaining checks. Return immediately.

PRE-CHECK 2 — Identifier count:
Count how many specific identifiers from the CODE CONTEXT appear in the developer's answer.
Identifiers = function names, class names, variable names, method names,
annotation names, file names, constant names, parameter names.
Generic words like "function", "class", "variable", "method" do NOT count.
Only count names that actually appear in the code above.
Store this as IDENTIFIER_COUNT.

PRE-CHECK 3 — Reasoning detection:
Does the answer explain WHY a decision was made, or does it only describe WHAT exists?
"The function returns a string" = WHAT (no reasoning)
"The function returns a string because the API contract requires serialized output" = WHY (has reasoning)

═══════════════════════════════════════════════════
SCORING RULES — Apply in this exact order:
═══════════════════════════════════════════════════

RULE 1 — Specificity score (apply IDENTIFIER_COUNT caps):
- IDENTIFIER_COUNT = 0: specificity_score = 1 or 2 (maximum 2, no exceptions)
- IDENTIFIER_COUNT = 1: specificity_score maximum = 4
- IDENTIFIER_COUNT = 2: specificity_score maximum = 6
- IDENTIFIER_COUNT >= 3: score normally (1-10 based on quality)

RULE 2 — Depth score:
- If answer has no reasoning (only WHAT, no WHY): depth_score maximum = 3
- If answer has partial reasoning: depth_score maximum = 6
- If answer explains trade-offs, edge cases, or design rationale: score 7-10

RULE 3 — Accuracy score:
- Score based on factual correctness against the CODE CONTEXT provided
- If answer contradicts the actual code: accuracy_score = 1-3
- If answer is correct but vague: accuracy_score = 4-6
- If answer is factually precise and matches code behavior: accuracy_score = 7-10

RULE 4 — Overall composite cap:
After computing all three scores, apply this final cap:
- If specificity_score <= 2: composite score cannot exceed 25 (regardless of other scores)
- If specificity_score = 3-4: composite score cannot exceed 45
- No cap applies when specificity_score >= 5

═══════════════════════════════════════════════════
EXPECTED SCORE RANGES — Use as calibration reference:
═══════════════════════════════════════════════════

Generic AI-style answer (no identifiers, no specifics):
"This function manages the data flow and returns results to the user."
→ Expected: accuracy=3, depth=2, specificity=1, composite=12-18

Partially specific answer (1 identifier, some reasoning):
"The searchKaggleDatasets function handles authentication for the API."
→ Expected: accuracy=5, depth=3, specificity=3, composite=25-35

Strong specific answer (3+ identifiers, explains WHY):
"In searchKaggleDatasets(), Buffer.from() encodes the username:key pair because
the Kaggle API requires Basic authentication headers in base64 format.
Without this encoding, the Authorization header would be rejected."
→ Expected: accuracy=8, depth=7, specificity=8, composite=70-80

═══════════════════════════════════════════════════
Return ONLY valid JSON. No text before or after. No markdown fences:
═══════════════════════════════════════════════════

{{
  "accuracy_score": <0-10>,
  "depth_score": <0-10>,
  "specificity_score": <0-10>,
  "ai_feedback": "2-3 sentences explaining the score. Reference actual identifiers from the code and the answer. Explain specifically what was missing or strong."
}}
"""


HYBRID_QUESTION_GENERATION_PROMPT = """
You are a senior software engineer conducting a technical interview.
You have been given a code summary of a developer's GitHub project.

Generate exactly {total_questions} interview questions with this mix:
- {code_grounded_questions} CODE_GROUNDED questions
- {conceptual_questions} CONCEPTUAL questions

Definitions:
- CODE_GROUNDED: must reference a specific file/class/function/variable from the summary.
- CONCEPTUAL: must test engineering reasoning (trade-offs, failure modes, design principles)
  but still stay relevant to this repo's stack and architecture.

STRICT RULES:
1. Return ONLY valid JSON array. No markdown, no prose.
2. Keep questions non-generic and interview-ready.
3. Each question must include:
   - questionNumber (1..{total_questions})
   - difficulty (EASY|MEDIUM|HARD)
   - fileReference (empty string allowed only for conceptual questions)
   - questionType (CODE_GROUNDED|CONCEPTUAL)
   - questionText
4. Difficulty split:
   - For 5 questions: Q1-2 EASY, Q3-4 MEDIUM, Q5 HARD
   - For 7 questions: Q1-2 EASY, Q3-5 MEDIUM, Q6-7 HARD
5. Avoid duplicate question intent.

CODE SUMMARY:
{code_summary}

PROJECT: {repo_name}
PRIMARY LANGUAGE: {primary_language}
FRAMEWORKS: {frameworks}

Return this JSON array format:
[
  {{
    "questionNumber": 1,
    "difficulty": "EASY",
    "fileReference": "AuthService.java",
    "questionType": "CODE_GROUNDED",
    "questionText": "Specific question"
  }}
]
"""


FOLLOWUP_QUESTION_PROMPT = """
You are a senior software engineer conducting a technical interview follow-up.

The developer answered a code-specific question, but their answer was incomplete.
Generate exactly 1 follow-up question that targets what they mentioned but did not explain fully.

STRICT RULES:
1. The follow-up must reference a specific identifier or behavior from CODE CONTEXT.
2. The follow-up must be answerable only by someone who read the actual source context.
3. Keep it concise and interview-ready.
4. Return ONLY valid JSON with no markdown/prose.

ORIGINAL QUESTION:
{original_question}

FILE REFERENCE:
{file_reference}

CODE CONTEXT:
{code_context}

DEVELOPER ANSWER:
{developer_answer}

Return this exact JSON:
{{
  "followup_question": "one focused follow-up question",
  "targets_identifier": "specific function/class/variable/behavior name"
}}
"""


REFERENCE_ANSWER_PROMPT = """
You are a senior engineer creating a recruiter review baseline.

QUESTION:
{question_text}

FILE REFERENCE:
{file_reference}

CODE CONTEXT:
{code_context}

Return ONLY valid JSON with this exact shape:
{{
  "reference_answer": "A concise but technically correct answer grounded in the provided code context.",
  "review_checkpoints": [
    "3-5 short bullets a recruiter can use to validate the candidate answer"
  ]
}}

Rules:
- Keep the reference answer focused and concrete.
- Do not invent APIs not present in code context.
- Avoid markdown/code fences.
"""