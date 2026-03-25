'use client'
import { useEffect, useState, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { motion, AnimatePresence } from 'framer-motion'
import { api, startVerification, submitAnswers } from '@/lib/api'
import { formatCooldownFromMinutes, parseApiError } from '@/lib/apiError'
import ErrorBanner from '@/app/components/ErrorBanner'
import SuccessBanner from '@/app/components/SuccessBanner'

// ── Types ─────────────────────────────────────────────────────────────────────
interface Repo {
  name: string
  description: string
  language: string
  stargazers_count: number
  updated_at: string
  private: boolean
}

interface Question {
  id: number
  questionNumber: number
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  fileReference: string
  questionText: string
  codeContextSnippet?: string
}

interface VerifyResult {
  sessionId: number
  overallScore: number
  technicalScore?: number
  integrityAdjustedScore?: number
  integrityPenaltyTotal?: number
  integrityPenaltyBreakdown?: {
    pastePenalty?: number
    speedPenalty?: number
    tabSwitchPenalty?: number
  }
  backendScore: number
  apiDesignScore: number
  errorHandlingScore: number
  codeQualityScore: number
  documentationScore: number
  repoAttemptCount?: number
  confidenceTier?: 'High' | 'Medium' | 'Low'
  tabSwitches?: number
  pasteCount?: number
  avgAnswerSeconds?: number
  badgeToken: string
  badgeUrl: string
  topGaps: string[]
  questionResults: {
    questionNumber: number
    difficulty: string
    fileReference: string
    questionText: string
    accuracyScore: number
    depthScore: number
    specificityScore: number
    compositeScore: number
    aiFeedback: string
  }[]
}

// ── Helpers ───────────────────────────────────────────────────────────────────
const DIFF_COLOR: Record<string, string> = {
  EASY: '#34D399', MEDIUM: '#F59E0B', HARD: '#F472B6'
}

function ScoreRing({ score, color, size = 120 }: { score: number; color: string; size?: number }) {
  const r = size * 0.38
  const circ = 2 * Math.PI * r
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <circle cx={size/2} cy={size/2} r={r} fill="none"
        stroke="rgba(255,255,255,0.05)" strokeWidth={size * 0.06} />
      <motion.circle cx={size/2} cy={size/2} r={r} fill="none"
        stroke={color} strokeWidth={size * 0.06} strokeLinecap="round"
        strokeDasharray={circ}
        initial={{ strokeDashoffset: circ }}
        animate={{ strokeDashoffset: circ * (1 - score / 100) }}
        transition={{ duration: 1.5, ease: 'easeOut' }}
        transform={`rotate(-90 ${size/2} ${size/2})`}
        style={{ filter: `drop-shadow(0 0 6px ${color}60)` }} />
      <text x={size/2} y={size/2 + 2} textAnchor="middle" dominantBaseline="middle"
        fill={color} fontSize={size * 0.22} fontWeight={900}
        fontFamily="JetBrains Mono, monospace">{score}</text>
      <text x={size/2} y={size/2 + size * 0.18} textAnchor="middle"
        fill="rgba(255,255,255,0.3)" fontSize={size * 0.1}
        fontFamily="JetBrains Mono, monospace">/100</text>
    </svg>
  )
}

// ── STEP COMPONENTS ───────────────────────────────────────────────────────────

// Step 1: Repo selection
function StepSelectRepo({ onSelect }: { onSelect: (repo: Repo) => void }) {
  const [repos, setRepos] = useState<Repo[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/api/auth/repos').then(res => {
      setRepos(res.data)
      setLoading(false)
    }).catch((err) => {
      const parsed = parseApiError(err, 'Could not load repositories. Make sure you are logged in.')
      setError(parsed.message)
      setLoading(false)
    })
  }, [])

  const filtered = repos.filter(r =>
    r.name.toLowerCase().includes(search.toLowerCase())
  )

  if (loading) return (
    <div style={{ textAlign: 'center', padding: '60px 0' }}>
      <div style={{ width: 40, height: 40, border: '2px solid rgba(212,255,0,0.3)', borderTopColor: '#D4FF00', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 16px' }} />
      <style dangerouslySetInnerHTML={{ __html: `@keyframes spin { to { transform: rotate(360deg); } }` }} />
      <p style={{ color: 'rgba(255,255,255,0.4)', fontFamily: 'JetBrains Mono, monospace', fontSize: 13 }}>Loading your repositories...</p>
    </div>
  )

  if (error) return (
    <div style={{ textAlign: 'center', padding: '60px 24px' }}>
      <ErrorBanner message={error} compact />
      <button onClick={() => window.location.href = '/api/auth/github'}
        style={{ background: '#D4FF00', color: '#000', border: 'none', padding: '12px 28px', borderRadius: 10, fontWeight: 700, cursor: 'pointer', marginTop: 14 }}>
        Login with GitHub
      </button>
    </div>
  )

  return (
    <div>
      <input
        value={search}
        onChange={e => setSearch(e.target.value)}
        placeholder="Search repositories..."
        style={{ width: '100%', padding: '12px 16px', background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 12, color: '#fff', fontSize: 14, fontFamily: 'Outfit, sans-serif', marginBottom: 16, outline: 'none', boxSizing: 'border-box' }}
      />
      <div style={{ display: 'grid', gap: 10, maxHeight: 420, overflowY: 'auto', paddingRight: 4 }}>
        {filtered.length === 0 ? (
          <p style={{ color: 'rgba(255,255,255,0.3)', textAlign: 'center', padding: '40px 0', fontFamily: 'JetBrains Mono, monospace', fontSize: 13 }}>No repositories found</p>
        ) : filtered.map((repo, i) => (
          <motion.div key={i}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.04 }}
            whileHover={{ borderColor: 'rgba(212,255,0,0.4)', x: 4 }}
            onClick={() => onSelect(repo)}
            style={{ padding: '16px 20px', background: '#0D0D0D', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 14, cursor: 'pointer', transition: 'border-color 0.2s' }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 4, color: '#D4FF00' }}>{repo.name}</div>
                {repo.description && (
                  <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.4)', lineHeight: 1.5, marginBottom: 8 }}>{repo.description}</div>
                )}
                <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                  {repo.language && (
                    <span style={{ fontSize: 12, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.3)' }}>
                      ● {repo.language}
                    </span>
                  )}
                  <span style={{ fontSize: 12, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.2)' }}>
                    ★ {repo.stargazers_count}
                  </span>
                </div>
              </div>
              <div style={{ fontSize: 20, color: 'rgba(255,255,255,0.15)', flexShrink: 0 }}>→</div>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  )
}

// Step 2: Analyzing
function StepAnalyzing({ repo, onDone }: { repo: Repo; onDone: (sessionId: number, questions: Question[]) => void }) {
  const [stage, setStage] = useState(0)
  const [error, setError] = useState('')
  const called = useRef(false)

  const STAGES = [
    'Fetching repository file tree...',
    'Filtering relevant source files...',
    'Extracting code structure...',
    'Generating AI questions from your code...',
    'Questions ready.',
  ]

  useEffect(() => {
    if (called.current) return
    called.current = true

    // Animate through stages while API call happens
    const intervals = STAGES.map((_, i) =>
      setTimeout(() => setStage(i), i * 900)
    )

    // Get user info from localStorage
    const userStr = localStorage.getItem('sp_user')
    const user = userStr ? JSON.parse(userStr) : null
    const owner = user?.githubUsername || ''

    startVerification(owner, repo.name)
      .then(res => {
        // Clear intervals
        intervals.forEach(clearTimeout)
        setStage(4)
        setTimeout(() => onDone(res.data.sessionId, res.data.questions), 800)
      })
      .catch(err => {
        intervals.forEach(clearTimeout)
        const parsed = parseApiError(err, 'Could not start verification for this repository.')
        if (parsed.code === 'COOLDOWN_ACTIVE') {
          const waitText = formatCooldownFromMinutes(parsed.details?.remainingMinutes)
          if (waitText) {
            setError(`Cooldown active for this repository. Retry in ${waitText}.`)
            return
          }
        }
        setError(parsed.message)
      })

    return () => intervals.forEach(clearTimeout)
  }, [])

  return (
    <div style={{ padding: '40px 0', textAlign: 'center' }}>
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 2, repeat: Infinity, ease: 'linear' }}
        style={{ width: 64, height: 64, margin: '0 auto 32px', position: 'relative' }}
      >
        <svg width="64" height="64" viewBox="0 0 64 64">
          <circle cx="32" cy="32" r="28" fill="none" stroke="rgba(212,255,0,0.1)" strokeWidth="3" />
          <circle cx="32" cy="32" r="28" fill="none" stroke="#D4FF00" strokeWidth="3"
            strokeDasharray="40 136" strokeLinecap="round"
            style={{ filter: 'drop-shadow(0 0 8px #D4FF00)' }} />
        </svg>
      </motion.div>

      <div style={{ fontFamily: 'JetBrains Mono, monospace', color: 'rgba(212,255,0,0.6)', fontSize: 12, marginBottom: 8, letterSpacing: '0.1em' }}>
        ANALYZING · {repo.name}
      </div>

      <div style={{ maxWidth: 400, margin: '0 auto', padding: '20px 0' }}>
        {STAGES.map((s, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, x: -10 }}
            animate={{ opacity: i <= stage ? 1 : 0.2, x: 0 }}
            transition={{ duration: 0.4 }}
            style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', justifyContent: 'flex-start', textAlign: 'left' }}
          >
            <span style={{ fontSize: 14, color: i < stage ? '#34D399' : i === stage ? '#D4FF00' : 'rgba(255,255,255,0.15)' }}>
              {i < stage ? '✓' : i === stage ? '⟳' : '○'}
            </span>
            <span style={{ fontSize: 13, fontFamily: 'JetBrains Mono, monospace', color: i < stage ? 'rgba(255,255,255,0.5)' : i === stage ? '#fff' : 'rgba(255,255,255,0.2)' }}>
              {s}
            </span>
          </motion.div>
        ))}
      </div>
      {error && (
        <ErrorBanner message={error} code="VERIFY_START_ERROR" />
      )}
    </div>
  )
}

// Step 3: Answer questions
function StepAnswerQuestions({
  questions, sessionId, onSubmit
}: {
  questions: Question[]
  sessionId: number
  onSubmit: (result: VerifyResult) => void
}) {
  const [current, setCurrent] = useState(0)
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const [skipped, setSkipped] = useState<Record<number, boolean>>({})
  const [questionViewStartedAt, setQuestionViewStartedAt] = useState<number>(Date.now())
  const [questionDurationsSeconds, setQuestionDurationsSeconds] = useState<Record<number, number>>({})
  const [totalTabSwitches, setTotalTabSwitches] = useState(0)
  const [pasteCount, setPasteCount] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const [invalidQuestionId, setInvalidQuestionId] = useState<number | null>(null)

  const maxSkips = 2
  const skippedCount = Object.values(skipped).filter(Boolean).length

  const q = questions[current]
  const progress = ((current + 1) / questions.length) * 100
  const canNext = !!(q && (skipped[q.id] || (answers[q.id] || '').trim().length > 30))
  const allAnswered = questions.every(q => skipped[q.id] || (answers[q.id] || '').trim().length > 30)

  useEffect(() => {
    setQuestionViewStartedAt(Date.now())
  }, [current])

  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.hidden) {
        setTotalTabSwitches(prev => prev + 1)
      }
    }

    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => document.removeEventListener('visibilitychange', onVisibilityChange)
  }, [])

  const captureCurrentQuestionDuration = () => {
    if (!q) {
      return questionDurationsSeconds
    }

    const elapsedSeconds = Math.max(1, Math.round((Date.now() - questionViewStartedAt) / 1000))
    const snapshot = {
      ...questionDurationsSeconds,
      [q.id]: (questionDurationsSeconds[q.id] || 0) + elapsedSeconds,
    }

    setQuestionDurationsSeconds(snapshot)
    setQuestionViewStartedAt(Date.now())
    return snapshot
  }

  const handleSkipCurrent = () => {
    if (!q) return
    const isAlreadySkipped = !!skipped[q.id]
    if (!isAlreadySkipped && skippedCount >= maxSkips) return
    setSkipped(prev => ({ ...prev, [q.id]: !isAlreadySkipped }))
    setSubmitError('')
    setInvalidQuestionId(null)
  }

  const handleSubmit = async () => {
    setSubmitting(true)
    setSubmitError('')
    setInvalidQuestionId(null)
    try {
      const durationSnapshot = captureCurrentQuestionDuration()
      const totalDuration = Object.values(durationSnapshot).reduce((sum, seconds) => sum + seconds, 0)
      const avgAnswerSeconds = questions.length > 0 ? Math.round(totalDuration / questions.length) : 0

      const payload = questions.map(q => ({
        questionId: q.id,
        answerText: skipped[q.id] ? '' : (answers[q.id] || ''),
        skipped: !!skipped[q.id]
      }))
      const res = await submitAnswers(sessionId, payload, {
        totalTabSwitches,
        pasteCount,
        avgAnswerSeconds,
      })
      onSubmit(res.data)
    } catch (e: any) {
      const parsed = parseApiError(e, 'Could not submit answers. Please try again.')
      if (parsed.code === 'ANSWER_TOO_SHORT') {
        const questionNumber = Number(parsed.details?.questionNumber)
        if (!Number.isNaN(questionNumber)) {
          const invalidQuestion = questions.find(item => item.questionNumber === questionNumber)
          if (invalidQuestion) {
            const invalidIndex = questions.findIndex(item => item.id === invalidQuestion.id)
            if (invalidIndex >= 0) setCurrent(invalidIndex)
            setInvalidQuestionId(invalidQuestion.id)
          }
        }
      }

      setSubmitError(parsed.message)
      setSubmitting(false)
    }
  }

  if (!q) return null

  return (
    <div>
      {/* Progress */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
          <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: 'rgba(255,255,255,0.3)' }}>
            Question {current + 1} of {questions.length}
          </span>
          <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: '#D4FF00' }}>
            {Math.round(progress)}%
          </span>
        </div>
        <div style={{ height: 3, background: 'rgba(255,255,255,0.06)', borderRadius: 2, overflow: 'hidden' }}>
          <motion.div
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.4 }}
            style={{ height: '100%', background: '#D4FF00', borderRadius: 2, boxShadow: '0 0 8px rgba(212,255,0,0.5)' }}
          />
        </div>

        {/* Question tabs */}
        <div style={{ display: 'flex', gap: 6, marginTop: 12 }}>
          {questions.map((_, i) => (
            <motion.button
              key={i}
              onClick={() => {
                captureCurrentQuestionDuration()
                setCurrent(i)
              }}
              animate={{ background: i === current ? '#D4FF00' : skipped[questions[i].id] ? 'rgba(245,158,11,0.35)' : answers[questions[i].id]?.trim().length > 30 ? 'rgba(52,211,153,0.3)' : 'rgba(255,255,255,0.06)' }}
              style={{ flex: 1, height: 6, borderRadius: 3, border: 'none', cursor: 'pointer' }}
            />
          ))}
        </div>
      </div>

      {/* Question card */}
      <AnimatePresence mode="wait">
        <motion.div
          key={current}
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -20 }}
          transition={{ duration: 0.3 }}
        >
          {/* Difficulty + file badge */}
          <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
            <span style={{ padding: '4px 10px', borderRadius: 6, background: `${DIFF_COLOR[q.difficulty]}18`, border: `1px solid ${DIFF_COLOR[q.difficulty]}40`, fontSize: 11, fontWeight: 700, color: DIFF_COLOR[q.difficulty], fontFamily: 'JetBrains Mono, monospace' }}>
              {q.difficulty}
            </span>
            <span style={{ padding: '4px 10px', borderRadius: 6, background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', fontSize: 11, color: 'rgba(255,255,255,0.4)', fontFamily: 'JetBrains Mono, monospace' }}>
              📄 {q.fileReference}
            </span>
          </div>

          <p style={{ fontSize: 17, lineHeight: 1.7, color: '#fff', marginBottom: 20, fontWeight: 500 }}>
            {q.questionText}
          </p>

          {q.codeContextSnippet && (
            <div style={{ marginBottom: 16, border: '1px solid rgba(96,165,250,0.24)', background: 'rgba(96,165,250,0.08)', borderRadius: 10, overflow: 'hidden' }}>
              <div style={{ padding: '8px 12px', borderBottom: '1px solid rgba(96,165,250,0.18)', fontSize: 11, letterSpacing: '0.08em', color: 'rgba(147,197,253,0.9)', fontFamily: 'JetBrains Mono, monospace' }}>
                REFERENCED CODE
              </div>
              <pre style={{ margin: 0, padding: '12px 14px', maxHeight: 260, overflow: 'auto', color: 'rgba(226,232,240,0.92)', fontSize: 12, lineHeight: 1.5, fontFamily: 'JetBrains Mono, monospace', background: 'rgba(2,6,23,0.55)' }}>
                {q.codeContextSnippet}
              </pre>
            </div>
          )}

          <textarea
            value={answers[q.id] || ''}
            onChange={e => {
              setAnswers(prev => ({ ...prev, [q.id]: e.target.value }))
              if (invalidQuestionId === q.id) {
                setInvalidQuestionId(null)
                setSubmitError('')
              }
            }}
            onPaste={() => setPasteCount(prev => prev + 1)}
            placeholder="Explain your implementation decision in detail. Reference specific components, functions, or architectural choices..."
            rows={6}
            disabled={!!skipped[q.id]}
            style={{ width: '100%', padding: '14px 16px', background: skipped[q.id] ? 'rgba(245,158,11,0.07)' : 'rgba(255,255,255,0.04)', border: `1px solid ${skipped[q.id] ? 'rgba(245,158,11,0.35)' : invalidQuestionId === q.id ? 'rgba(244,114,182,0.8)' : canNext ? 'rgba(212,255,0,0.3)' : 'rgba(255,255,255,0.1)'}`, borderRadius: 12, color: '#fff', fontSize: 14, fontFamily: 'Outfit, sans-serif', resize: 'vertical', outline: 'none', lineHeight: 1.6, transition: 'border-color 0.2s', boxSizing: 'border-box' }}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 6 }}>
            <span style={{ fontSize: 12, fontFamily: 'JetBrains Mono, monospace', color: canNext ? '#34D399' : 'rgba(255,255,255,0.2)' }}>
              {skipped[q.id] ? `Skipped · ${Math.max(0, maxSkips - skippedCount)} skips left` : `${(answers[q.id] || '').trim().length} chars ${!canNext ? '· minimum 30' : ''}`}
            </span>
            <button
              onClick={handleSkipCurrent}
              disabled={!skipped[q.id] && skippedCount >= maxSkips}
              style={{ fontSize: 12, padding: '5px 10px', background: skipped[q.id] ? 'rgba(245,158,11,0.2)' : 'transparent', border: '1px solid rgba(245,158,11,0.4)', borderRadius: 8, color: '#F59E0B', fontWeight: 700, cursor: !skipped[q.id] && skippedCount >= maxSkips ? 'not-allowed' : 'pointer', opacity: !skipped[q.id] && skippedCount >= maxSkips ? 0.4 : 1, fontFamily: 'JetBrains Mono, monospace' }}>
              {skipped[q.id] ? 'Unskip' : `Skip (${Math.max(0, maxSkips - skippedCount)} left)`}
            </button>
          </div>

          {submitError && (
            <ErrorBanner message={submitError} code="VERIFY_SUBMIT_ERROR" />
          )}
        </motion.div>
      </AnimatePresence>

      {/* Navigation */}
      <div style={{ display: 'flex', gap: 10, marginTop: 24 }}>
        {current > 0 && (
          <motion.button whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
            onClick={() => {
              captureCurrentQuestionDuration()
              setCurrent(c => c - 1)
            }}
            style={{ padding: '13px 24px', background: 'transparent', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 12, color: 'rgba(255,255,255,0.6)', fontSize: 14, fontWeight: 600, cursor: 'pointer', fontFamily: 'Outfit, sans-serif' }}>
            ← Previous
          </motion.button>
        )}

        {current < questions.length - 1 ? (
          <motion.button whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
            onClick={() => {
              captureCurrentQuestionDuration()
              setCurrent(c => c + 1)
            }}
            style={{ flex: 1, padding: '13px 24px', background: canNext ? '#D4FF00' : 'rgba(255,255,255,0.06)', border: 'none', borderRadius: 12, color: canNext ? '#000' : 'rgba(255,255,255,0.3)', fontSize: 14, fontWeight: 700, cursor: canNext ? 'pointer' : 'not-allowed', fontFamily: 'Outfit, sans-serif', transition: 'all 0.2s' }}>
            Next Question →
          </motion.button>
        ) : (
          <motion.button whileHover={allAnswered ? { scale: 1.02 } : {}} whileTap={allAnswered ? { scale: 0.98 } : {}}
            onClick={allAnswered ? handleSubmit : undefined}
            disabled={!allAnswered || submitting}
            style={{ flex: 1, padding: '13px 24px', background: allAnswered ? '#D4FF00' : 'rgba(255,255,255,0.06)', border: 'none', borderRadius: 12, color: allAnswered ? '#000' : 'rgba(255,255,255,0.3)', fontSize: 14, fontWeight: 800, cursor: allAnswered ? 'pointer' : 'not-allowed', fontFamily: 'Outfit, sans-serif', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
            {submitting ? (
              <>
                <div style={{ width: 16, height: 16, border: '2px solid rgba(0,0,0,0.3)', borderTopColor: '#000', borderRadius: '50%', animation: 'spin 0.6s linear infinite' }} />
                Evaluating Answers...
              </>
            ) : allAnswered ? 'Submit All Answers →' : `Answer all ${questions.length} questions first`}
          </motion.button>
        )}
      </div>
    </div>
  )
}

// SkillGapSection Component
function SkillGapSection({ gaps }: { gaps: string[] }) {
  const skillIcons: Record<string, { icon: string; color: string; suggestion: string }> = {
    'API Design': { icon: '🏗️', color: '#60A5FA', suggestion: 'Focus on RESTful principles and endpoint structure' },
    'Error Handling': { icon: '⚠️', color: '#F59E0B', suggestion: 'Add comprehensive try-catch blocks and validation' },
    'Code Quality': { icon: '✨', color: '#34D399', suggestion: 'Refactor for clarity, modularity, and maintainability' },
    'Documentation': { icon: '📝', color: '#F472B6', suggestion: 'Add comments, docstrings, and architectural docs' },
    'Backend Logic': { icon: '⚙️', color: '#D4FF00', suggestion: 'Strengthen core logic and business rules' },
    'Performance': { icon: '⚡', color: '#06B6D4', suggestion: 'Optimize algorithms and reduce complexity' },
    'Testing': { icon: '🧪', color: '#8B5CF6', suggestion: 'Add unit tests and integration tests' },
    'Security': { icon: '🔒', color: '#EF4444', suggestion: 'Implement proper authentication and input validation' },
  }

  const getGapInfo = (gap: string) => {
    for (const [key, value] of Object.entries(skillIcons)) {
      if (gap.toLowerCase().includes(key.toLowerCase())) {
        return value
      }
    }
    return { icon: '🎯', color: '#A78BFA', suggestion: 'Work on this skill to improve overall performance' }
  }

  if (!gaps || gaps.length === 0) return null

  return (
    <div style={{ marginBottom: 24 }}>
      <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.4)', marginBottom: 12, letterSpacing: '0.1em' }}>
        RECOMMENDED IMPROVEMENTS
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: gaps.length === 1 ? '1fr' : 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
        {gaps.map((gap, i) => {
          const { icon, color, suggestion } = getGapInfo(gap)
          return (
            <motion.div
              key={i}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: i * 0.1 }}
              style={{
                padding: '14px 12px',
                background: `${color}08`,
                border: `1px solid ${color}20`,
                borderRadius: 10,
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
              }}
            >
              <div style={{ fontSize: 24 }}>{icon}</div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 700, color: color, fontFamily: 'Outfit, sans-serif', marginBottom: 4 }}>
                  {gap}
                </div>
                <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.4)', lineHeight: 1.4, fontFamily: 'Outfit, sans-serif' }}>
                  {suggestion}
                </div>
              </div>
            </motion.div>
          )
        })}
      </div>
    </div>
  )
}

// Step 4: Results
function StepResults({ result, onNewVerification }: { result: VerifyResult; onNewVerification: () => void }) {
  const router = useRouter()
  const [copiedBadgeUrl, setCopiedBadgeUrl] = useState(false)
  const SKILL_LABELS = [
    { key: 'backendScore', label: 'Backend Logic', color: '#D4FF00' },
    { key: 'apiDesignScore', label: 'API Design', color: '#60A5FA' },
    { key: 'errorHandlingScore', label: 'Error Handling', color: '#F59E0B' },
    { key: 'codeQualityScore', label: 'Code Quality', color: '#34D399' },
    { key: 'documentationScore', label: 'Documentation', color: '#F472B6' },
  ] as const

  const adjustedScore = result.integrityAdjustedScore ?? result.overallScore
  const technicalScore = result.technicalScore ?? result.overallScore
  const scoreColor = adjustedScore >= 80 ? '#34D399' : adjustedScore >= 60 ? '#F59E0B' : '#F472B6'

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.5 }}>
      {/* Score */}
      <div style={{ textAlign: 'center', marginBottom: 32 }}>
        <motion.div initial={{ scale: 0, rotate: -20 }} animate={{ scale: 1, rotate: 0 }}
          transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1], delay: 0.2 }}
          style={{ display: 'inline-block' }}>
          <ScoreRing score={adjustedScore} color={scoreColor} size={140} />
        </motion.div>
        <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: 'rgba(255,255,255,0.3)', marginTop: 8, letterSpacing: '0.1em' }}>
          INTEGRITY-ADJUSTED SCORE
        </div>
        <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(255,255,255,0.48)', fontFamily: 'JetBrains Mono, monospace' }}>
          technical {technicalScore} · penalty -{result.integrityPenaltyTotal ?? 0}
        </div>
      </div>

      {(result.integrityPenaltyTotal ?? 0) > 0 && (
        <div style={{ marginBottom: 22, padding: '14px 16px', background: 'rgba(245,158,11,0.08)', border: '1px solid rgba(245,158,11,0.25)', borderRadius: 12 }}>
          <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: '#F59E0B', marginBottom: 8, letterSpacing: '0.08em' }}>
            INTEGRITY ADJUSTMENT APPLIED
          </div>
          <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.6)', lineHeight: 1.6 }}>
            Your technical score is preserved, but a soft integrity deduction was applied based on telemetry signals.
          </div>
          {result.integrityPenaltyBreakdown && (
            <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(255,255,255,0.55)', fontFamily: 'JetBrains Mono, monospace' }}>
              paste {result.integrityPenaltyBreakdown.pastePenalty ?? 0} · speed {result.integrityPenaltyBreakdown.speedPenalty ?? 0} · tab {result.integrityPenaltyBreakdown.tabSwitchPenalty ?? 0}
            </div>
          )}
        </div>
      )}

      {/* Skill bars */}
      <div style={{ marginBottom: 28 }}>
        {SKILL_LABELS.map((skill, i) => (
          <div key={skill.key} style={{ marginBottom: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
              <span style={{ fontSize: 13, color: 'rgba(255,255,255,0.5)' }}>{skill.label}</span>
              <span style={{ fontSize: 13, fontWeight: 800, color: skill.color, fontFamily: 'JetBrains Mono, monospace' }}>
                {(result as any)[skill.key]}
              </span>
            </div>
            <div style={{ height: 5, background: 'rgba(255,255,255,0.05)', borderRadius: 3, overflow: 'hidden' }}>
              <motion.div
                initial={{ width: 0 }}
                animate={{ width: `${(result as any)[skill.key]}%` }}
                transition={{ duration: 1.2, delay: i * 0.1, ease: 'easeOut' }}
                style={{ height: '100%', background: skill.color, borderRadius: 3, boxShadow: `0 0 6px ${skill.color}50` }}
              />
            </div>
          </div>
        ))}
      </div>

      {/* Top gaps */}
      {result.topGaps && result.topGaps.length > 0 && (
        <div style={{ marginBottom: 24, padding: '16px 18px', background: 'rgba(244,114,182,0.06)', border: '1px solid rgba(244,114,182,0.2)', borderRadius: 12 }}>
          <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: '#F472B6', marginBottom: 10, letterSpacing: '0.1em' }}>
            TOP GAPS TO IMPROVE
          </div>
          {result.topGaps.map((gap, i) => (
            <div key={i} style={{ fontSize: 13, color: 'rgba(255,255,255,0.5)', marginBottom: 4 }}>
              · {gap}
            </div>
          ))}
        </div>
      )}

      {/* Skill Gap Recommendations */}
      <SkillGapSection gaps={result.topGaps || []} />

      {/* Badge URL */}
      <div style={{ marginBottom: 24, padding: '16px 18px', background: 'rgba(212,255,0,0.05)', border: '1px solid rgba(212,255,0,0.2)', borderRadius: 12 }}>
        <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: '#D4FF00', marginBottom: 8, letterSpacing: '0.1em' }}>
          YOUR BADGE URL
        </div>
        <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: 'rgba(255,255,255,0.5)', wordBreak: 'break-all', marginBottom: 10 }}>
          {result.badgeUrl}
        </div>
        <button
          onClick={() => {
            navigator.clipboard.writeText(result.badgeUrl)
            setCopiedBadgeUrl(true)
            setTimeout(() => setCopiedBadgeUrl(false), 1800)
          }}
          style={{ fontSize: 12, padding: '6px 14px', background: '#D4FF00', color: '#000', border: 'none', borderRadius: 7, fontWeight: 700, cursor: 'pointer', fontFamily: 'Outfit, sans-serif' }}>
          Copy Badge URL
        </button>
        {copiedBadgeUrl && (
          <SuccessBanner message="Badge URL copied to clipboard." compact />
        )}
        {typeof result.repoAttemptCount === 'number' && (
          <div style={{ marginTop: 10, fontSize: 12, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>
            Repository attempts completed: {result.repoAttemptCount}
          </div>
        )}
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', gap: 10 }}>
        <motion.button whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
          onClick={() => router.push(`/badge/${result.badgeToken}`)}
          style={{ flex: 1, padding: '14px', background: '#D4FF00', color: '#000', border: 'none', borderRadius: 12, fontWeight: 800, fontSize: 15, cursor: 'pointer', fontFamily: 'Outfit, sans-serif' }}>
          View My Badge →
        </motion.button>
        <motion.button whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
          onClick={onNewVerification}
          style={{ padding: '14px 20px', background: 'transparent', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 12, color: 'rgba(255,255,255,0.6)', fontSize: 14, fontWeight: 600, cursor: 'pointer', fontFamily: 'Outfit, sans-serif' }}>
          Verify Another
        </motion.button>
      </div>
    </motion.div>
  )
}

// ── MAIN WIZARD ───────────────────────────────────────────────────────────────
const WIZARD_STEPS = ['Select Repo', 'Analyzing', 'Answer Questions', 'Your Badge']

export default function VerifyPage() {
  const router = useRouter()
  const [step, setStep] = useState(0)
  const [selectedRepo, setSelectedRepo] = useState<Repo | null>(null)
  const [sessionId, setSessionId] = useState<number | null>(null)
  const [questions, setQuestions] = useState<Question[]>([])
  const [result, setResult] = useState<VerifyResult | null>(null)
  const [user, setUser] = useState<any>(null)

  useEffect(() => {
    const token = localStorage.getItem('sp_token')
    const userStr = localStorage.getItem('sp_user')
    if (!token) {
      router.push('/')
      return
    }
    if (userStr) setUser(JSON.parse(userStr))
  }, [])

  const handleRepoSelect = (repo: Repo) => {
    setSelectedRepo(repo)
    setStep(1)
  }

  const handleAnalysisDone = (sid: number, qs: Question[]) => {
    setSessionId(sid)
    setQuestions(qs)
    setStep(2)
  }

  const handleAnswersSubmitted = (res: VerifyResult) => {
    setResult(res)
    setStep(3)
  }

  const handleNewVerification = () => {
    setStep(0)
    setSelectedRepo(null)
    setSessionId(null)
    setQuestions([])
    setResult(null)
  }

  const stepContent = [
    <StepSelectRepo key="1" onSelect={handleRepoSelect} />,
    selectedRepo ? <StepAnalyzing key="2" repo={selectedRepo} onDone={handleAnalysisDone} /> : null,
    questions.length > 0 && sessionId ? <StepAnswerQuestions key="3" questions={questions} sessionId={sessionId} onSubmit={handleAnswersSubmitted} /> : null,
    result ? <StepResults key="4" result={result} onNewVerification={handleNewVerification} /> : null,
  ]

  return (
    <div style={{ minHeight: '100vh', background: '#000', color: '#fff', fontFamily: 'Outfit, sans-serif' }}>
      <style dangerouslySetInnerHTML={{ __html: `
        @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500&display=swap');
        @keyframes spin { to { transform: rotate(360deg); } }
        textarea::placeholder { color: rgba(255,255,255,0.2); }
        input::placeholder { color: rgba(255,255,255,0.2); }
        textarea:focus, input:focus { border-color: rgba(212,255,0,0.4) !important; }
        ::-webkit-scrollbar { width: 4px; }
        ::-webkit-scrollbar-track { background: #000; }
        ::-webkit-scrollbar-thumb { background: rgba(212,255,0,0.2); border-radius: 2px; }
      ` }} />

      {/* Nav */}
      <nav style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 100, background: 'rgba(0,0,0,0.9)', backdropFilter: 'blur(20px)', borderBottom: '1px solid rgba(255,255,255,0.06)', height: 60, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px' }}>
        <motion.div whileHover={{ scale: 1.05 }} onClick={() => router.push('/')} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}>
          <div style={{ width: 32, height: 32, borderRadius: 8, background: '#D4FF00', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'JetBrains Mono, monospace', fontWeight: 800, fontSize: 11, color: '#000' }}>SP</div>
          <span style={{ fontWeight: 900, fontSize: 16, letterSpacing: '-0.02em' }}>SkillProof</span>
        </motion.div>
        {user && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src={user.avatarUrl} alt="" style={{ width: 28, height: 28, borderRadius: '50%', border: '2px solid rgba(212,255,0,0.3)' }} />
            <span style={{ fontSize: 13, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.4)' }}>@{user.githubUsername}</span>
          </div>
        )}
      </nav>

      {/* Content */}
      <div style={{ maxWidth: 680, margin: '0 auto', padding: '90px 24px 60px' }}>

        {/* Wizard steps header */}
        <div style={{ marginBottom: 36 }}>
          <div style={{ display: 'flex', gap: 0, position: 'relative', marginBottom: 24 }}>
            <div style={{ position: 'absolute', top: 14, left: '6%', right: '6%', height: 1, background: 'rgba(255,255,255,0.06)' }} />
            {WIZARD_STEPS.map((label, i) => (
              <div key={i} style={{ flex: 1, textAlign: 'center', position: 'relative', zIndex: 1 }}>
                <motion.div
                  animate={{ background: i < step ? '#34D399' : i === step ? '#D4FF00' : 'rgba(255,255,255,0.06)', borderColor: i <= step ? 'transparent' : 'rgba(255,255,255,0.1)' }}
                  style={{ width: 28, height: 28, borderRadius: '50%', margin: '0 auto 8px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 800, color: i <= step ? '#000' : 'rgba(255,255,255,0.2)', fontFamily: 'JetBrains Mono, monospace', border: '1px solid' }}
                >
                  {i < step ? '✓' : i + 1}
                </motion.div>
                <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: i === step ? '#D4FF00' : i < step ? 'rgba(255,255,255,0.4)' : 'rgba(255,255,255,0.2)', letterSpacing: '0.05em' }}>
                  {label}
                </div>
              </div>
            ))}
          </div>

          {/* Step title */}
          <AnimatePresence mode="wait">
            <motion.h1 key={step}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.3 }}
              style={{ fontSize: 'clamp(24px, 4vw, 36px)', fontWeight: 900, letterSpacing: '-0.03em', margin: 0 }}
            >
              {step === 0 && 'Choose a repository to verify'}
              {step === 1 && <>Analyzing <span style={{ color: '#D4FF00' }}>{selectedRepo?.name}</span>...</>}
              {step === 2 && 'Answer 5 questions about your code'}
              {step === 3 && <>Your badge is <span style={{ color: '#D4FF00' }}>ready.</span></>}
            </motion.h1>
          </AnimatePresence>
        </div>

        {/* Step content */}
        <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 20, padding: 28 }}>
          <AnimatePresence mode="wait">
            <motion.div key={step}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
            >
              {stepContent[step]}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </div>
  )
}