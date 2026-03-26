'use client'
export const dynamic = 'force-dynamic'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { motion } from 'framer-motion'
import { getRecruiterCandidateDetail, getRecruiterReferenceAnswer } from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import ErrorBanner from '@/app/components/ErrorBanner'

interface QuestionEvidence {
  questionNumber: number
  difficulty: string
  questionType?: 'CODE_GROUNDED' | 'CONCEPTUAL' | 'PATTERN' | 'EDGE_CASE'
  fileReference: string
  questionText: string
  questionCodeSnippet?: string
  skipped?: boolean
  answerLength?: number
  maskedAnswerExcerpt?: string
  fullAnswerText?: string
  accuracyScore?: number
  depthScore?: number
  specificityScore?: number
  compositeScore?: number
  aiFeedback?: string
  keyPoints?: string[]
}

interface CandidateDetail {
  valid: boolean
  verificationToken: string
  githubUsername: string
  displayName: string
  repoOwner: string
  repoName: string
  overallScore: number
  technicalScore?: number
  integrityAdjustedScore?: number
  integrityPenaltyTotal?: number
  integrityPenaltyBreakdown?: Record<string, number>
  scoreByQuestionType?: Record<string, number>
  weightedScoringEnabled?: boolean
  codeWeightPercent?: number
  conceptualWeightPercent?: number
  confidenceTier?: 'High' | 'Medium' | 'Low'
  tabSwitches?: number
  pasteCount?: number
  avgAnswerSeconds?: number
  followUpRequiredCount?: number
  followUpAnsweredCount?: number
  followUpResults?: {
    questionNumber: number
    followUpQuestion: string
    skipped: boolean
    answerLength: number
    answerExcerpt: string
  }[]
  answeredCount?: number
  totalQuestions?: number
  skippedCount?: number
  evaluationComplete?: boolean
  confidenceExplanation?: string
  answerRevealAvailable?: boolean
  questionResults?: QuestionEvidence[]
}

const diffColor: Record<string, string> = {
  EASY: '#34D399',
  MEDIUM: '#F59E0B',
  HARD: '#F472B6',
}

const typeLabel: Record<NonNullable<QuestionEvidence['questionType']>, string> = {
  CODE_GROUNDED: 'CODE',
  CONCEPTUAL: 'CONCEPT',
  PATTERN: 'PATTERN',
  EDGE_CASE: 'EDGE',
}

const REFERENCE_CACHE_TTL_MS = 10 * 60 * 1000

const cacheKeyFor = (badgeToken: string, questionNumber: number) =>
  `sp_ref_answer:${badgeToken}:${questionNumber}`

const truncateCodeContext = (snippet?: string, maxLines = 15) => {
  if (!snippet) {
    return ''
  }
  const lines = snippet.split(/\r?\n/)
  return lines.slice(0, maxLines).join('\n')
}

export default function RecruiterCandidateDetailPage() {
  const params = useParams()
  const router = useRouter()
  const token = params.token as string

  const [detail, setDetail] = useState<CandidateDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [referenceAnswers, setReferenceAnswers] = useState<Record<number, { referenceAnswer: string; reviewCheckpoints: string[] }>>({})
  const [referenceLoading, setReferenceLoading] = useState<Record<number, boolean>>({})
  const referenceCacheRef = useRef<Record<number, { referenceAnswer: string; reviewCheckpoints: string[] }>>({})
  const inFlightRef = useRef<Map<number, Promise<void>>>(new Map())

  const readLocalReferenceCache = useCallback((questionNumber: number) => {
    if (typeof window === 'undefined') {
      return null
    }
    try {
      const raw = window.localStorage.getItem(cacheKeyFor(token, questionNumber))
      if (!raw) {
        return null
      }
      const parsed = JSON.parse(raw) as {
        referenceAnswer: string
        reviewCheckpoints: string[]
        cachedAt: number
      }
      if (!parsed.cachedAt || Date.now() - parsed.cachedAt > REFERENCE_CACHE_TTL_MS) {
        return null
      }
      return {
        referenceAnswer: parsed.referenceAnswer || '',
        reviewCheckpoints: Array.isArray(parsed.reviewCheckpoints) ? parsed.reviewCheckpoints : [],
      }
    } catch {
      return null
    }
  }, [token])

  const writeLocalReferenceCache = useCallback((
    questionNumber: number,
    payload: { referenceAnswer: string; reviewCheckpoints: string[] }
  ) => {
    if (typeof window === 'undefined') {
      return
    }
    try {
      window.localStorage.setItem(
        cacheKeyFor(token, questionNumber),
        JSON.stringify({ ...payload, cachedAt: Date.now() })
      )
    } catch {
      // no-op for storage quota issues
    }
  }, [token])

  const requestReferenceAnswer = useCallback(async (questionNumber: number, forceRefresh = false) => {
    if (referenceCacheRef.current[questionNumber] && !forceRefresh) {
      return
    }

    const localCached = !forceRefresh ? readLocalReferenceCache(questionNumber) : null
    if (localCached) {
      referenceCacheRef.current[questionNumber] = localCached
      setReferenceAnswers(prev => ({ ...prev, [questionNumber]: localCached }))
      return
    }

    const inFlight = inFlightRef.current.get(questionNumber)
    if (inFlight) {
      await inFlight
      return
    }

    setReferenceLoading(prev => ({ ...prev, [questionNumber]: true }))
    const task = (async () => {
      try {
        const res = await getRecruiterReferenceAnswer(token, questionNumber, forceRefresh)
        const payload = {
          referenceAnswer: String(res.data?.referenceAnswer ?? '').trim(),
          reviewCheckpoints: Array.isArray(res.data?.reviewCheckpoints)
            ? res.data.reviewCheckpoints.map((item: unknown) => String(item)).filter(Boolean)
            : [],
        }
        referenceCacheRef.current[questionNumber] = payload
        writeLocalReferenceCache(questionNumber, payload)
        setReferenceAnswers(prev => ({ ...prev, [questionNumber]: payload }))
      } catch (err) {
        const parsed = parseApiError(err, 'Unable to load AI reference answer for this question.')
        setError(parsed.message)
      } finally {
        setReferenceLoading(prev => ({ ...prev, [questionNumber]: false }))
        inFlightRef.current.delete(questionNumber)
      }
    })()

    inFlightRef.current.set(questionNumber, task)
    await task
  }, [token, readLocalReferenceCache, writeLocalReferenceCache])

  useEffect(() => {
    getRecruiterCandidateDetail(token)
      .then((res) => {
        setDetail(res.data)
        setLoading(false)
      })
      .catch((err) => {
        const parsed = parseApiError(err, 'Could not load candidate review details.')
        setError(parsed.message)
        setLoading(false)
      })
  }, [token])

  useEffect(() => {
    const questionNumbers = (detail?.questionResults || []).map(q => q.questionNumber)
    if (questionNumbers.length === 0) {
      return
    }

    let cancelled = false
    const preload = async () => {
      const queue = [...questionNumbers]
      const worker = async () => {
        while (!cancelled && queue.length > 0) {
          const currentQuestion = queue.shift()
          if (typeof currentQuestion === 'number') {
            await requestReferenceAnswer(currentQuestion)
          }
        }
      }

      await Promise.all([worker(), worker()])
    }

    void preload()
    return () => {
      cancelled = true
    }
  }, [detail?.questionResults, requestReferenceAnswer])

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', background: '#000', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ width: 34, height: 34, border: '2px solid rgba(212,255,0,0.2)', borderTopColor: '#D4FF00', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
        <style>{'@keyframes spin{to{transform:rotate(360deg)}}'}</style>
      </div>
    )
  }

  if (!detail || !detail.valid) {
    return (
      <div style={{ minHeight: '100vh', background: '#000', color: '#fff', padding: 24, fontFamily: 'Outfit, sans-serif' }}>
        <button onClick={() => router.push('/recruiter')} style={{ marginBottom: 16, background: 'transparent', border: '1px solid rgba(255,255,255,0.15)', color: 'rgba(255,255,255,0.65)', borderRadius: 8, padding: '8px 12px', cursor: 'pointer' }}>
          Back to recruiter dashboard
        </button>
        <ErrorBanner message={error || 'Candidate detail not found.'} code='RECRUITER_DETAIL_NOT_FOUND' />
      </div>
    )
  }

  const confidenceColor = detail.confidenceTier === 'High'
    ? '#34D399'
    : detail.confidenceTier === 'Medium'
      ? '#F59E0B'
      : '#EF4444'

  const technicalScore = detail.technicalScore ?? detail.overallScore
  const adjustedScore = detail.integrityAdjustedScore ?? detail.overallScore
  const codeScore = detail.scoreByQuestionType?.CODE_GROUNDED ?? 0
  const conceptScore = detail.scoreByQuestionType?.CONCEPTUAL ?? 0
  const conceptGap = codeScore - conceptScore
  const copyEvents = detail.integrityPenaltyBreakdown?.copyEvents ?? 0
  const coachingPatternDetected = (detail.integrityPenaltyBreakdown?.coachingPatternDetected ?? 0) > 0
  const integrityRiskFlag = copyEvents > 0 || (detail.integrityPenaltyBreakdown?.copyPenalty ?? 0) > 0 || coachingPatternDetected

  const handleLoadReferenceAnswer = async (questionNumber: number) => {
    await requestReferenceAnswer(questionNumber)
  }

  return (
    <div style={{ minHeight: '100vh', background: '#000', color: '#fff', fontFamily: 'Outfit, sans-serif', padding: '24px 24px 64px' }}>
      <style>{"@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap');"}</style>

      <div style={{ maxWidth: 1000, margin: '0 auto' }}>
        <button onClick={() => router.push('/recruiter')} style={{ marginBottom: 18, background: 'transparent', border: '1px solid rgba(255,255,255,0.15)', color: 'rgba(255,255,255,0.65)', borderRadius: 8, padding: '8px 12px', cursor: 'pointer' }}>
          Back to recruiter dashboard
        </button>

        <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 18, marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
            <div>
              <div style={{ fontSize: 22, fontWeight: 800 }}>{detail.displayName || detail.githubUsername}</div>
              <div style={{ marginTop: 4, fontSize: 12, color: 'rgba(255,255,255,0.4)', fontFamily: 'JetBrains Mono, monospace' }}>
                @{detail.githubUsername} · {detail.repoOwner}/{detail.repoName}
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: 38, fontWeight: 900, color: '#D4FF00', fontFamily: 'JetBrains Mono, monospace' }}>{adjustedScore}</div>
              <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)' }}>integrity-adjusted score</div>
              <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)', marginTop: 4, fontFamily: 'JetBrains Mono, monospace' }}>
                technical {technicalScore} · penalty -{detail.integrityPenaltyTotal ?? 0}
              </div>
            </div>
          </div>

          <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <span style={{ padding: '4px 8px', borderRadius: 999, border: `1px solid ${confidenceColor}66`, background: `${confidenceColor}1A`, color: confidenceColor, fontSize: 11, fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>
              Confidence: {detail.confidenceTier || 'Unknown'}
            </span>
            <span style={{ padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(96,165,250,0.4)', background: 'rgba(96,165,250,0.1)', color: '#60A5FA', fontSize: 11, fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>
              Completed: {detail.answeredCount ?? 0}/{detail.totalQuestions ?? 0}
            </span>
            <span style={{ padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(245,158,11,0.4)', background: 'rgba(245,158,11,0.1)', color: '#F59E0B', fontSize: 11, fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>
              Skipped: {detail.skippedCount ?? 0}
            </span>
            <span style={{ padding: '4px 8px', borderRadius: 999, border: `1px solid ${(detail.evaluationComplete ? '#34D399' : '#EF4444')}66`, background: `${(detail.evaluationComplete ? '#34D399' : '#EF4444')}1A`, color: detail.evaluationComplete ? '#34D399' : '#EF4444', fontSize: 11, fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>
              Evaluation: {detail.evaluationComplete ? 'Complete' : 'Incomplete'}
            </span>
            {typeof detail.followUpRequiredCount === 'number' && detail.followUpRequiredCount > 0 && (
              <span style={{ padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(245,158,11,0.5)', background: 'rgba(245,158,11,0.12)', color: '#F59E0B', fontSize: 11, fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>
                Follow-up: {detail.followUpAnsweredCount ?? 0}/{detail.followUpRequiredCount}
              </span>
            )}
          </div>

          {detail.confidenceExplanation && (
            <p style={{ marginTop: 12, marginBottom: 0, color: 'rgba(255,255,255,0.58)', fontSize: 13, lineHeight: 1.5 }}>
              {detail.confidenceExplanation}
            </p>
          )}
        </div>

        <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 14, marginBottom: 16 }}>
          <div style={{ fontSize: 11, color: 'rgba(245,158,11,0.85)', fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.08em', marginBottom: 8 }}>INTEGRITY ADJUSTMENT</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 8 }}>
            <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>Technical score: <b>{technicalScore}</b></div>
            <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>Penalty total: <b>{detail.integrityPenaltyTotal ?? 0}</b></div>
            <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>Adjusted score: <b>{adjustedScore}</b></div>
          </div>
          {detail.integrityPenaltyBreakdown && (
            <div style={{ marginTop: 10, fontSize: 12, color: 'rgba(255,255,255,0.55)', fontFamily: 'JetBrains Mono, monospace', lineHeight: 1.8 }}>
              <div>pastePenalty: {detail.integrityPenaltyBreakdown.pastePenalty ?? 0}</div>
              <div>copyPenalty: {detail.integrityPenaltyBreakdown.copyPenalty ?? 0}</div>
              <div>speedPenalty: {detail.integrityPenaltyBreakdown.speedPenalty ?? 0}</div>
              <div>tabSwitchPenalty: {detail.integrityPenaltyBreakdown.tabSwitchPenalty ?? 0}</div>
              <div>coachingPatternPenalty: {detail.integrityPenaltyBreakdown.coachingPatternPenalty ?? 0}</div>
            </div>
          )}
          <div style={{ marginTop: 10, fontSize: 12, color: 'rgba(255,255,255,0.55)', fontFamily: 'JetBrains Mono, monospace' }}>
            Scoring mode: {detail.weightedScoringEnabled ? 'weighted' : 'equal'} {detail.weightedScoringEnabled ? `(${detail.codeWeightPercent ?? 60}% code · ${detail.conceptualWeightPercent ?? 40}% concept)` : '(per-question average)'}
          </div>
        </div>

        <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 14, marginBottom: 16 }}>
          <div style={{ fontSize: 11, color: 'rgba(96,165,250,0.85)', fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.08em', marginBottom: 8 }}>INTEGRITY SIGNALS</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 8 }}>
            <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>Tab switches: <b>{detail.tabSwitches ?? 0}</b></div>
            <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>Paste count: <b>{detail.pasteCount ?? 0}</b></div>
            <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>Copy events: <b>{copyEvents}</b></div>
            <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>Avg answer time: <b>{detail.avgAnswerSeconds ?? 0}s</b></div>
          </div>
        </div>

        {integrityRiskFlag && (
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(248,113,113,0.3)', borderRadius: 16, padding: 14, marginBottom: 16 }}>
            <div style={{ fontSize: 11, color: 'rgba(248,113,113,0.9)', fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.08em', marginBottom: 8 }}>INTEGRITY ALERT</div>
            <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.7)', lineHeight: 1.6 }}>
              {copyEvents > 0 && `Question/context copy activity detected (${copyEvents}). `}
              {coachingPatternDetected && 'High-accuracy with low-depth pattern detected across answers. '}
              Require live explanation for final validation.
            </div>
          </div>
        )}

        {!!detail.scoreByQuestionType && (
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 14, marginBottom: 16 }}>
            <div style={{ fontSize: 11, color: 'rgba(96,165,250,0.85)', fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.08em', marginBottom: 8 }}>CODE VS CONCEPT</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 8 }}>
              <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>Code-grounded: <b>{codeScore}</b></div>
              <div style={{ padding: 10, borderRadius: 10, background: 'rgba(255,255,255,0.03)' }}>Conceptual: <b>{conceptScore}</b></div>
            </div>
            {conceptGap >= 15 && (
              <div style={{ marginTop: 10, fontSize: 12, color: 'rgba(245,158,11,0.9)' }}>
                Mismatch signal: high code familiarity but weaker conceptual reasoning. Recommend follow-up why/trade-off questions.
              </div>
            )}
          </div>
        )}

        {Array.isArray(detail.followUpResults) && detail.followUpResults.length > 0 && (
          <div style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 14, marginBottom: 16 }}>
            <div style={{ fontSize: 11, color: 'rgba(245,158,11,0.9)', fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.08em', marginBottom: 8 }}>FOLLOW-UP AUDIT</div>
            <div style={{ display: 'grid', gap: 10 }}>
              {detail.followUpResults.map((item) => (
                <div key={item.questionNumber} style={{ padding: '10px 12px', borderRadius: 10, background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center', marginBottom: 6, flexWrap: 'wrap' }}>
                    <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.75)', fontWeight: 700 }}>Q{item.questionNumber} follow-up</div>
                    <span style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', borderRadius: 999, padding: '3px 8px', border: `1px solid ${item.skipped ? 'rgba(245,158,11,0.45)' : 'rgba(52,211,153,0.45)'}`, background: item.skipped ? 'rgba(245,158,11,0.12)' : 'rgba(52,211,153,0.12)', color: item.skipped ? '#F59E0B' : '#34D399' }}>
                      {item.skipped ? 'Skipped' : 'Answered'}
                    </span>
                  </div>
                  <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.6)', lineHeight: 1.55, marginBottom: 6 }}>
                    {item.followUpQuestion}
                  </div>
                  <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace' }}>
                    answer length: {item.answerLength ?? 0}
                  </div>
                  {!item.skipped && item.answerExcerpt && (
                    <div style={{ marginTop: 6, fontSize: 12, color: 'rgba(255,255,255,0.55)', lineHeight: 1.5 }}>
                      <b style={{ color: '#F59E0B' }}>Answer excerpt:</b> {item.answerExcerpt}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        <div style={{ marginBottom: 10, fontSize: 11, color: 'rgba(212,255,0,0.88)', fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.08em' }}>
          EVIDENCE PANEL (MANUAL REVIEW)
        </div>

        <div style={{ display: 'grid', gap: 12 }}>
          {(detail.questionResults || []).map((q) => (
            <motion.div key={q.questionNumber} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
              <details style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 14, overflow: 'hidden' }}>
                <summary style={{ listStyle: 'none', cursor: 'pointer', padding: 14, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                  <div style={{ fontWeight: 700, color: 'rgba(255,255,255,0.9)' }}>
                    Q{q.questionNumber} • {q.difficulty} • {q.fileReference || 'no file'}
                  </div>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    {q.questionType && (
                      <span style={{ fontSize: 11, color: '#D4FF00', border: '1px solid rgba(212,255,0,0.3)', background: 'rgba(212,255,0,0.1)', padding: '3px 8px', borderRadius: 999 }}>
                        {typeLabel[q.questionType]}
                      </span>
                    )}
                    <span style={{ fontSize: 11, color: diffColor[q.difficulty] || '#D4FF00', border: `1px solid ${(diffColor[q.difficulty] || '#D4FF00')}55`, background: `${(diffColor[q.difficulty] || '#D4FF00')}12`, padding: '3px 8px', borderRadius: 999 }}>
                      {q.difficulty}
                    </span>
                  </div>
                </summary>

                <div style={{ padding: '0 14px 14px' }}>
                  <div style={{ marginBottom: 10, fontSize: 13, color: 'rgba(255,255,255,0.82)', lineHeight: 1.5 }}>
                    <b>Question:</b> {q.questionText}
                  </div>

                  <div style={{ marginBottom: 10, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    <span style={{ padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(52,211,153,0.4)', background: 'rgba(52,211,153,0.12)', color: '#34D399', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}>
                      Accuracy: {q.accuracyScore ?? 0}/10
                    </span>
                    <span style={{ padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(96,165,250,0.4)', background: 'rgba(96,165,250,0.12)', color: '#60A5FA', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}>
                      Depth: {q.depthScore ?? 0}/10
                    </span>
                    <span style={{ padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(245,158,11,0.4)', background: 'rgba(245,158,11,0.12)', color: '#F59E0B', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}>
                      Specificity: {q.specificityScore ?? 0}/10
                    </span>
                  </div>

                  {q.questionCodeSnippet && (
                    <div style={{ marginBottom: 10, border: '1px solid rgba(96,165,250,0.24)', borderRadius: 10, overflow: 'hidden' }}>
                      <div style={{ padding: '6px 10px', borderBottom: '1px solid rgba(96,165,250,0.2)', fontSize: 10, color: 'rgba(147,197,253,0.88)', letterSpacing: '0.08em', fontFamily: 'JetBrains Mono, monospace' }}>
                        RELEVANT CODE SNIPPET (FIRST 15 LINES)
                      </div>
                      <pre style={{ margin: 0, padding: '10px 12px', maxHeight: 220, overflow: 'auto', background: 'rgba(2,6,23,0.55)', color: 'rgba(226,232,240,0.9)', fontSize: 12, lineHeight: 1.45, fontFamily: 'JetBrains Mono, monospace' }}>
                        {truncateCodeContext(q.questionCodeSnippet, 15)}
                      </pre>
                    </div>
                  )}

                  <div style={{ marginBottom: 10, padding: '10px 12px', borderRadius: 10, border: '1px solid rgba(244,114,182,0.25)', background: 'rgba(244,114,182,0.08)', fontSize: 12, color: 'rgba(255,255,255,0.78)', lineHeight: 1.55 }}>
                    <b style={{ color: '#F472B6' }}>Developer answer:</b>{' '}
                    {q.fullAnswerText && q.fullAnswerText.trim().length > 0
                      ? q.fullAnswerText
                      : q.maskedAnswerExcerpt || (q.skipped ? 'Question skipped.' : 'Not available.')}
                  </div>

                  <div style={{ padding: '10px 12px', borderRadius: 10, border: '1px solid rgba(96,165,250,0.25)', background: 'rgba(96,165,250,0.1)', fontSize: 12, color: 'rgba(191,219,254,0.92)', lineHeight: 1.55 }}>
                    <b>AI scoring rationale:</b> {q.aiFeedback || 'No AI feedback available.'}
                  </div>

                  <div style={{ marginTop: 10 }}>
                    <button
                      onClick={() => handleLoadReferenceAnswer(q.questionNumber)}
                      disabled={!!referenceLoading[q.questionNumber]}
                      style={{ padding: '6px 10px', borderRadius: 8, border: '1px solid rgba(212,255,0,0.3)', background: 'rgba(212,255,0,0.08)', color: '#D4FF00', fontSize: 12, fontFamily: 'JetBrains Mono, monospace', cursor: referenceLoading[q.questionNumber] ? 'not-allowed' : 'pointer' }}
                    >
                      {referenceLoading[q.questionNumber]
                        ? 'Generating reference...'
                        : referenceAnswers[q.questionNumber]
                          ? 'Reference answer loaded'
                          : 'Load AI reference answer'}
                    </button>
                  </div>

                  {referenceAnswers[q.questionNumber] && (
                    <div style={{ marginTop: 10, padding: '10px 12px', borderRadius: 10, border: '1px solid rgba(16,185,129,0.3)', background: 'rgba(16,185,129,0.1)' }}>
                      <div style={{ fontSize: 11, color: 'rgba(110,231,183,0.9)', fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.08em', marginBottom: 8 }}>
                        AI REFERENCE ANSWER (RECRUITER AID)
                      </div>
                      <div style={{ fontSize: 12, color: 'rgba(220,252,231,0.9)', lineHeight: 1.55 }}>
                        {referenceAnswers[q.questionNumber].referenceAnswer || 'Reference answer unavailable.'}
                      </div>
                      {referenceAnswers[q.questionNumber].reviewCheckpoints.length > 0 && (
                        <ul style={{ margin: '8px 0 0', paddingLeft: 18, color: 'rgba(220,252,231,0.86)', fontSize: 12, lineHeight: 1.5 }}>
                          {referenceAnswers[q.questionNumber].reviewCheckpoints.map((point, idx) => (
                            <li key={idx}>{point}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  )}
                </div>
              </details>
            </motion.div>
          ))}
        </div>
      </div>
    </div>
  )
}
