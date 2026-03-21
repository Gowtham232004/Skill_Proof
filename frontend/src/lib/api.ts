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

export const submitAnswers = (sessionId: number, answers: { questionId: number; answerText: string }[]) =>
  api.post('/api/verify/submit', { sessionId, answers })

// Badge
export const getBadge = (token: string) =>
  api.get(`/api/badge/${token}`)
export const getUserRepos = () => api.get('/api/auth/repos')