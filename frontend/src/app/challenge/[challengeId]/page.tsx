'use client'

import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import { getChallenge, submitChallenge } from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import type { ChallengeResponse, ChallengeSubmissionResponse } from '@/types/challenge'
import ErrorBanner from '@/app/components/ErrorBanner'
import SuccessBanner from '@/app/components/SuccessBanner'
import { useAuth } from '@/hooks/useAuth'
import NotificationBell from '@/components/NotificationBell'

export default function ChallengePage() {
  const params = useParams<{ challengeId: string }>()
  const challengeId = Number(params?.challengeId)

  const [challenge, setChallenge] = useState<ChallengeResponse | null>(null)
  const [code, setCode] = useState('')
  const [submission, setSubmission] = useState<ChallengeSubmissionResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [submitStatus, setSubmitStatus] = useState('')
  const { user } = useAuth()

  useEffect(() => {
    const load = async () => {
      setError('')
      try {
        const res = await getChallenge(challengeId)
        setChallenge(res.data)
        setCode(res.data?.starterCode ?? '')
      } catch (err) {
        const parsed = parseApiError(err, 'Could not load challenge.')
        setError(parsed.message)
      } finally {
        setLoading(false)
      }
    }

    if (Number.isFinite(challengeId)) {
      load()
    } else {
      setError('Invalid challenge id.')
      setLoading(false)
    }
  }, [challengeId])

  const isExpired = useMemo(() => {
    if (!challenge?.expiresAt) return false
    return new Date(challenge.expiresAt).getTime() < Date.now()
  }, [challenge?.expiresAt])

  const isOwner = useMemo(() => {
    if (!challenge || !user?.githubUsername) return false
    return challenge.recruiterUsername?.toLowerCase() === user.githubUsername.toLowerCase()
  }, [challenge, user?.githubUsername])

  const candidateShareUrl = useMemo(() => {
    if (!challenge) return ''
    if (typeof window === 'undefined') return `/challenge/${challenge.id}`
    return `${window.location.origin}/challenge/${challenge.id}`
  }, [challenge])

  const onSubmit = async () => {
    if (!challenge) return

    if (!user) {
      setError('You must be logged in to submit this challenge.')
      return
    }

    if (!code.trim()) {
      setError('Please paste or write code before submitting.')
      return
    }

    setSubmitting(true)
    setError('')
    setSubmitStatus('Running hidden tests in sandbox. This can take up to 60-90 seconds...')

    try {
      const res = await submitChallenge(challenge.id, { code })
      setSubmission(res.data)
      setSubmitStatus('Submission completed.')
    } catch (err) {
      const parsed = parseApiError(err, 'Could not submit challenge.')
      setError(parsed.message)
      setSubmitStatus('')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div style={shellStyle}>
        <div style={{ textAlign: 'center', color: 'rgba(255,255,255,0.6)' }}>Loading challenge...</div>
      </div>
    )
  }

  return (
    <div style={shellStyle}>
      <style dangerouslySetInnerHTML={{ __html: `
        @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap');
      ` }} />

      <div style={{ maxWidth: 1060, margin: '0 auto' }}>
        {error && <ErrorBanner message={error} compact />}
        {submission && <SuccessBanner message={`Submitted with score ${submission.score}/100 (${submission.status})`} compact />}

        {challenge && (
          <>
            <div style={{ marginBottom: 20 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                <div>
                  <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: '#7ED4FF', letterSpacing: '0.12em' }}>
                    CODING CHALLENGE
                  </div>
                  <h1 style={{ margin: '8px 0 6px', fontSize: 34, fontWeight: 800 }}>{challenge.title}</h1>
                  <p style={{ margin: 0, color: 'rgba(255,255,255,0.72)', lineHeight: 1.6 }}>{challenge.description}</p>
                </div>
                <NotificationBell />
              </div>
              {isOwner && (
                <div style={{ marginTop: 10 }}>
                  <Link
                    href={`/challenge/${challenge.id}/submissions`}
                    style={{
                      display: 'inline-block',
                      color: '#9AD7FF',
                      fontFamily: 'JetBrains Mono, monospace',
                      fontSize: 12,
                      textDecoration: 'none',
                      border: '1px solid rgba(154,215,255,0.45)',
                      borderRadius: 999,
                      padding: '6px 12px',
                    }}
                  >
                    Open recruiter submission review
                  </Link>
                </div>
              )}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 16 }}>
              <div style={panelStyle}>
                <div style={metaRowStyle}>
                  <span>Language: <b>{challenge.language}</b></span>
                  <span>Time limit: <b>{challenge.timeLimitSeconds}s</b></span>
                </div>

                {isOwner && (
                  <div style={{ marginBottom: 10, border: '1px solid rgba(125,211,252,0.35)', borderRadius: 10, background: 'rgba(8,47,73,0.35)', padding: 10, fontSize: 12, color: '#BAE6FD', lineHeight: 1.5 }}>
                    Recruiter self-test mode: you can submit here to validate challenge quality before sharing with candidates.
                  </div>
                )}

                <div style={{ marginBottom: 10, border: '1px solid rgba(148,163,184,0.25)', borderRadius: 10, background: 'rgba(15,23,42,0.45)', padding: 10, fontSize: 12, color: 'rgba(226,232,240,0.9)', lineHeight: 1.5 }}>
                  Candidate test link: <b style={{ fontFamily: 'JetBrains Mono, monospace' }}>{candidateShareUrl}</b>
                </div>

                {challenge.challengeMode === 'REPO_GROUNDED' && (
                  <div style={{ marginBottom: 10, border: '1px solid rgba(96,165,250,0.35)', borderRadius: 10, background: 'rgba(2,6,23,0.55)', padding: 10, fontSize: 12, color: 'rgba(191,219,254,0.92)', lineHeight: 1.55 }}>
                    <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 10, letterSpacing: '0.08em', color: '#93C5FD', marginBottom: 6 }}>
                      REPO-GROUNDED MODE
                    </div>
                    {challenge.sourceRepoName && <div>Source repo: <b>{challenge.sourceRepoName}</b></div>}
                    {challenge.sourceFilePath && <div>Source file: <b>{challenge.sourceFilePath}</b></div>}
                    {challenge.sourceSnippetHash && <div>Snippet hash: <b style={{ fontFamily: 'JetBrains Mono, monospace' }}>{challenge.sourceSnippetHash.slice(0, 16)}...</b></div>}
                    {challenge.generationReason && <div>Reason: <b>{challenge.generationReason}</b></div>}
                  </div>
                )}

                <textarea
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  spellCheck={false}
                  style={{
                    width: '100%',
                    minHeight: 430,
                    borderRadius: 12,
                    border: '1px solid rgba(255,255,255,0.14)',
                    background: '#08111D',
                    color: '#D8EEFF',
                    fontFamily: 'JetBrains Mono, monospace',
                    fontSize: 12,
                    lineHeight: 1.55,
                    padding: 14,
                    boxSizing: 'border-box',
                    resize: 'vertical',
                    outline: 'none',
                  }}
                />

                <button
                  onClick={onSubmit}
                  disabled={submitting || isExpired}
                  style={{
                    marginTop: 12,
                    width: '100%',
                    border: 'none',
                    background: 'linear-gradient(100deg, #B2E1FF 0%, #70EAC7 100%)',
                    color: '#071019',
                    borderRadius: 12,
                    fontSize: 14,
                    fontWeight: 800,
                    padding: '12px 16px',
                    cursor: submitting ? 'wait' : 'pointer',
                    opacity: submitting || isExpired ? 0.7 : 1,
                  }}
                >
                  {isExpired ? 'Challenge expired' : submitting ? 'Submitting...' : 'Submit solution'}
                </button>

                {submitStatus && (
                  <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(191,219,254,0.88)', fontFamily: 'JetBrains Mono, monospace' }}>
                    {submitStatus}
                  </div>
                )}

                {!submitting && !isExpired && !code.trim() && (
                  <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(253,230,138,0.95)', fontFamily: 'JetBrains Mono, monospace' }}>
                    Write some code first, then click Submit solution.
                  </div>
                )}
              </div>

              <div style={panelStyle}>
                <h3 style={{ marginTop: 0, marginBottom: 10 }}>Challenge Details</h3>
                <div style={metaBlockStyle}>
                  <div>Created by: <b>@{challenge.recruiterUsername}</b></div>
                  <div>Mode: <b>{challenge.challengeMode || 'MANUAL'}</b></div>
                  <div>Access: <b>{challenge.accessMode || 'OPEN'}</b></div>
                  {challenge.expiresAt && <div>Expires: <b>{new Date(challenge.expiresAt).toLocaleString()}</b></div>}
                  <div>
                    Test cases: <b>{challenge.visibleTestCases ?? challenge.testCases.length}</b>
                    {typeof challenge.totalTestCases === 'number' && (
                      <span style={{ color: 'rgba(255,255,255,0.6)' }}> / {challenge.totalTestCases} visible</span>
                    )}
                  </div>
                </div>

                <h4 style={{ marginBottom: 8 }}>Visible Test Inputs</h4>
                <div style={{ display: 'grid', gap: 8 }}>
                  {challenge.testCases.map((testCase, index) => (
                    <div key={index} style={{ border: '1px solid rgba(255,255,255,0.12)', borderRadius: 10, padding: 10, background: 'rgba(255,255,255,0.03)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                        <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.55)' }}>
                          {testCase.name || `Test #${testCase.caseNumber ?? index + 1}`}
                        </div>
                        <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: '#34D399' }}>VISIBLE</div>
                      </div>
                      <pre style={{ margin: '6px 0 0', whiteSpace: 'pre-wrap', color: 'rgba(255,255,255,0.88)', fontFamily: 'JetBrains Mono, monospace', fontSize: 11 }}>
                        {testCase.stdin || '(empty input)'}
                      </pre>
                    </div>
                  ))}
                </div>

                {submission && (
                  <div style={{ marginTop: 14 }}>
                    <h4 style={{ marginBottom: 8 }}>Latest Result</h4>
                    <div style={metaBlockStyle}>
                      <div>Status: <b>{submission.status}</b></div>
                      <div>Score: <b>{submission.score}/100</b></div>
                      <div>Feedback: <b>{submission.feedback || 'No feedback'}</b></div>
                    </div>

                    {submission.testCases && submission.testCases.length > 0 && (
                      <div style={{ marginTop: 10 }}>
                        <h4 style={{ marginBottom: 8 }}>Hidden Test Results</h4>
                        <div style={{ display: 'grid', gap: 8 }}>
                          {submission.testCases.map((testCase) => (
                            <div key={testCase.caseNumber} style={{ border: '1px solid rgba(255,255,255,0.12)', borderRadius: 10, padding: 10, background: 'rgba(255,255,255,0.03)' }}>
                              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, marginBottom: 6 }}>
                                <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.7)' }}>
                                  {testCase.name || `Test #${testCase.caseNumber}`}
                                </div>
                                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                                  <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: testCase.isVisible ? '#34D399' : '#FBBF24' }}>
                                    {testCase.isVisible ? 'VISIBLE' : 'HIDDEN'}
                                  </div>
                                  <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: testCase.status === 'PASS' ? '#34D399' : testCase.status === 'FAIL' ? '#F59E0B' : '#F87171' }}>
                                    {testCase.status}
                                  </div>
                                </div>
                              </div>

                              {testCase.expectedOutput && (
                                <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.55)', marginBottom: 4 }}>
                                  Expected: <b style={{ color: 'rgba(255,255,255,0.88)' }}>{testCase.expectedOutput}</b>
                                </div>
                              )}
                              {testCase.actualOutput && (
                                <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.55)', marginBottom: 4 }}>
                                  Actual: <b style={{ color: 'rgba(255,255,255,0.88)' }}>{testCase.actualOutput}</b>
                                </div>
                              )}
                              {testCase.errorMessage && (
                                <div style={{ fontSize: 11, color: '#FCA5A5' }}>
                                  Error: {testCase.errorMessage}
                                </div>
                              )}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {(submission.stdout || submission.stderr) && (
                      <div style={{ marginTop: 12 }}>
                        <h4 style={{ marginBottom: 8 }}>Compile / Runtime Diagnostics</h4>
                        {submission.stderr && (
                          <div style={{ marginBottom: 8 }}>
                            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.58)', marginBottom: 4 }}>stderr</div>
                            <pre style={{ margin: 0, background: 'rgba(248,113,113,0.1)', border: '1px solid rgba(248,113,113,0.4)', borderRadius: 8, padding: 8, whiteSpace: 'pre-wrap', color: '#FECACA', fontFamily: 'JetBrains Mono, monospace', fontSize: 11 }}>
                              {submission.stderr}
                            </pre>
                          </div>
                        )}
                        {submission.stdout && (
                          <div>
                            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.58)', marginBottom: 4 }}>stdout</div>
                            <pre style={{ margin: 0, background: 'rgba(59,130,246,0.1)', border: '1px solid rgba(59,130,246,0.4)', borderRadius: 8, padding: 8, whiteSpace: 'pre-wrap', color: '#BFDBFE', fontFamily: 'JetBrains Mono, monospace', fontSize: 11 }}>
                              {submission.stdout}
                            </pre>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

const shellStyle: CSSProperties = {
  minHeight: '100vh',
  background: 'radial-gradient(circle at 20% 20%, #1B2F4A 0%, #05070B 42%, #030407 100%)',
  color: '#fff',
  fontFamily: 'Outfit, sans-serif',
  padding: '32px 16px',
}

const panelStyle: CSSProperties = {
  border: '1px solid rgba(255,255,255,0.12)',
  borderRadius: 16,
  background: 'rgba(4,10,18,0.84)',
  padding: 14,
}

const metaRowStyle: CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  marginBottom: 10,
  fontFamily: 'JetBrains Mono, monospace',
  fontSize: 12,
  color: 'rgba(255,255,255,0.78)',
}

const metaBlockStyle: CSSProperties = {
  border: '1px solid rgba(255,255,255,0.12)',
  borderRadius: 10,
  padding: 10,
  display: 'grid',
  gap: 7,
  fontSize: 13,
  color: 'rgba(255,255,255,0.86)',
}
