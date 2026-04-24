export interface QuickChallengeResponse {
  id: number
  challengeToken: string
  badgeToken: string
  candidateUsername: string
  repoName: string
  selectedFilePath: string
  status: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'EXPIRED'
  overallScore: number | null
  accuracyScore: number | null
  depthScore: number | null
  specificityScore: number | null
  aiFeedback: string | null
  tabSwitchCount: number | null
  timeTakenSeconds: number | null
  createdAt: string
  openedAt: string | null
  expiresAt: string
  completedAt: string | null
  codeSnippet: string | null
  questionText: string | null
  secondsRemaining: number
  candidateUrl: string
  candidateAnswer: string | null
}

export type QuickChallengeOpenResponse = QuickChallengeResponse

export interface QuickChallengeSubmitPayload {
  answer: string
  tabSwitches: number
  timeTaken: number
}
