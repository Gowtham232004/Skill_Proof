export interface ChallengeTestCasePreview {
  caseNumber?: number
  name?: string
  stdin?: string
  isVisible?: boolean
  expectedOutput?: string | null
}

export interface ChallengeResponse {
  id: number
  title: string
  description: string
  language: string
  challengeMode?: 'MANUAL' | 'REPO_GROUNDED'
  accessMode?: 'OPEN' | 'ASSIGNED'
  assignedCandidateUsernames?: string[]
  starterCode: string | null
  timeLimitSeconds: number
  expiresAt: string | null
  createdAt: string
  recruiterUsername: string
  sourceBadgeToken?: string | null
  sourceRepoName?: string | null
  sourceFilePath?: string | null
  sourceSnippetHash?: string | null
  generationReason?: string | null
  totalTestCases?: number
  visibleTestCases?: number
  testCases: ChallengeTestCasePreview[]
}

export interface CreateChallengeRequest {
  title: string
  description: string
  language: string
  accessMode?: 'OPEN' | 'ASSIGNED'
  assignedCandidateUsernames?: string[]
  starterCode?: string
  referenceSolution?: string
  testCases: {
    stdin?: string
    expectedOutput: string
    isVisible?: boolean
  }[]
  timeLimitSeconds?: number
  expiresAt?: string
}

export interface SubmitChallengeRequest {
  code: string
}

export interface CreateRepoGroundedChallengeRequest {
  badgeToken: string
  preferredLanguage?: 'python' | 'javascript' | 'java'
  challengeType?: 'REPO_BUG_FIX' | 'REPO_COMPLETION'
  accessMode?: 'OPEN' | 'ASSIGNED'
  assignedCandidateUsernames?: string[]
  timeLimitSeconds?: number
  expiresAt?: string
}

export type ChallengeSubmissionStatus = 'PASSED' | 'FAILED' | 'ERROR'

export interface ChallengeSubmissionTestCaseResult {
  caseNumber: number
  name: string
  status: 'PASS' | 'FAIL' | 'ERROR' | 'UNKNOWN'
  isVisible?: boolean
  expectedOutput: string | null
  actualOutput: string
  errorMessage: string
}

export interface ChallengeSubmissionResponse {
  submissionId: number
  challengeId: number
  candidateUsername: string
  score: number
  status: ChallengeSubmissionStatus
  feedback: string
  stdout: string
  stderr: string
  submittedCode: string
  testCases: ChallengeSubmissionTestCaseResult[]
  createdAt: string
}
