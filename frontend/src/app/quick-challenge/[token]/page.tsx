'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'next/navigation'

import { openQuickChallenge, submitQuickChallenge } from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import type { QuickChallengeOpenResponse, QuickChallengeResponse } from '@/types/quick-challenge'
import NotificationBell from '@/components/NotificationBell'

const CHALLENGE_TIME = 600

export default function QuickChallengePage() {
  const params = useParams()
  const token = String(params.token || '')

  const [challenge, setChallenge] = useState<QuickChallengeOpenResponse | null>(null)
  const [answer, setAnswer] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [result, setResult] = useState<QuickChallengeResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [secondsLeft, setSecondsLeft] = useState(CHALLENGE_TIME)
  const [timerStarted, setTimerStarted] = useState(false)
  const [tabSwitches, setTabSwitches] = useState(0)
  const [expired, setExpired] = useState(false)

  const timerRef = useRef<NodeJS.Timeout | null>(null)
  const startTimeRef = useRef<number>(0)

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.hidden && timerStarted && !submitted) {
        setTabSwitches((prev) => prev + 1)
      }
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [timerStarted, submitted])

  const submitAnswer = useCallback(async (answerText: string, elapsedSeconds: number) => {
    if (submitted) {
      return
    }

    setLoading(true)
    if (timerRef.current) {
      clearInterval(timerRef.current)
    }

    try {
      const response = await submitQuickChallenge(token, {
        answer: answerText,
        tabSwitches,
        timeTaken: elapsedSeconds,
      })
      setResult(response.data)
      setSubmitted(true)
      setLoading(false)
    } catch (err) {
      const parsed = parseApiError(err, 'Submission failed')
      if (parsed.message.toLowerCase().includes('already submitted')) {
        try {
          const latest = await openQuickChallenge(token)
          if (latest.data.status === 'COMPLETED') {
            setResult(latest.data)
            setSubmitted(true)
            setLoading(false)
            return
          }
        } catch {
          // fall through to display the original message
        }
      }

      if (parsed.message.toLowerCase().includes('expired')) {
        setExpired(true)
      }
      setError(parsed.message)
      setLoading(false)
    }
  }, [submitted, tabSwitches, token])

  const handleAutoSubmit = useCallback(async () => {
    if (submitted || !timerStarted) {
      return
    }

    const elapsed = Math.floor((Date.now() - startTimeRef.current) / 1000)
    const finalAnswer = answer.trim().length > 0 ? answer : '[No answer - time expired]'
    await submitAnswer(finalAnswer, elapsed)
  }, [answer, submitted, submitAnswer, timerStarted])

  useEffect(() => {
    const load = async () => {
      try {
        const response = await openQuickChallenge(token)
        const data = response.data
        setChallenge(data)

        if (data.status === 'EXPIRED') {
          setExpired(true)
          return
        }

        if (data.status === 'COMPLETED') {
          setSubmitted(true)
          setResult(data)
          return
        }

        startTimeRef.current = Date.now()
        setTimerStarted(true)
        const remaining = Math.min(data.secondsRemaining || CHALLENGE_TIME, CHALLENGE_TIME)
        setSecondsLeft(remaining)

        timerRef.current = setInterval(() => {
          setSecondsLeft((prev) => {
            if (prev <= 1) {
              if (timerRef.current) {
                clearInterval(timerRef.current)
              }
              setTimerStarted(false)
              void handleAutoSubmit()
              return 0
            }
            return prev - 1
          })
        }, 1000)
      } catch (err) {
        const parsed = parseApiError(err, 'Failed to load challenge')
        setError(parsed.message)
      }
    }

    void load()
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current)
      }
    }
  }, [handleAutoSubmit, token])

  useEffect(() => {
    if (submitted || !token) {
      return
    }

    const poll = setInterval(() => {
      void (async () => {
        try {
          const response = await openQuickChallenge(token)
          const data = response.data
          setChallenge(data)

          if (data.status === 'COMPLETED') {
            setResult(data)
            setSubmitted(true)
            setLoading(false)
            if (timerRef.current) {
              clearInterval(timerRef.current)
            }
            return
          }

          if (data.status === 'EXPIRED') {
            setExpired(true)
            if (timerRef.current) {
              clearInterval(timerRef.current)
            }
            return
          }

          if (typeof data.secondsRemaining === 'number') {
            setSecondsLeft((prev) => Math.min(prev, data.secondsRemaining))
          }
        } catch {
          // keep local timer running even if poll fails once
        }
      })()
    }, 15000)

    return () => clearInterval(poll)
  }, [submitted, token])

  const handleManualSubmit = () => {
    if (expired || secondsLeft <= 0) {
      return
    }
    if (answer.trim().length < 20) {
      return
    }
    const elapsed = Math.floor((Date.now() - startTimeRef.current) / 1000)
    void submitAnswer(answer, elapsed)
  }

  const formatTime = (totalSeconds: number) => {
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}:${seconds.toString().padStart(2, '0')}`
  }

  const timerColor = () => {
    if (secondsLeft > 300) return '#4ADE80'
    if (secondsLeft > 120) return '#FACC15'
    return '#F87171'
  }

  if (!challenge && !error) {
    return (
      <div style={{ minHeight: '100vh', background: '#000', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ color: '#D4FF00', fontFamily: 'JetBrains Mono, monospace' }}>Loading challenge...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div style={{ minHeight: '100vh', background: '#000', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ textAlign: 'center' }}>
          <p style={{ color: '#F87171', fontFamily: 'JetBrains Mono, monospace', fontSize: 20, marginBottom: 6 }}>Error</p>
          <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 13 }}>{error}</p>
        </div>
      </div>
    )
  }

  if (challenge?.status === 'EXPIRED' || expired) {
    return (
      <div style={{ minHeight: '100vh', background: '#000', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ textAlign: 'center', maxWidth: 420 }}>
          <p style={{ color: '#F87171', fontFamily: 'JetBrains Mono, monospace', fontSize: 24, marginBottom: 10 }}>Challenge Expired</p>
          <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 13 }}>
            This challenge was valid for 24 hours and allows 10 minutes once opened.
          </p>
        </div>
      </div>
    )
  }

  if (submitted && result) {
    const overall = result.overallScore || 0
    const scoreColor = overall >= 70 ? '#4ADE80' : overall >= 40 ? '#FACC15' : '#F87171'

    return (
      <div style={{ minHeight: '100vh', background: '#000', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ maxWidth: 560, padding: 24, textAlign: 'center' }}>
          <p style={{ color: '#D4FF00', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, letterSpacing: '0.08em', marginBottom: 16 }}>SUBMITTED</p>
          <div style={{ fontFamily: 'JetBrains Mono, monospace', fontWeight: 800, fontSize: 72, color: scoreColor, lineHeight: 1 }}>
            {overall}<span style={{ color: 'rgba(255,255,255,0.35)', fontSize: 32 }}>/100</span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,minmax(0,1fr))', gap: 12, marginTop: 20, marginBottom: 20 }}>
            {[
              ['Accuracy', result.accuracyScore || 0],
              ['Depth', result.depthScore || 0],
              ['Specificity', result.specificityScore || 0],
            ].map(([label, score]) => (
              <div key={String(label)} style={{ border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, padding: 12 }}>
                <div style={{ color: '#fff', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700, fontSize: 18 }}>{score}/10</div>
                <div style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, marginTop: 4 }}>{label}</div>
              </div>
            ))}
          </div>

          {result.aiFeedback && (
            <p style={{ color: 'rgba(255,255,255,0.7)', fontSize: 14, lineHeight: 1.5, marginBottom: 10 }}>{result.aiFeedback}</p>
          )}

          <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>
            Completed in {Math.floor((result.timeTakenSeconds || 0) / 60)}m {(result.timeTakenSeconds || 0) % 60}s
            {' '}· {result.tabSwitchCount || tabSwitches} tab switches
          </p>
        </div>
      </div>
    )
  }

  return (
    <div style={{ minHeight: '100vh', background: '#000', color: '#fff', display: 'flex', flexDirection: 'column' }}>
      <style>{"@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600;700&display=swap');"}</style>

      <div style={{ borderBottom: '1px solid rgba(255,255,255,0.08)', padding: '12px 20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <span style={{ color: '#D4FF00', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>SkillProof</span>
          <span style={{ marginLeft: 8, color: 'rgba(255,255,255,0.5)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>Quick Code Challenge</span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <span style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>{challenge?.repoName}</span>
          <span style={{ color: timerColor(), fontFamily: 'JetBrains Mono, monospace', fontSize: 24, fontWeight: 800 }}>{formatTime(secondsLeft)}</span>
          <NotificationBell />
        </div>
      </div>

      <div style={{ height: 4, background: 'rgba(255,255,255,0.08)' }}>
        <div style={{ height: 4, background: timerColor(), width: `${(secondsLeft / CHALLENGE_TIME) * 100}%`, transition: 'width 1s linear' }} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', flex: 1, minHeight: 0 }}>
        <div style={{ borderRight: '1px solid rgba(255,255,255,0.08)', padding: 20, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
          <div style={{ marginBottom: 12 }}>
            <div style={{ color: '#D4FF00', fontFamily: 'JetBrains Mono, monospace', fontSize: 12, fontWeight: 700 }}>{challenge?.selectedFilePath || 'Code context'}</div>
            <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, marginTop: 4, marginBottom: 0 }}>
              From repository {challenge?.repoName}
            </p>
          </div>

          <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, marginBottom: 8, letterSpacing: '0.08em' }}>CODE SNIPPET</p>
          <pre style={{ margin: 0, border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, padding: 12, background: '#050505', color: 'rgba(255,255,255,0.85)', fontSize: 13, lineHeight: 1.5, fontFamily: 'JetBrains Mono, monospace', overflow: 'auto', flex: 1 }}>
            {challenge?.codeSnippet}
          </pre>
        </div>

        <div style={{ padding: 20, display: 'flex', flexDirection: 'column' }}>
          <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, marginBottom: 8, letterSpacing: '0.08em' }}>QUESTION</p>
          <div style={{ border: '1px solid rgba(255,255,255,0.14)', borderRadius: 10, padding: 12, marginBottom: 16 }}>
            <p style={{ margin: 0, fontSize: 14, lineHeight: 1.6 }}>{challenge?.questionText}</p>
          </div>

          <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, marginBottom: 8, letterSpacing: '0.08em' }}>YOUR ANSWER</p>
          <p style={{ color: 'rgba(255,255,255,0.38)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, marginTop: 0, marginBottom: 8 }}>
            Reference exact functions/variables from this snippet. Generic answers score lower.
          </p>

          <textarea
            value={answer}
            onChange={(event) => setAnswer(event.target.value)}
            placeholder="Explain this code in your own implementation terms. Why is it written this way, and what edge case matters?"
            style={{ flex: 1, minHeight: 180, border: '1px solid rgba(255,255,255,0.18)', borderRadius: 10, padding: 12, background: 'rgba(255,255,255,0.03)', color: '#fff', fontSize: 14, lineHeight: 1.5, resize: 'none', outline: 'none' }}
            autoFocus
          />

          <div style={{ marginTop: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            <div>
              <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 11, color: answer.trim().length < 20 ? 'rgba(255,255,255,0.4)' : '#4ADE80' }}>
                {answer.trim().length} chars{answer.trim().length < 20 ? ' (minimum 20)' : ' ✓'}
              </span>
              {secondsLeft < 60 && (
                <span style={{ marginLeft: 8, color: '#F87171', fontFamily: 'JetBrains Mono, monospace', fontSize: 11 }}>
                  Auto-submits in {secondsLeft}s
                </span>
              )}
            </div>

            <button
              onClick={handleManualSubmit}
              disabled={loading || expired || secondsLeft <= 0 || answer.trim().length < 20}
              style={{ padding: '10px 14px', borderRadius: 10, border: 'none', background: '#D4FF00', color: '#000', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700, cursor: loading ? 'not-allowed' : 'pointer', opacity: loading || answer.trim().length < 20 ? 0.5 : 1 }}
            >
              {loading ? 'Evaluating...' : 'Submit Answer'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
