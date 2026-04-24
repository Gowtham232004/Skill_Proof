export type PrReviewSeverity = 'CRITICAL' | 'IMPORTANT' | 'MINOR'

export interface PrReviewCommentInput {
  lineNumber: number
  comment: string
  severity: PrReviewSeverity
}

export interface PrReviewComment {
  lineNumber: number
  comment: string
  severity: PrReviewSeverity
}

export interface PrReviewResponse {
  id: number
  reviewToken: string
  badgeToken: string
  candidateUsername: string
  repoName: string
  filePath: string
  modifiedCode: string | null
  status: 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'EXPIRED'
  overallScore: number | null
  bugsFoundCount: number | null
  aiFeedback: string | null
  timeTakenSeconds: number | null
  createdAt: string
  expiresAt: string
  completedAt: string | null
  candidateUrl: string
  bugDescription: string | null
  originalCode: string | null
  comments: PrReviewComment[] | null
}

export interface GeneratePrReviewRequest {
  badgeToken: string
}

export interface SubmitPrReviewRequest {
  comments: PrReviewCommentInput[]
  timeTaken: number
}

export interface CreateVideoRoomRequest {
  candidateUsername?: string
}

export interface VideoRoomResponse {
  url: string
  name: string
}
