'use client'

import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { useParams } from 'next/navigation'
import { getChallenge, getChallengeReferenceAnswer, getChallengeSubmissions } from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import type {
  ChallengeResponse,
  ChallengeSubmissionResponse,
  ChallengeSubmissionTestCaseResult,
} from '@/types/challenge'
import ErrorBanner from '@/app/components/ErrorBanner'

type SubmissionRow = {
  submission: ChallengeSubmissionResponse
  attemptNumber: number
  previousAttempt: ChallengeSubmissionResponse | null
}

export default function ChallengeSubmissionsPage() {
  const params = useParams<{ challengeId: string }>()
  const challengeId = Number(params?.challengeId)

  const [challenge, setChallenge] = useState<ChallengeResponse | null>(null)
  const [submissions, setSubmissions] = useState<ChallengeSubmissionResponse[]>([])
  const [referenceAnswer, setReferenceAnswer] = useState<string>('')
  const [referenceMeta, setReferenceMeta] = useState<{ sourceFilePath?: string; generationReason?: string }>({})
  const [selectedSubmissionId, setSelectedSubmissionId] = useState<number | null>(null)
  const [candidateFilter, setCandidateFilter] = useState('all')
  const [statusFilter, setStatusFilter] = useState<'all' | 'PASSED' | 'FAILED' | 'ERROR'>('all')
  const [searchText, setSearchText] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const load = async () => {
      setError('')
      setLoading(true)

      try {
        const [challengeRes, submissionsRes] = await Promise.all([
          getChallenge(challengeId),
          getChallengeSubmissions(challengeId),
        ])

        const fetchedSubmissions = submissionsRes.data ?? []
        setChallenge(challengeRes.data)
        setSubmissions(fetchedSubmissions)
        setSelectedSubmissionId(fetchedSubmissions[0]?.submissionId ?? null)

        try {
          const refRes = await getChallengeReferenceAnswer(challengeId)
          setReferenceAnswer(refRes.data?.referenceSolution ?? '')
          setReferenceMeta({
            sourceFilePath: refRes.data?.sourceFilePath,
            generationReason: refRes.data?.generationReason,
          })
        } catch {
          setReferenceAnswer('')
          setReferenceMeta({})
        }
      } catch (err) {
        const parsed = parseApiError(err, 'Could not load challenge submissions.')
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

  const submissionRows = useMemo<SubmissionRow[]>(() => {
    const byCandidate = new Map<string, ChallengeSubmissionResponse[]>()

    for (const submission of submissions) {
      const key = submission.candidateUsername || 'unknown'
      const existing = byCandidate.get(key)
      if (existing) {
        existing.push(submission)
      } else {
        byCandidate.set(key, [submission])
      }
    }

    const rows: SubmissionRow[] = []

    for (const candidateSubmissions of Array.from(byCandidate.values())) {
      const sorted = [...candidateSubmissions].sort(
        (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
      )

      sorted.forEach((submission, index) => {
        rows.push({
          submission,
          attemptNumber: index + 1,
          previousAttempt: index > 0 ? sorted[index - 1] : null,
        })
      })
    }

    return rows.sort(
      (a, b) => new Date(b.submission.createdAt).getTime() - new Date(a.submission.createdAt).getTime(),
    )
  }, [submissions])

  const candidateOptions = useMemo(() => {
    return Array.from(new Set(submissionRows.map((row) => row.submission.candidateUsername || 'unknown')))
      .sort((a, b) => a.localeCompare(b))
  }, [submissionRows])

  const filteredRows = useMemo(() => {
    const search = searchText.trim().toLowerCase()
    return submissionRows.filter((row) => {
      if (candidateFilter !== 'all' && row.submission.candidateUsername !== candidateFilter) {
        return false
      }

      if (statusFilter !== 'all' && row.submission.status !== statusFilter) {
        return false
      }

      if (!search) {
        return true
      }

      const byCandidate = (row.submission.candidateUsername || '').toLowerCase().includes(search)
      const byStatus = row.submission.status.toLowerCase().includes(search)
      const byScore = String(row.submission.score).includes(search)
      return byCandidate || byStatus || byScore
    })
  }, [submissionRows, candidateFilter, statusFilter, searchText])

  const selectedRow = useMemo(
    () => filteredRows.find((row) => row.submission.submissionId === selectedSubmissionId) ?? null,
    [filteredRows, selectedSubmissionId],
  )

  useEffect(() => {
    if (filteredRows.length === 0) {
      setSelectedSubmissionId(null)
      return
    }

    if (!filteredRows.some((row) => row.submission.submissionId === selectedSubmissionId)) {
      setSelectedSubmissionId(filteredRows[0].submission.submissionId)
    }
  }, [filteredRows, selectedSubmissionId])

  if (loading) {
    return (
      <div style={shellStyle}>
        <div style={{ textAlign: 'center', color: 'rgba(255,255,255,0.6)' }}>Loading submissions...</div>
      </div>
    )
  }

  return (
    <div style={shellStyle}>
      <style
        dangerouslySetInnerHTML={{
          __html: `
            @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap');
          `,
        }}
      />

      <div style={{ maxWidth: 1180, margin: '0 auto' }}>
        {error && <ErrorBanner message={error} compact />}

        <div style={{ marginBottom: 20 }}>
          <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: '#8FD4FF', letterSpacing: '0.12em' }}>
            RECRUITER REVIEW
          </div>
          <h1 style={{ margin: '8px 0 6px', fontSize: 34, fontWeight: 800 }}>Challenge Submissions</h1>
          <p style={{ margin: 0, color: 'rgba(255,255,255,0.72)' }}>
            {challenge ? `${challenge.title} • ${challenge.language}` : 'Submission history'}
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1.1fr 1fr', gap: 16 }}>
          <section style={panelStyle}>
            <h3 style={{ marginTop: 0, marginBottom: 12 }}>All Attempts</h3>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 10 }}>
              <select
                value={candidateFilter}
                onChange={(e) => setCandidateFilter(e.target.value)}
                style={filterInputStyle}
              >
                <option value="all">All candidates</option>
                {candidateOptions.map((candidate) => (
                  <option key={candidate} value={candidate}>
                    {candidate}
                  </option>
                ))}
              </select>

              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as 'all' | 'PASSED' | 'FAILED' | 'ERROR')}
                style={filterInputStyle}
              >
                <option value="all">All statuses</option>
                <option value="PASSED">PASSED</option>
                <option value="FAILED">FAILED</option>
                <option value="ERROR">ERROR</option>
              </select>
            </div>

            <input
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              placeholder="Search candidate/status/score"
              style={{ ...filterInputStyle, width: '100%', marginBottom: 12 }}
            />

            {filteredRows.length === 0 ? (
              <div style={{ color: 'rgba(255,255,255,0.62)' }}>No submissions yet.</div>
            ) : (
              <div style={{ display: 'grid', gap: 8 }}>
                {filteredRows.map((row) => {
                  const isSelected = row.submission.submissionId === selectedSubmissionId
                  return (
                    <button
                      key={row.submission.submissionId}
                      onClick={() => setSelectedSubmissionId(row.submission.submissionId)}
                      style={{
                        border: isSelected ? '1px solid #9AD7FF' : '1px solid rgba(255,255,255,0.14)',
                        borderRadius: 12,
                        padding: 10,
                        background: isSelected ? 'rgba(64,146,196,0.2)' : 'rgba(255,255,255,0.02)',
                        color: '#fff',
                        textAlign: 'left',
                        cursor: 'pointer',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, marginBottom: 4 }}>
                        <div style={{ fontWeight: 700 }}>{row.submission.candidateUsername}</div>
                        <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 11, color: 'rgba(255,255,255,0.75)' }}>
                          Attempt #{row.attemptNumber}
                        </div>
                      </div>

                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, fontSize: 12, color: 'rgba(255,255,255,0.76)' }}>
                        <span>
                          {row.submission.status} • {row.submission.score}/100
                        </span>
                        <span>{new Date(row.submission.createdAt).toLocaleString()}</span>
                      </div>
                    </button>
                  )
                })}
              </div>
            )}
          </section>

          <section style={panelStyle}>
            <h3 style={{ marginTop: 0, marginBottom: 12 }}>Attempt Insight</h3>

            {!selectedRow ? (
              <div style={{ color: 'rgba(255,255,255,0.62)' }}>Select an attempt to inspect details.</div>
            ) : (
              <>
                {referenceAnswer && (
                  <div style={{ marginBottom: 12 }}>
                    <h4 style={{ margin: '0 0 8px' }}>Recruiter Reference Answer</h4>
                    <div style={{ border: '1px solid rgba(59,130,246,0.35)', borderRadius: 10, background: 'rgba(30,58,138,0.18)', padding: 10 }}>
                      {referenceMeta.sourceFilePath && (
                        <div style={{ fontSize: 11, color: 'rgba(191,219,254,0.88)', marginBottom: 5 }}>
                          Source file: <b>{referenceMeta.sourceFilePath}</b>
                        </div>
                      )}
                      {referenceMeta.generationReason && (
                        <div style={{ fontSize: 11, color: 'rgba(191,219,254,0.78)', marginBottom: 7 }}>
                          Reason: {referenceMeta.generationReason}
                        </div>
                      )}
                      <pre style={{ margin: 0, background: 'rgba(15,23,42,0.65)', border: '1px solid rgba(148,163,184,0.3)', borderRadius: 8, padding: 8, whiteSpace: 'pre-wrap', color: '#DBEAFE', fontFamily: 'JetBrains Mono, monospace', fontSize: 11 }}>
                        {referenceAnswer}
                      </pre>
                    </div>
                  </div>
                )}

                <div style={summaryCardStyle}>
                  <div>Candidate: <b>{selectedRow.submission.candidateUsername}</b></div>
                  <div>Attempt: <b>#{selectedRow.attemptNumber}</b></div>
                  <div>Status: <b>{selectedRow.submission.status}</b></div>
                  <div>Score: <b>{selectedRow.submission.score}/100</b></div>
                  <div>Per-test outcomes: <b>{selectedRow.submission.testCases?.length ?? 0}</b></div>
                </div>

                <div style={{ marginTop: 12 }}>
                  <h4 style={{ margin: '0 0 8px' }}>Hidden Test Diff</h4>
                  <div style={{ display: 'grid', gap: 8 }}>
                    {buildDiffRows(selectedRow.submission.testCases, selectedRow.previousAttempt?.testCases ?? []).map((item) => (
                      <div key={item.caseKey} style={{ border: '1px solid rgba(255,255,255,0.12)', borderRadius: 10, padding: 10 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, marginBottom: 6 }}>
                          <div style={{ fontSize: 12, fontFamily: 'JetBrains Mono, monospace' }}>{item.label}</div>
                          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                            <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: item.current.isVisible ? '#34D399' : '#FBBF24' }}>
                              {item.current.isVisible ? 'VISIBLE' : 'HIDDEN'}
                            </div>
                            <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: statusColor(item.current.status) }}>
                              {item.current.status}
                              {item.changed ? ' • changed' : ''}
                            </div>
                          </div>
                        </div>

                        <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.75)', marginBottom: 4 }}>
                          Expected: <b style={{ color: '#fff' }}>{item.current.expectedOutput || '(empty)'}</b>
                        </div>
                        <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.75)', marginBottom: 4 }}>
                          Actual: <b style={{ color: '#fff' }}>{item.current.actualOutput || '(empty)'}</b>
                        </div>
                        {item.current.errorMessage && (
                          <div style={{ fontSize: 11, color: '#FCA5A5', marginBottom: 4 }}>
                            Error: {item.current.errorMessage}
                          </div>
                        )}

                        {item.previous && (
                          <div style={{ marginTop: 8, borderTop: '1px solid rgba(255,255,255,0.1)', paddingTop: 8 }}>
                            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.6)', marginBottom: 4 }}>Previous attempt</div>
                            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.65)' }}>
                              Status: <b style={{ color: statusColor(item.previous.status) }}>{item.previous.status}</b>
                            </div>
                            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.65)' }}>
                              Actual: <b style={{ color: '#fff' }}>{item.previous.actualOutput || '(empty)'}</b>
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>

                <div style={{ marginTop: 14 }}>
                  <h4 style={{ margin: '0 0 8px' }}>Compile / Runtime Diagnostics</h4>
                  {selectedRow.submission.stdout || selectedRow.submission.stderr ? (
                    <div style={{ display: 'grid', gap: 8, marginBottom: 14 }}>
                      {selectedRow.submission.stderr && (
                        <div>
                          <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.58)', marginBottom: 4 }}>stderr</div>
                          <pre style={{ margin: 0, background: 'rgba(248,113,113,0.1)', border: '1px solid rgba(248,113,113,0.4)', borderRadius: 8, padding: 8, whiteSpace: 'pre-wrap', color: '#FECACA', fontFamily: 'JetBrains Mono, monospace', fontSize: 11 }}>
                            {selectedRow.submission.stderr}
                          </pre>
                        </div>
                      )}
                      {selectedRow.submission.stdout && (
                        <div>
                          <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.58)', marginBottom: 4 }}>stdout</div>
                          <pre style={{ margin: 0, background: 'rgba(59,130,246,0.1)', border: '1px solid rgba(59,130,246,0.4)', borderRadius: 8, padding: 8, whiteSpace: 'pre-wrap', color: '#BFDBFE', fontFamily: 'JetBrains Mono, monospace', fontSize: 11 }}>
                            {selectedRow.submission.stdout}
                          </pre>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div style={{ color: 'rgba(255,255,255,0.62)', marginBottom: 14 }}>No compile/runtime diagnostics captured.</div>
                  )}

                  <h4 style={{ margin: '0 0 8px' }}>Code Diff (vs previous attempt)</h4>
                  {selectedRow.previousAttempt ? (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                      <div style={codePanelStyle}>
                        <div style={codePanelTitleStyle}>Previous (Attempt #{selectedRow.attemptNumber - 1})</div>
                        <pre style={codePreStyle}>{selectedRow.previousAttempt.submittedCode || ''}</pre>
                      </div>
                      <div style={codePanelStyle}>
                        <div style={codePanelTitleStyle}>Current (Attempt #{selectedRow.attemptNumber})</div>
                        <pre style={codePreStyle}>{renderMarkedCode(selectedRow.submission.submittedCode || '', selectedRow.previousAttempt.submittedCode || '')}</pre>
                      </div>
                    </div>
                  ) : (
                    <div style={{ color: 'rgba(255,255,255,0.62)' }}>No previous attempt for this candidate.</div>
                  )}
                </div>
              </>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}

function buildDiffRows(
  current: ChallengeSubmissionTestCaseResult[] = [],
  previous: ChallengeSubmissionTestCaseResult[] = [],
) {
  const previousMap = new Map<number, ChallengeSubmissionTestCaseResult>()
  previous.forEach((item) => previousMap.set(item.caseNumber, item))

  return current.map((item) => {
    const previousItem = previousMap.get(item.caseNumber)
    const changed =
      !previousItem ||
      previousItem.status !== item.status ||
      normalize(previousItem.actualOutput) !== normalize(item.actualOutput) ||
      normalize(previousItem.errorMessage) !== normalize(item.errorMessage)

    return {
      caseKey: `${item.caseNumber}-${item.name}`,
      label: item.name || `Test #${item.caseNumber}`,
      current: item,
      previous: previousItem,
      changed,
    }
  })
}

function normalize(value?: string | null) {
  return (value ?? '').trim()
}

function statusColor(status: string) {
  if (status === 'PASS') return '#34D399'
  if (status === 'FAIL') return '#F59E0B'
  if (status === 'ERROR') return '#F87171'
  return 'rgba(255,255,255,0.75)'
}

const shellStyle: CSSProperties = {
  minHeight: '100vh',
  background: 'radial-gradient(circle at 14% 18%, #1D3552 0%, #070A11 40%, #030407 100%)',
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

const summaryCardStyle: CSSProperties = {
  border: '1px solid rgba(255,255,255,0.12)',
  borderRadius: 10,
  padding: 10,
  display: 'grid',
  gap: 6,
  fontSize: 13,
  color: 'rgba(255,255,255,0.86)',
}

function renderMarkedCode(currentCode: string, previousCode: string) {
  const currentLines = splitLines(currentCode)
  const previousLines = splitLines(previousCode)

  return currentLines
    .map((line, index) => {
      const previousLine = previousLines[index] ?? ''
      if (line === previousLine) {
        return `  ${line}`
      }
      return `+ ${line}`
    })
    .join('\n')
}

function splitLines(code: string) {
  return code.replace(/\r\n/g, '\n').split('\n')
}

const filterInputStyle: CSSProperties = {
  padding: '8px 10px',
  borderRadius: 10,
  border: '1px solid rgba(255,255,255,0.14)',
  background: 'rgba(255,255,255,0.04)',
  color: '#fff',
  fontFamily: 'JetBrains Mono, monospace',
  fontSize: 12,
  outline: 'none',
}

const codePanelStyle: CSSProperties = {
  border: '1px solid rgba(255,255,255,0.12)',
  borderRadius: 10,
  overflow: 'hidden',
  background: 'rgba(4,10,18,0.85)',
}

const codePanelTitleStyle: CSSProperties = {
  padding: '8px 10px',
  fontFamily: 'JetBrains Mono, monospace',
  fontSize: 11,
  borderBottom: '1px solid rgba(255,255,255,0.12)',
  color: 'rgba(255,255,255,0.74)',
}

const codePreStyle: CSSProperties = {
  margin: 0,
  padding: 10,
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-word',
  fontFamily: 'JetBrains Mono, monospace',
  fontSize: 11,
  lineHeight: 1.5,
  color: '#D8EEFF',
  maxHeight: 320,
  overflow: 'auto',
}