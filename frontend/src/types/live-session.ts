export type LiveSessionStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'EXPIRED'

export interface CreateLiveSessionRequest {
  badgeToken: string
  candidateEmail?: string
}

export interface LiveSessionResponse {
  id: number
  sessionCode: string
  badgeToken: string
  candidateUsername: string
  repoName: string
  currentRevealedQuestion: number
  status: LiveSessionStatus
  liveScore: number | null
  createdAt: string
  expiresAt: string
  recruiterUrl: string
  candidateUrl: string
  totalQuestions: number
}

export interface LiveQuestionRevealResponse {
  sessionCode: string
  questionNumber: number
  totalQuestions: number
  questionText: string
  difficulty: string
  fileReference: string
  codeContext: string
  isLastQuestion: boolean
}

export interface LiveAnswerSubmitResponse {
  sessionCode: string
  questionNumber: number
  accuracyScore: number
  depthScore: number
  specificityScore: number
  compositeScore: number
  aiFeedback: string
  allQuestionsAnswered: boolean
  overallLiveScore: number | null
}

export interface LiveCandidateQuestion {
  questionNumber: number
  questionText: string
  difficulty: string
  fileReference: string
}

export interface LiveSessionAnswer {
  questionNumber: number
  answerText: string
  accuracyScore: number
  depthScore: number
  specificityScore: number
  compositeScore: number
  aiFeedback: string
  questionText: string
  fileReference: string
  codeContext: string
}
