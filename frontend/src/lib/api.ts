import axios from 'axios'
import type {
  CreateLiveSessionRequest,
  LiveAnswerSubmitResponse,
  LiveCandidateQuestion,
  LiveQuestionRevealResponse,
  LiveSessionAnswer,
  LiveSessionResponse,
} from '@/types/live-session'
import type {
  CreateRepoGroundedChallengeRequest,
  ChallengeResponse,
  ChallengeSubmissionResponse,
  CreateChallengeRequest,
  SubmitChallengeRequest,
} from '@/types/challenge'
import type {
  CreateVideoRoomRequest,
  GeneratePrReviewRequest,
  PrReviewResponse,
  SubmitPrReviewRequest,
  VideoRoomResponse,
} from '@/types/pr-review'
import type {
  QuickChallengeOpenResponse,
  QuickChallengeResponse,
  QuickChallengeSubmitPayload,
} from '@/types/quick-challenge'

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

function getStoredAuthToken() {
  if (typeof window === 'undefined') {
    return null
  }

  const primary = localStorage.getItem('sp_token')
  if (primary && primary.trim().length > 0) {
    return primary
  }

  // Backward compatibility for older key names.
  const legacy = localStorage.getItem('skillproof_token')
  if (legacy && legacy.trim().length > 0) {
    localStorage.setItem('sp_token', legacy)
    return legacy
  }

  return null
}

export const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

export interface NotificationItem {
  id: number
  type: string
  title: string
  message: string
  actionUrl: string
  isRead: boolean
  createdAt?: string
  readAt?: string | null
  senderUsername?: string | null
  emailSent?: boolean
  emailSentAt?: string | null
  metadata?: Record<string, unknown>
}

// Attach JWT token to every request automatically
api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = getStoredAuthToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.code === 'ECONNABORTED' && !error?.response) {
      return Promise.reject({
        ...error,
        response: {
          data: {
            code: 'REQUEST_TIMEOUT',
            message: 'Request timed out. Check backend services and try again.',
            details: {
              timeoutMs: error?.config?.timeout ?? null,
            },
          },
        },
      })
    }

    if (!error?.response) {
      return Promise.reject({
        ...error,
        response: {
          data: {
            code: 'NETWORK_UNAVAILABLE',
            message: 'Could not reach backend services. Verify backend is running on the configured API URL.',
            details: {
              baseURL: error?.config?.baseURL ?? API_BASE,
            },
          },
        },
      })
    }

    return Promise.reject(error)
  },
)

// Auth
export const getGitHubAuthUrl = () => api.get('/api/auth/github')

// Verification
export const startVerification = (repoOwner: string, repoName: string) =>
  api.post('/api/verify/start', { repoOwner, repoName }, { timeout: 120000 })

export const submitAnswers = (
  sessionId: number,
  answers: { questionId: number; answerText: string; skipped?: boolean }[],
  meta?: { totalTabSwitches?: number; pasteCount?: number; totalCopyEvents?: number; avgAnswerSeconds?: number }
) =>
  api.post(
    '/api/verify/submit',
    {
      sessionId,
      answers,
      totalTabSwitches: meta?.totalTabSwitches ?? 0,
      pasteCount: meta?.pasteCount ?? 0,
      totalCopyEvents: meta?.totalCopyEvents ?? 0,
      avgAnswerSeconds: meta?.avgAnswerSeconds ?? 0,
    },
    {
      // AI scoring can exceed 15s when Groq applies 429 backoff; avoid false client timeout.
      timeout: 180000,
    }
  )

export const submitFollowUpAnswers = (
  sessionId: number,
  followUps: {
    questionNumber: number
    followUpQuestion: string
    answerText?: string
    skipped?: boolean
  }[]
) =>
  api.post(
    '/api/verify/submit-followups',
    {
      sessionId,
      followUps,
    },
    {
      timeout: 180000,
    }
  )

// Badge
export const getBadge = (token: string) =>
  api.get(`/api/badge/${token}`)
export const getUserRepos = () => api.get('/api/auth/repos')

export const getRecruiterCandidateDetail = (badgeToken: string) =>
  api.get(`/api/recruiter/candidates/${badgeToken}`)

export const compareRecruiterCandidates = (badgeTokens: string[]) =>
  api.post<{ comparedCount: number; candidates: unknown[]; topCandidateToken: string; generatedAt: string }>(
    '/api/recruiter/compare',
    { badgeTokens }
  )

export const updateRecruiterDecision = (
  badgeToken: string,
  payload: { status: 'VERIFIED' | 'NEEDS_LIVE_INTERVIEW' | 'REJECT'; reason: string; notes?: string }
) => api.put(`/api/recruiter/candidates/${badgeToken}/decision`, payload)

export const exportRecruiterReviewSummary = (badgeToken: string) =>
  api.get(`/api/recruiter/candidates/${badgeToken}/export-summary`)

export const revealRecruiterCandidateAnswer = (badgeToken: string, questionNumber: number) =>
  api.get(`/api/recruiter/candidates/${badgeToken}/questions/${questionNumber}/answer`)

export const getRecruiterReferenceAnswer = (badgeToken: string, questionNumber: number, refresh = false) =>
  api.get(`/api/recruiter/candidates/${badgeToken}/questions/${questionNumber}/reference-answer`, {
    params: refresh ? { refresh: true } : undefined,
  })

export const getNotifications = () =>
  api.get<NotificationItem[]>('/api/notifications')

export const getNotificationCount = () =>
  api.get<{ unreadCount: number }>('/api/notifications/count')

export const markNotificationRead = (id: number) =>
  api.post<NotificationItem>(`/api/notifications/${id}/read`)

export const markAllNotificationsRead = () =>
  api.post<{ updatedCount: number }>('/api/notifications/read-all')

// Live Session
export const createLiveSession = (payload: CreateLiveSessionRequest) =>
  api.post<LiveSessionResponse>('/api/live/sessions', payload)

export const revealNextLiveQuestion = (sessionCode: string) =>
  api.post<LiveQuestionRevealResponse>(`/api/live/${sessionCode}/reveal-next`)

export const getLiveSessionStatus = (sessionCode: string) =>
  api.get<LiveSessionResponse>(`/api/live/${sessionCode}/status`)

export const getCandidateLiveQuestion = (sessionCode: string, questionNumber: number) =>
  api.get<LiveCandidateQuestion>(`/api/live/${sessionCode}/questions/${questionNumber}`)

export const submitCandidateLiveAnswer = (sessionCode: string, questionNumber: number, answerText: string) =>
  api.post<LiveAnswerSubmitResponse>(`/api/live/${sessionCode}/questions/${questionNumber}/answer`, { answerText })

export const getLiveSessionAnswers = (sessionCode: string) =>
  api.get<LiveSessionAnswer[]>(`/api/live/${sessionCode}/answers`)

// Coding Challenges
export const createChallenge = (payload: CreateChallengeRequest) =>
  api.post<ChallengeResponse>('/api/challenges', payload)

export const createRepoGroundedChallenge = (payload: CreateRepoGroundedChallengeRequest) =>
  api.post<ChallengeResponse>('/api/challenges/repo-grounded/generate', payload)

export const getChallenge = (challengeId: number) =>
  api.get<ChallengeResponse>(`/api/challenges/${challengeId}`)

export const submitChallenge = (challengeId: number, payload: SubmitChallengeRequest) =>
  api.post<ChallengeSubmissionResponse>(`/api/challenges/${challengeId}/submit`, payload, {
    timeout: 90000,
  })

export const getChallengeSubmissions = (challengeId: number) =>
  api.get<ChallengeSubmissionResponse[]>(`/api/challenges/${challengeId}/submissions`)

export const getChallengeReferenceAnswer = (challengeId: number) =>
  api.get<{ challengeId: number; title: string; language: string; referenceSolution: string; sourceFilePath?: string; generationReason?: string }>(`/api/challenges/${challengeId}/reference-answer`)

// Quick Challenge
export const generateQuickChallenge = (badgeToken: string) =>
  api.post<QuickChallengeResponse>('/api/quick-challenge/generate', { badgeToken })

export const openQuickChallenge = (token: string) =>
  api.get<QuickChallengeOpenResponse>(`/api/quick-challenge/${token}`)

export const submitQuickChallenge = (token: string, payload: QuickChallengeSubmitPayload) =>
  api.post<QuickChallengeResponse>(`/api/quick-challenge/${token}/submit`, payload)

export const getQuickChallengeResult = (token: string) =>
  api.get<QuickChallengeResponse>(`/api/quick-challenge/${token}/result`)

export const getRecruiterQuickChallenges = () =>
  api.get<QuickChallengeResponse[]>('/api/quick-challenge/my-challenges')

// PR Review Challenge
export const generatePrReview = (payload: GeneratePrReviewRequest) =>
  api.post<PrReviewResponse>('/api/pr-review/generate', payload)

export const openPrReview = (token: string) =>
  api.get<PrReviewResponse>(`/api/pr-review/${token}`)

export const submitPrReview = (token: string, payload: SubmitPrReviewRequest) =>
  api.post<PrReviewResponse>(`/api/pr-review/${token}/submit`, payload)

export const getPrReviewResult = (token: string) =>
  api.get<PrReviewResponse>(`/api/pr-review/${token}/result`)

// Video room
export const createVideoRoom = (payload: CreateVideoRoomRequest) =>
  api.post<VideoRoomResponse>('/api/video/create-room', payload)