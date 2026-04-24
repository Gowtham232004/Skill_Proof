'use client'

import { useEffect, useMemo, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'

import { getQuickChallengeResult } from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import type { QuickChallengeResponse } from '@/types/quick-challenge'

const monospace = 'JetBrains Mono, monospace'

function scoreColor(score: number): string {
  if (score >= 70) {
    return '#4ADE80'
  }
  if (score >= 40) {
    return '#FACC15'
  }
  return '#F87171'
}

export default function RecruiterQuickChallengeResultPage() {
  const params = useParams()
  const router = useRouter()
  const token = String(params.token || '')

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [result, setResult] = useState<QuickChallengeResponse | null>(null)

  useEffect(() => {
    let cancelled = false

    const loadResult = async () => {
      setLoading(true)
      setError('')
      try {
        const response = await getQuickChallengeResult(token)
        if (cancelled) {
          return
        }
        setResult(response.data)
      } catch (err) {
        if (cancelled) {
          return
        }
        const parsed = parseApiError(err, 'Unable to load quick challenge result.')
        setError(parsed.message)
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    if (token) {
      void loadResult()
    } else {
      setLoading(false)
      setError('Missing quick challenge token.')
    }

    return () => {
      cancelled = true
    }
  }, [token])

  const overall = useMemo(() => result?.overallScore ?? 0, [result])

  return (
    <div style={{ minHeight: '100vh', background: '#000', color: '#fff', padding: '24px 24px 64px', fontFamily: 'Outfit, sans-serif' }}>
      <style>{"@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600;700&display=swap');"}</style>

      <div style={{ maxWidth: 1040, margin: '0 auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
          <button
            onClick={() => router.back()}
            style={{ background: 'transparent', border: '1px solid rgba(255,255,255,0.2)', color: 'rgba(255,255,255,0.7)', borderRadius: 8, padding: '8px 12px', cursor: 'pointer', fontFamily: monospace, fontSize: 12 }}
          >
            Back
          </button>
          <div style={{ fontFamily: monospace, fontSize: 11, letterSpacing: '0.08em', color: 'rgba(212,255,0,0.9)' }}>
            QUICK CHALLENGE RESULT
          </div>
        </div>

        {loading && (
          <div style={{ border: '1px solid rgba(255,255,255,0.1)', borderRadius: 16, background: '#0A0A0A', padding: 24, textAlign: 'center', color: 'rgba(255,255,255,0.6)', fontFamily: monospace }}>
            Loading recruiter result view...
          </div>
        )}

        {!loading && error && (
          <div style={{ border: '1px solid rgba(248,113,113,0.35)', borderRadius: 16, background: 'rgba(127,29,29,0.18)', padding: 16 }}>
            <div style={{ color: '#F87171', fontFamily: monospace, fontSize: 12, marginBottom: 6 }}>QUICK_CHALLENGE_RESULT_ERROR</div>
            <div style={{ color: 'rgba(255,255,255,0.8)', fontSize: 14 }}>{error}</div>
          </div>
        )}

        {!loading && !error && result && (
          <div style={{ display: 'grid', gap: 14 }}>
            <div style={{ border: '1px solid rgba(255,255,255,0.1)', borderRadius: 16, background: '#0A0A0A', padding: 16 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 10 }}>
                <div style={{ padding: 12, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>
                  <div style={{ fontFamily: monospace, fontSize: 10, color: 'rgba(255,255,255,0.45)', letterSpacing: '0.08em', marginBottom: 6 }}>CANDIDATE</div>
                  <div style={{ fontWeight: 700 }}>{result.candidateUsername || 'Unknown'}</div>
                </div>
                <div style={{ padding: 12, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>
                  <div style={{ fontFamily: monospace, fontSize: 10, color: 'rgba(255,255,255,0.45)', letterSpacing: '0.08em', marginBottom: 6 }}>STATUS</div>
                  <div style={{ fontWeight: 700 }}>{result.status}</div>
                </div>
                <div style={{ padding: 12, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>
                  <div style={{ fontFamily: monospace, fontSize: 10, color: 'rgba(255,255,255,0.45)', letterSpacing: '0.08em', marginBottom: 6 }}>OVERALL SCORE</div>
                  <div style={{ fontWeight: 800, color: scoreColor(overall), fontFamily: monospace, fontSize: 24 }}>{overall}<span style={{ color: 'rgba(255,255,255,0.35)', fontSize: 14 }}>/100</span></div>
                </div>
                <div style={{ padding: 12, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>
                  <div style={{ fontFamily: monospace, fontSize: 10, color: 'rgba(255,255,255,0.45)', letterSpacing: '0.08em', marginBottom: 6 }}>INTEGRITY</div>
                  <div style={{ fontWeight: 700 }}>Tab switches: {result.tabSwitchCount ?? 0}</div>
                  <div style={{ marginTop: 4, color: 'rgba(255,255,255,0.65)', fontFamily: monospace, fontSize: 12 }}>
                    Time: {Math.floor((result.timeTakenSeconds ?? 0) / 60)}m {(result.timeTakenSeconds ?? 0) % 60}s
                  </div>
                </div>
              </div>
            </div>

            <div style={{ border: '1px solid rgba(96,165,250,0.25)', borderRadius: 16, background: 'rgba(2,6,23,0.5)', overflow: 'hidden' }}>
              <div style={{ padding: '8px 12px', borderBottom: '1px solid rgba(96,165,250,0.2)', fontFamily: monospace, fontSize: 11, color: 'rgba(147,197,253,0.9)' }}>
                QUESTION CONTEXT · {result.selectedFilePath || 'Code snippet'}
              </div>
              <div style={{ padding: 12 }}>
                <div style={{ marginBottom: 10, fontSize: 14, lineHeight: 1.6 }}>
                  {result.questionText || 'Question text unavailable.'}
                </div>
                <pre style={{ margin: 0, maxHeight: 280, overflow: 'auto', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, padding: 12, background: '#050505', color: 'rgba(226,232,240,0.9)', fontFamily: monospace, fontSize: 12, lineHeight: 1.5 }}>
                  {result.codeSnippet || 'Code snippet unavailable.'}
                </pre>
              </div>
            </div>

            <div style={{ border: '1px solid rgba(244,114,182,0.24)', borderRadius: 16, background: 'rgba(131,24,67,0.18)', padding: 14 }}>
              <div style={{ fontFamily: monospace, fontSize: 11, color: 'rgba(251,113,133,0.95)', letterSpacing: '0.08em', marginBottom: 8 }}>CANDIDATE ANSWER</div>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontFamily: monospace, fontSize: 12, lineHeight: 1.6, color: 'rgba(255,255,255,0.92)' }}>
                {result.candidateAnswer || 'No answer recorded.'}
              </pre>
            </div>

            <div style={{ border: '1px solid rgba(52,211,153,0.3)', borderRadius: 16, background: 'rgba(6,78,59,0.2)', padding: 14 }}>
              <div style={{ fontFamily: monospace, fontSize: 11, color: 'rgba(110,231,183,0.95)', letterSpacing: '0.08em', marginBottom: 8 }}>AI EVALUATION</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 8, marginBottom: 10 }}>
                <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.04)' }}>Accuracy: <b>{result.accuracyScore ?? 0}/10</b></div>
                <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.04)' }}>Depth: <b>{result.depthScore ?? 0}/10</b></div>
                <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.04)' }}>Specificity: <b>{result.specificityScore ?? 0}/10</b></div>
              </div>
              <div style={{ color: 'rgba(220,252,231,0.92)', lineHeight: 1.6, fontSize: 14 }}>
                {result.aiFeedback || 'No AI feedback available.'}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
