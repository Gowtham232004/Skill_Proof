import axios from 'axios'

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

export const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
})

// Attach JWT token to every request automatically
api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('sp_token')
    if (token) config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Auth
export const getGitHubAuthUrl = () => api.get('/api/auth/github')

// Verification
export const startVerification = (repoOwner: string, repoName: string) =>
  api.post('/api/verify/start', { repoOwner, repoName })

export const submitAnswers = (
  sessionId: number,
  answers: { questionId: number; answerText: string; skipped?: boolean }[],
  meta?: { totalTabSwitches?: number; pasteCount?: number; totalCopyEvents?: number; avgAnswerSeconds?: number }
) =>
  api.post('/api/verify/submit', {
    sessionId,
    answers,
    totalTabSwitches: meta?.totalTabSwitches ?? 0,
    pasteCount: meta?.pasteCount ?? 0,
    totalCopyEvents: meta?.totalCopyEvents ?? 0,
    avgAnswerSeconds: meta?.avgAnswerSeconds ?? 0,
  })

export const submitFollowUpAnswers = (
  sessionId: number,
  followUps: {
    questionNumber: number
    followUpQuestion: string
    answerText?: string
    skipped?: boolean
  }[]
) =>
  api.post('/api/verify/submit-followups', {
    sessionId,
    followUps,
  })

// Badge
export const getBadge = (token: string) =>
  api.get(`/api/badge/${token}`)
export const getUserRepos = () => api.get('/api/auth/repos')

export const getRecruiterCandidateDetail = (badgeToken: string) =>
  api.get(`/api/recruiter/candidates/${badgeToken}`)

export const revealRecruiterCandidateAnswer = (badgeToken: string, questionNumber: number) =>
  api.get(`/api/recruiter/candidates/${badgeToken}/questions/${questionNumber}/answer`)