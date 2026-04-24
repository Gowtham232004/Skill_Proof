'use client'

import { useEffect, useMemo, useState } from 'react'

import { api, compareRecruiterCandidates } from '@/lib/api'
import { parseApiError } from '@/lib/apiError'

interface RecruiterCandidateListItem {
  badgeToken: string
  githubUsername: string
  displayName?: string
  repoName: string
  overallScore: number
  technicalScore?: number
  integrityAdjustedScore?: number
  confidenceTier?: 'High' | 'Medium' | 'Low'
  recruiterDecisionStatus?: 'VERIFIED' | 'NEEDS_LIVE_INTERVIEW' | 'REJECT' | 'PENDING'
}

interface ComparisonCandidate {
  verificationToken: string
  candidateUsername: string
  displayName?: string
  repoName: string
  overallScore: number
  technicalScore?: number
  integrityAdjustedScore?: number
  executionAdjustedScore?: number
  executionPassRatePercent?: number
  backendScore?: number
  apiDesignScore?: number
  errorHandlingScore?: number
  codeQualityScore?: number
  documentationScore?: number
  confidenceTier?: string
  recruiterDecisionStatus?: string
  attemptCount?: number
}

const DIMENSIONS: Array<{ key: keyof ComparisonCandidate; label: string }> = [
  { key: 'technicalScore', label: 'Technical' },
  { key: 'integrityAdjustedScore', label: 'Integrity Adj' },
  { key: 'executionAdjustedScore', label: 'Execution Adj' },
  { key: 'executionPassRatePercent', label: 'Exec Pass %' },
  { key: 'backendScore', label: 'Backend' },
  { key: 'apiDesignScore', label: 'API Design' },
  { key: 'errorHandlingScore', label: 'Error Handling' },
  { key: 'codeQualityScore', label: 'Code Quality' },
  { key: 'documentationScore', label: 'Docs' },
]

export default function ComparePage() {
  const [tokens, setTokens] = useState(['', '', ''])
  const [badges, setBadges] = useState<ComparisonCandidate[]>([])
  const [candidatePool, setCandidatePool] = useState<RecruiterCandidateListItem[]>([])
  const [selectedPoolTokens, setSelectedPoolTokens] = useState<string[]>([])
  const [poolLoading, setPoolLoading] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const loadCandidatePool = async () => {
      setPoolLoading(true)
      try {
        const response = await api.get('/api/recruiter/candidates')
        const rows = Array.isArray(response.data) ? response.data : []
        const mapped = rows
          .map((row) => ({
            badgeToken: String(row.badgeToken || '').trim(),
            githubUsername: String(row.githubUsername || '').trim(),
            displayName: String(row.displayName || '').trim(),
            repoName: String(row.repoName || '').trim(),
            overallScore: Number(row.overallScore || 0),
            technicalScore: Number(row.technicalScore || 0),
            integrityAdjustedScore: Number(row.integrityAdjustedScore || 0),
            confidenceTier: row.confidenceTier as 'High' | 'Medium' | 'Low' | undefined,
            recruiterDecisionStatus: (row.recruiterDecisionStatus || 'PENDING') as 'VERIFIED' | 'NEEDS_LIVE_INTERVIEW' | 'REJECT' | 'PENDING',
          }))
          .filter((row) => row.badgeToken && row.githubUsername)
        setCandidatePool(mapped)
      } catch {
        setCandidatePool([])
      } finally {
        setPoolLoading(false)
      }
    }

    void loadCandidatePool()
  }, [])

  const togglePoolCandidate = (badgeToken: string) => {
    setSelectedPoolTokens((prev) => {
      if (prev.includes(badgeToken)) {
        return prev.filter((token) => token !== badgeToken)
      }
      if (prev.length >= 5) {
        return prev
      }
      return [...prev, badgeToken]
    })
  }

  const updateToken = (index: number, value: string) => {
    const next = [...tokens]
    next[index] = value
    setTokens(next)
  }

  const addToken = () => {
    if (tokens.length >= 5) {
      return
    }
    setTokens((prev) => [...prev, ''])
  }

  const removeToken = (index: number) => {
    setTokens((prev) => prev.filter((_, idx) => idx !== index))
  }

  const compare = async () => {
    const typedTokens = tokens
      .map((token) => token.trim())
      .filter((token) => token.length > 0)

    const validTokens = Array.from(new Set([...selectedPoolTokens, ...typedTokens]))

    if (validTokens.length < 2) {
      setError('Select or enter at least 2 badge tokens to compare')
      return
    }

    setError('')
    setLoading(true)

    try {
      const response = await compareRecruiterCandidates(validTokens)
      const data = response.data
      const rows = Array.isArray(data?.candidates) ? data.candidates : []
      const clean = rows
        .map((row) => row as ComparisonCandidate)
        .filter((row) => row.verificationToken && row.candidateUsername)

      if (clean.length < 2) {
        setError('Could not load enough candidate badges. Verify tokens and recruiter access.')
      }
      setBadges(clean)
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to compare candidates')
      setError(parsed.message)
    } finally {
      setLoading(false)
    }
  }

  const bestByDimension = useMemo(() => {
    const best: Record<string, number> = {}
    for (const dim of DIMENSIONS) {
      best[String(dim.key)] = Math.max(...badges.map((badge) => Number(badge[dim.key] || 0)), 0)
    }
    return best
  }, [badges])

  const scoreColor = (score: number) => {
    if (score >= 70) return '#4ADE80'
    if (score >= 40) return '#FACC15'
    return '#F87171'
  }

  const topCandidate = useMemo(() => {
    if (badges.length === 0) {
      return null
    }
    const scoreOf = (candidate: ComparisonCandidate) => Number(candidate.executionAdjustedScore || candidate.overallScore || 0)
    return badges.reduce((best, current) => (scoreOf(current) > scoreOf(best) ? current : best), badges[0])
  }, [badges])

  const decisionColor = (status?: string) => {
    const normalized = (status || 'PENDING').toUpperCase()
    if (normalized === 'VERIFIED') return '#34D399'
    if (normalized === 'REJECT') return '#F87171'
    if (normalized === 'NEEDS_LIVE_INTERVIEW') return '#FACC15'
    return 'rgba(255,255,255,0.7)'
  }

  return (
    <div style={{ minHeight: '100vh', background: '#000', color: '#fff', padding: 24, fontFamily: 'Outfit, sans-serif' }}>
      <style>{"@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600;700&display=swap');"}</style>

      <div style={{ maxWidth: 1180, margin: '0 auto' }}>
        <div style={{ marginBottom: 20 }}>
          <p style={{ color: '#D4FF00', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, letterSpacing: '0.08em', marginBottom: 4 }}>RECRUITER TOOL</p>
          <h1 style={{ margin: 0, fontSize: 32 }}>Candidate Comparison</h1>
          <p style={{ color: 'rgba(255,255,255,0.55)', fontFamily: 'JetBrains Mono, monospace', fontSize: 13, marginTop: 8 }}>
            Compare up to 5 verified candidates side-by-side across skill dimensions.
          </p>
        </div>

        {badges.length === 0 && (
          <div style={{ border: '1px solid rgba(255,255,255,0.1)', borderRadius: 14, padding: 16, marginBottom: 16 }}>
            <p style={{ color: 'rgba(96,165,250,0.88)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, letterSpacing: '0.08em', marginTop: 0, marginBottom: 10 }}>
              QUICK SELECT FROM YOUR CANDIDATES
            </p>
            {poolLoading ? (
              <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12, marginTop: 0, marginBottom: 12 }}>
                Loading recruiter candidates...
              </p>
            ) : candidatePool.length > 0 ? (
              <div style={{ display: 'grid', gap: 8, marginBottom: 14 }}>
                {candidatePool.slice(0, 10).map((candidate) => {
                  const selected = selectedPoolTokens.includes(candidate.badgeToken)
                  return (
                    <button
                      key={candidate.badgeToken}
                      onClick={() => togglePoolCandidate(candidate.badgeToken)}
                      style={{
                        textAlign: 'left',
                        padding: '9px 10px',
                        borderRadius: 8,
                        border: selected ? '1px solid rgba(212,255,0,0.5)' : '1px solid rgba(255,255,255,0.14)',
                        background: selected ? 'rgba(212,255,0,0.08)' : 'rgba(255,255,255,0.02)',
                        color: selected ? '#D4FF00' : 'rgba(255,255,255,0.78)',
                        fontFamily: 'JetBrains Mono, monospace',
                        fontSize: 12,
                        cursor: 'pointer',
                      }}
                    >
                      @{candidate.githubUsername} · {candidate.repoName} · overall {candidate.overallScore}/100 · tech {candidate.technicalScore ?? 0}/100 · adj {candidate.integrityAdjustedScore ?? 0}/100 · {(candidate.recruiterDecisionStatus || 'PENDING')}
                    </button>
                  )
                })}
              </div>
            ) : (
              <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12, marginTop: 0, marginBottom: 12 }}>
                Recruiter candidate list unavailable. You can still compare by entering badge tokens manually.
              </p>
            )}

            <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, letterSpacing: '0.08em', marginTop: 0, marginBottom: 10 }}>
              ENTER BADGE TOKENS
            </p>

            <div style={{ display: 'grid', gap: 10 }}>
              {tokens.map((token, index) => (
                <div key={index} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <span style={{ width: 16, color: 'rgba(255,255,255,0.35)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>{index + 1}</span>
                  <input
                    value={token}
                    onChange={(event) => updateToken(index, event.target.value)}
                    placeholder="sp_abc123..."
                    style={{ flex: 1, padding: '10px 12px', background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.16)', borderRadius: 8, color: '#fff', fontFamily: 'JetBrains Mono, monospace', fontSize: 13, outline: 'none' }}
                  />
                  {tokens.length > 2 && (
                    <button onClick={() => removeToken(index)} style={{ border: 'none', background: 'transparent', color: '#F87171', cursor: 'pointer', fontSize: 18 }}>
                      ×
                    </button>
                  )}
                </div>
              ))}
            </div>

            <div style={{ display: 'flex', gap: 10, marginTop: 12 }}>
              {tokens.length < 5 && (
                <button
                  onClick={addToken}
                  style={{ padding: '10px 12px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.18)', background: 'rgba(255,255,255,0.03)', color: 'rgba(255,255,255,0.75)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12, cursor: 'pointer' }}
                >
                  + Add Candidate
                </button>
              )}

              <button
                onClick={() => { void compare() }}
                disabled={loading}
                style={{ flex: 1, padding: '10px 12px', borderRadius: 8, border: 'none', background: '#D4FF00', color: '#000', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700, cursor: loading ? 'not-allowed' : 'pointer', opacity: loading ? 0.6 : 1 }}
              >
                {loading ? 'Loading...' : 'Compare'}
              </button>
            </div>

            {error && <p style={{ marginBottom: 0, marginTop: 10, color: '#F87171', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>{error}</p>}
          </div>
        )}

        {badges.length > 0 && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <p style={{ color: 'rgba(255,255,255,0.5)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>
                Comparing {badges.length} candidates
              </p>
              <button
                onClick={() => setBadges([])}
                style={{ padding: '8px 12px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.18)', background: 'transparent', color: 'rgba(255,255,255,0.7)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12, cursor: 'pointer' }}
              >
                New Comparison
              </button>
            </div>

            <div style={{ border: '1px solid rgba(255,255,255,0.1)', borderRadius: 12, overflowX: 'auto' }}>
              <div style={{ minWidth: 760 }}>
                <div style={{ display: 'grid', gridTemplateColumns: `220px repeat(${badges.length}, minmax(180px,1fr))`, borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
                  <div style={{ padding: 14 }} />
                  {badges.map((badge) => (
                    <div key={badge.verificationToken} style={{ padding: 14, textAlign: 'center', borderLeft: '1px solid rgba(255,255,255,0.08)' }}>
                      <div style={{ width: 40, height: 40, borderRadius: 999, margin: '0 auto 8px', background: 'rgba(212,255,0,0.15)', color: '#D4FF00', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        {(badge.candidateUsername || '?').charAt(0).toUpperCase()}
                      </div>
                      <div style={{ fontFamily: 'JetBrains Mono, monospace', fontWeight: 700, fontSize: 13 }}>@{badge.candidateUsername}</div>
                      <div style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, marginTop: 4 }}>{badge.repoName}</div>
                      <div style={{ marginTop: 8, color: scoreColor(badge.overallScore), fontFamily: 'JetBrains Mono, monospace', fontSize: 24, fontWeight: 800 }}>
                        {badge.overallScore}<span style={{ color: 'rgba(255,255,255,0.35)', fontSize: 13 }}>/100</span>
                      </div>
                      <div style={{ marginTop: 6, color: decisionColor(badge.recruiterDecisionStatus), fontFamily: 'JetBrains Mono, monospace', fontSize: 10, letterSpacing: '0.06em' }}>
                        {(badge.recruiterDecisionStatus || 'PENDING').replaceAll('_', ' ')}
                      </div>
                    </div>
                  ))}
                </div>

                {DIMENSIONS.map((dimension, index) => (
                  <div
                    key={String(dimension.key)}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: `220px repeat(${badges.length}, minmax(180px,1fr))`,
                      background: index % 2 === 0 ? 'rgba(255,255,255,0.03)' : '#000',
                      borderBottom: '1px solid rgba(255,255,255,0.08)',
                    }}
                  >
                    <div style={{ padding: 14, color: 'rgba(255,255,255,0.65)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>{dimension.label}</div>

                    {badges.map((badge) => {
                      const score = Number(badge[dimension.key] || 0)
                      const isBest = score > 0 && score === bestByDimension[String(dimension.key)]
                      return (
                        <div key={`${badge.verificationToken}-${String(dimension.key)}`} style={{ padding: 14, borderLeft: '1px solid rgba(255,255,255,0.08)', textAlign: 'center' }}>
                          <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 22, fontWeight: 800, color: isBest ? '#D4FF00' : scoreColor(score) }}>
                            {score}{isBest ? <span style={{ marginLeft: 4, fontSize: 11 }}>★</span> : null}
                          </div>
                          <div style={{ height: 4, marginTop: 6, background: 'rgba(255,255,255,0.1)', borderRadius: 999 }}>
                            <div style={{ width: `${Math.max(0, Math.min(100, score))}%`, height: 4, borderRadius: 999, background: scoreColor(score) }} />
                          </div>
                        </div>
                      )
                    })}
                  </div>
                ))}

                <div style={{ display: 'grid', gridTemplateColumns: `220px repeat(${badges.length}, minmax(180px,1fr))`, background: 'rgba(255,255,255,0.03)' }}>
                  <div style={{ padding: 14, color: 'rgba(255,255,255,0.65)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>Confidence</div>
                  {badges.map((badge) => (
                    <div key={`${badge.verificationToken}-confidence`} style={{ padding: 14, borderLeft: '1px solid rgba(255,255,255,0.08)', textAlign: 'center' }}>
                      <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 11, padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(255,255,255,0.2)', color: 'rgba(255,255,255,0.8)' }}>
                        {badge.confidenceTier || 'Unknown'}
                      </span>
                    </div>
                  ))}
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: `220px repeat(${badges.length}, minmax(180px,1fr))`, background: '#000' }}>
                  <div style={{ padding: 14, color: 'rgba(255,255,255,0.65)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>Decision</div>
                  {badges.map((badge) => (
                    <div key={`${badge.verificationToken}-decision`} style={{ padding: 14, borderLeft: '1px solid rgba(255,255,255,0.08)', textAlign: 'center' }}>
                      <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 11, padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(255,255,255,0.2)', color: decisionColor(badge.recruiterDecisionStatus) }}>
                        {(badge.recruiterDecisionStatus || 'PENDING').replaceAll('_', ' ')}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {topCandidate && (
              <div style={{ marginTop: 12, border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, padding: 12 }}>
                <p style={{ color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, letterSpacing: '0.08em', marginTop: 0, marginBottom: 6 }}>RECOMMENDATION</p>
                <p style={{ margin: 0, color: 'rgba(255,255,255,0.8)', fontSize: 14, lineHeight: 1.6 }}>
                  <span style={{ color: '#D4FF00', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>@{topCandidate.candidateUsername}</span>
                  {' '}scores highest on execution-adjusted signal ({topCandidate.executionAdjustedScore ?? topCandidate.overallScore}/100) on {topCandidate.repoName}.{' '}
                  {(topCandidate.confidenceTier || '').toUpperCase() === 'HIGH'
                    ? 'High confidence indicates stronger authenticity.'
                    : 'Consider sending a Quick Challenge for stronger verification.'}
                </p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
