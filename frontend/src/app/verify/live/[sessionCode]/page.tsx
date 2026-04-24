'use client'

import { useCallback, useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { motion } from 'framer-motion'
import {
  getCandidateLiveQuestion,
  getLiveSessionStatus,
  submitCandidateLiveAnswer,
} from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import type {
  LiveCandidateQuestion,
  LiveSessionResponse,
} from '@/types/live-session'

const difficultyColor = (difficulty?: string) => {
  if (difficulty === 'EASY') return '#34D399'
  if (difficulty === 'MEDIUM') return '#F59E0B'
  if (difficulty === 'HARD') return '#F472B6'
  return '#60A5FA'
}

export default function CandidateLiveSessionPage() {
  const params = useParams()
  const router = useRouter()
  const sessionCode = String(params.sessionCode || '').toUpperCase()

  const [status, setStatus] = useState<LiveSessionResponse | null>(null)
  const [question, setQuestion] = useState<LiveCandidateQuestion | null>(null)
  const [answerText, setAnswerText] = useState('')
  const [submittedNumbers, setSubmittedNumbers] = useState<number[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [error, setError] = useState('')

  const refreshStatus = useCallback(async () => {
    try {
      const res = await getLiveSessionStatus(sessionCode)
      setStatus(res.data)

      const current = res.data.currentRevealedQuestion
      if (current > 0) {
        const qRes = await getCandidateLiveQuestion(sessionCode, current)
        setQuestion(qRes.data)
      } else {
        setQuestion(null)
      }
    } catch (err) {
      const parsed = parseApiError(err, 'Could not load live session details.')
      setError(parsed.message)
    }
  }, [sessionCode])

  useEffect(() => {
    let mounted = true
    const boot = async () => {
      await refreshStatus()
      if (mounted) setLoading(false)
    }

    void boot()
    const timer = setInterval(() => {
      void refreshStatus()
    }, 3000)

    return () => {
      mounted = false
      clearInterval(timer)
    }
  }, [refreshStatus])

  const submitAnswer = async () => {
    if (!question) return
    setSubmitting(true)
    setError('')
    setSuccessMessage('')
    try {
      const res = await submitCandidateLiveAnswer(sessionCode, question.questionNumber, answerText)
      setSubmittedNumbers((prev) => (prev.includes(question.questionNumber) ? prev : [...prev, question.questionNumber]))
      setSuccessMessage(`Answer submitted for question ${res.data.questionNumber}. Waiting for recruiter to reveal next question.`)
      setAnswerText('')
      await refreshStatus()
    } catch (err) {
      const parsed = parseApiError(err, 'Could not submit your answer.')
      setError(parsed.message)
    } finally {
      setSubmitting(false)
    }
  }

  const isCurrentQuestionSubmitted = question ? submittedNumbers.includes(question.questionNumber) : false

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', background: '#000', color: '#fff', display: 'grid', placeItems: 'center' }}>
        <div style={{ width: 36, height: 36, border: '2px solid rgba(212,255,0,0.2)', borderTopColor: '#D4FF00', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
        <style>{'@keyframes spin{to{transform:rotate(360deg)}}'}</style>
      </div>
    )
  }

  return (
    <div style={{ minHeight: '100vh', background: '#000', color: '#fff', padding: '20px', fontFamily: 'Outfit, sans-serif' }}>
      <style>{"@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap');"}</style>
      <div style={{ maxWidth: 900, margin: '0 auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center', marginBottom: 14, flexWrap: 'wrap' }}>
          <button onClick={() => router.push('/')} style={{ background: 'transparent', border: '1px solid rgba(255,255,255,0.16)', borderRadius: 10, color: 'rgba(255,255,255,0.75)', padding: '8px 12px', cursor: 'pointer' }}>
            Exit
          </button>
          <div style={{ fontFamily: 'JetBrains Mono, monospace', color: '#D4FF00', fontSize: 12 }}>
            LIVE INTERVIEW {sessionCode}
          </div>
        </div>

        {error && (
          <div style={{ marginBottom: 12, borderRadius: 12, border: '1px solid rgba(248,113,113,0.35)', background: 'rgba(248,113,113,0.1)', color: '#FCA5A5', padding: '10px 12px', fontSize: 13 }}>
            {error}
          </div>
        )}

        {successMessage && (
          <div style={{ marginBottom: 12, borderRadius: 12, border: '1px solid rgba(52,211,153,0.35)', background: 'rgba(52,211,153,0.1)', color: '#86EFAC', padding: '10px 12px', fontSize: 13 }}>
            {successMessage}
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(220px,1fr))', gap: 10, marginBottom: 12 }}>
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: 12 }}>
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>STATUS</div>
            <div style={{ marginTop: 6, fontSize: 20, fontWeight: 800, color: '#D4FF00' }}>{status?.status || 'UNKNOWN'}</div>
          </div>
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: 12 }}>
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>QUESTION</div>
            <div style={{ marginTop: 6, fontSize: 20, fontWeight: 800 }}>{status?.currentRevealedQuestion ?? 0}/{status?.totalQuestions ?? 0}</div>
          </div>
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: 12 }}>
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>REPOSITORY</div>
            <div style={{ marginTop: 6, fontSize: 18, fontWeight: 700 }}>{status?.repoName || '-'}</div>
          </div>
        </div>

        {status?.status === 'COMPLETED' && (
          <div style={{ border: '1px solid rgba(52,211,153,0.35)', background: 'rgba(52,211,153,0.1)', borderRadius: 14, padding: 14, marginBottom: 12 }}>
            <div style={{ fontSize: 18, fontWeight: 800, color: '#34D399', marginBottom: 4 }}>Session complete</div>
            <div style={{ color: 'rgba(255,255,255,0.75)' }}>Live score: <b>{status.liveScore ?? '—'}</b></div>
          </div>
        )}

        {status?.status === 'EXPIRED' && (
          <div style={{ border: '1px solid rgba(248,113,113,0.35)', background: 'rgba(248,113,113,0.1)', borderRadius: 14, padding: 14, marginBottom: 12 }}>
            <div style={{ fontSize: 18, fontWeight: 800, color: '#F87171', marginBottom: 4 }}>Session expired</div>
            <div style={{ color: 'rgba(255,255,255,0.75)' }}>This live session has expired. Ask the recruiter to create a new one.</div>
          </div>
        )}

        {!question && status?.status !== 'COMPLETED' && status?.status !== 'EXPIRED' && (
          <div style={{ border: '1px solid rgba(96,165,250,0.35)', background: 'rgba(96,165,250,0.1)', borderRadius: 14, padding: 14 }}>
            <div style={{ fontSize: 16, fontWeight: 700, color: '#93C5FD' }}>Waiting for recruiter</div>
            <div style={{ marginTop: 4, color: 'rgba(255,255,255,0.7)' }}>Your interviewer will reveal questions one-by-one.</div>
          </div>
        )}

        {question && status?.status !== 'COMPLETED' && status?.status !== 'EXPIRED' && (
          <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} style={{ border: '1px solid rgba(255,255,255,0.09)', background: '#0A0A0A', borderRadius: 14, padding: 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 10 }}>
              <div style={{ fontFamily: 'JetBrains Mono, monospace', color: '#D4FF00', fontSize: 12 }}>
                Q{question.questionNumber} · {question.fileReference}
              </div>
              <span style={{ fontSize: 11, borderRadius: 999, padding: '3px 8px', color: difficultyColor(question.difficulty), border: `1px solid ${difficultyColor(question.difficulty)}66`, background: `${difficultyColor(question.difficulty)}1A` }}>
                {question.difficulty}
              </span>
            </div>

            <div style={{ marginBottom: 12, lineHeight: 1.6, color: 'rgba(255,255,255,0.9)' }}>{question.questionText}</div>

            <textarea
              value={answerText}
              onChange={(e) => setAnswerText(e.target.value)}
              disabled={isCurrentQuestionSubmitted || submitting}
              placeholder="Type your explanation here. Include concrete reasoning and details from your implementation."
              style={{ width: '100%', minHeight: 180, resize: 'vertical', borderRadius: 12, border: '1px solid rgba(255,255,255,0.14)', background: 'rgba(255,255,255,0.03)', color: '#fff', padding: 12, boxSizing: 'border-box', fontFamily: 'Outfit, sans-serif', fontSize: 14, outline: 'none' }}
            />

            <div style={{ marginTop: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12 }}>
                Minimum 20 characters required.
              </div>
              <button
                onClick={submitAnswer}
                disabled={submitting || isCurrentQuestionSubmitted || answerText.trim().length < 20}
                style={{ border: '1px solid rgba(212,255,0,0.35)', background: 'rgba(212,255,0,0.1)', color: '#D4FF00', borderRadius: 10, padding: '8px 14px', cursor: 'pointer', fontWeight: 700, opacity: (submitting || isCurrentQuestionSubmitted || answerText.trim().length < 20) ? 0.6 : 1 }}
              >
                {isCurrentQuestionSubmitted ? 'Submitted' : submitting ? 'Submitting...' : 'Submit Answer'}
              </button>
            </div>
          </motion.div>
        )}
      </div>
    </div>
  )
}
