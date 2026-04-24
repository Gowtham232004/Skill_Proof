'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams, useRouter, useSearchParams } from 'next/navigation'
import { motion } from 'framer-motion'
import {
  getLiveSessionAnswers,
  getLiveSessionStatus,
  revealNextLiveQuestion,
} from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import type {
  LiveQuestionRevealResponse,
  LiveSessionAnswer,
  LiveSessionResponse,
} from '@/types/live-session'

const difficultyColor = (difficulty?: string) => {
  if (difficulty === 'EASY') return '#34D399'
  if (difficulty === 'MEDIUM') return '#F59E0B'
  if (difficulty === 'HARD') return '#F472B6'
  return '#60A5FA'
}

export default function RecruiterLiveSessionPage() {
  const params = useParams()
  const router = useRouter()
  const searchParams = useSearchParams()
  const sessionCode = String(params.sessionCode || '').toUpperCase()
  const resumedFromConflict = searchParams.get('resumed') === '1'

  const [status, setStatus] = useState<LiveSessionResponse | null>(null)
  const [answers, setAnswers] = useState<LiveSessionAnswer[]>([])
  const [latestReveal, setLatestReveal] = useState<LiveQuestionRevealResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [revealing, setRevealing] = useState(false)
  const [error, setError] = useState('')

  const refreshStatus = useCallback(async () => {
    try {
      const res = await getLiveSessionStatus(sessionCode)
      setStatus(res.data)
    } catch (err) {
      const parsed = parseApiError(err, 'Could not load live session status.')
      setError(parsed.message)
    }
  }, [sessionCode])

  const refreshAnswers = useCallback(async () => {
    try {
      const res = await getLiveSessionAnswers(sessionCode)
      setAnswers(res.data || [])
    } catch (err) {
      const parsed = parseApiError(err, 'Could not load submitted live answers.')
      setError(parsed.message)
    }
  }, [sessionCode])

  useEffect(() => {
    let mounted = true
    const boot = async () => {
      await Promise.all([refreshStatus(), refreshAnswers()])
      if (mounted) setLoading(false)
    }

    void boot()
    const timer = setInterval(() => {
      void refreshStatus()
      void refreshAnswers()
    }, 5000)

    return () => {
      mounted = false
      clearInterval(timer)
    }
  }, [refreshAnswers, refreshStatus])

  const revealNext = async () => {
    setRevealing(true)
    setError('')
    try {
      const res = await revealNextLiveQuestion(sessionCode)
      setLatestReveal(res.data)
      await Promise.all([refreshStatus(), refreshAnswers()])
    } catch (err) {
      const parsed = parseApiError(err, 'Could not reveal the next question.')
      setError(parsed.message)
    } finally {
      setRevealing(false)
    }
  }

  const candidateUrl = useMemo(() => {
    if (typeof window === 'undefined') return ''
    return `${window.location.origin}/verify/live/${sessionCode}`
  }, [sessionCode])

  const copyCandidateUrl = async () => {
    if (!candidateUrl) return
    try {
      await navigator.clipboard.writeText(candidateUrl)
    } catch {
      setError('Could not copy candidate link. Please copy it manually.')
    }
  }

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
      <div style={{ maxWidth: 1080, margin: '0 auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginBottom: 14 }}>
          <button onClick={() => router.push('/recruiter')} style={{ background: 'transparent', border: '1px solid rgba(255,255,255,0.16)', borderRadius: 10, color: 'rgba(255,255,255,0.75)', padding: '8px 12px', cursor: 'pointer' }}>
            Back to recruiter
          </button>
          <div style={{ fontFamily: 'JetBrains Mono, monospace', color: '#D4FF00', fontSize: 12 }}>
            LIVE SESSION {sessionCode}
          </div>
        </div>

        {error && (
          <div style={{ marginBottom: 12, borderRadius: 12, border: '1px solid rgba(248,113,113,0.35)', background: 'rgba(248,113,113,0.1)', color: '#FCA5A5', padding: '10px 12px', fontSize: 13 }}>
            {error}
          </div>
        )}

        {resumedFromConflict && (
          <div style={{ marginBottom: 12, borderRadius: 12, border: '1px solid rgba(96,165,250,0.35)', background: 'rgba(96,165,250,0.12)', color: '#BFDBFE', padding: '10px 12px', fontSize: 13 }}>
            Resumed existing active live session for this candidate.
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(220px,1fr))', gap: 10, marginBottom: 12 }}>
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: 12 }}>
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>STATUS</div>
            <div style={{ marginTop: 6, fontSize: 20, fontWeight: 800, color: '#D4FF00' }}>{status?.status || 'UNKNOWN'}</div>
          </div>
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: 12 }}>
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>PROGRESS</div>
            <div style={{ marginTop: 6, fontSize: 20, fontWeight: 800 }}>{status?.currentRevealedQuestion ?? 0}/{status?.totalQuestions ?? 0}</div>
          </div>
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: 12 }}>
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>CANDIDATE</div>
            <div style={{ marginTop: 6, fontSize: 18, fontWeight: 700 }}>{status?.candidateUsername || '-'}</div>
          </div>
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 12, padding: 12 }}>
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>LIVE SCORE</div>
            <div style={{ marginTop: 6, fontSize: 20, fontWeight: 800 }}>{status?.liveScore ?? '—'}</div>
          </div>
        </div>

        <div style={{ background: '#0A0A0A', border: '1px solid rgba(96,165,250,0.3)', borderRadius: 14, padding: 12, marginBottom: 12 }}>
          <div style={{ fontSize: 11, color: 'rgba(147,197,253,0.9)', marginBottom: 8, fontFamily: 'JetBrains Mono, monospace' }}>CANDIDATE LINK</div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <code style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: 'rgba(191,219,254,0.95)', wordBreak: 'break-all' }}>{candidateUrl}</code>
            <button onClick={copyCandidateUrl} style={{ border: '1px solid rgba(96,165,250,0.45)', background: 'rgba(96,165,250,0.12)', color: '#93C5FD', borderRadius: 8, padding: '6px 10px', cursor: 'pointer', fontSize: 12 }}>
              Copy
            </button>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(280px,1fr) minmax(280px,1fr)', gap: 12 }}>
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 14, padding: 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, flexWrap: 'wrap', gap: 8 }}>
              <div style={{ fontSize: 12, fontFamily: 'JetBrains Mono, monospace', color: '#D4FF00' }}>QUESTION CONTROL</div>
              <button
                onClick={revealNext}
                disabled={revealing || status?.status === 'COMPLETED' || status?.status === 'EXPIRED'}
                style={{ border: '1px solid rgba(212,255,0,0.35)', background: 'rgba(212,255,0,0.1)', color: '#D4FF00', borderRadius: 10, padding: '8px 12px', cursor: 'pointer', opacity: revealing ? 0.7 : 1, fontWeight: 700 }}
              >
                {revealing ? 'Revealing...' : 'Reveal Next Question'}
              </button>
            </div>

            {latestReveal ? (
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <div style={{ marginBottom: 8, fontSize: 12, color: 'rgba(255,255,255,0.5)', fontFamily: 'JetBrains Mono, monospace' }}>
                  Q{latestReveal.questionNumber}/{latestReveal.totalQuestions} · {latestReveal.fileReference}
                </div>
                <div style={{ marginBottom: 8, display: 'inline-block', fontSize: 11, borderRadius: 999, padding: '3px 8px', color: difficultyColor(latestReveal.difficulty), border: `1px solid ${difficultyColor(latestReveal.difficulty)}66`, background: `${difficultyColor(latestReveal.difficulty)}1A` }}>
                  {latestReveal.difficulty}
                </div>
                <div style={{ marginBottom: 10, lineHeight: 1.55 }}>{latestReveal.questionText}</div>
                <div style={{ border: '1px solid rgba(96,165,250,0.28)', borderRadius: 10, overflow: 'hidden' }}>
                  <div style={{ padding: '6px 10px', borderBottom: '1px solid rgba(96,165,250,0.2)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, color: '#93C5FD' }}>
                    CODE CONTEXT (RECRUITER ONLY)
                  </div>
                  <pre style={{ margin: 0, padding: '10px', maxHeight: 250, overflow: 'auto', fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: 'rgba(226,232,240,0.92)', background: 'rgba(2,6,23,0.55)' }}>
                    {latestReveal.codeContext || 'No context provided'}
                  </pre>
                </div>
              </motion.div>
            ) : (
              <div style={{ color: 'rgba(255,255,255,0.55)', fontSize: 13 }}>
                Reveal the first question to start the live interview.
              </div>
            )}
          </div>

          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 14, padding: 14 }}>
            <div style={{ marginBottom: 10, fontSize: 12, fontFamily: 'JetBrains Mono, monospace', color: '#D4FF00' }}>
              CANDIDATE ANSWERS
            </div>
            {answers.length === 0 ? (
              <div style={{ color: 'rgba(255,255,255,0.55)', fontSize: 13 }}>No answers submitted yet.</div>
            ) : (
              <div style={{ display: 'grid', gap: 8, maxHeight: 560, overflowY: 'auto' }}>
                {answers
                  .slice()
                  .sort((a, b) => a.questionNumber - b.questionNumber)
                  .map((answer) => (
                    <div key={answer.questionNumber} style={{ border: '1px solid rgba(255,255,255,0.08)', borderRadius: 10, background: 'rgba(255,255,255,0.02)', padding: 10 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, marginBottom: 6, alignItems: 'center' }}>
                        <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: '#93C5FD' }}>
                          Q{answer.questionNumber}
                        </div>
                        <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 11, color: '#34D399' }}>
                          score {answer.compositeScore}
                        </div>
                      </div>
                      <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.78)', marginBottom: 6, lineHeight: 1.5 }}>
                        {answer.answerText}
                      </div>
                      <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.5)', lineHeight: 1.5 }}>
                        A:{answer.accuracyScore}/10 · D:{answer.depthScore}/10 · S:{answer.specificityScore}/10
                      </div>
                    </div>
                  ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
