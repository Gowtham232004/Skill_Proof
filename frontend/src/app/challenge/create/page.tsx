'use client'

import { useState, type CSSProperties } from 'react'
import { useRouter } from 'next/navigation'
import { createChallenge, createRepoGroundedChallenge } from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import ErrorBanner from '@/app/components/ErrorBanner'

type DraftTestCase = {
  stdin: string
  expectedOutput: string
  isVisible: boolean
}

export default function CreateChallengePage() {
  const router = useRouter()
  const [mode, setMode] = useState<'MANUAL' | 'REPO_GROUNDED'>('MANUAL')
  const [accessMode, setAccessMode] = useState<'OPEN' | 'ASSIGNED'>('OPEN')
  const [assignedUsernames, setAssignedUsernames] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [badgeToken, setBadgeToken] = useState('')
  const [language, setLanguage] = useState('python')
  const [starterCode, setStarterCode] = useState('')
  const [referenceSolution, setReferenceSolution] = useState('')
  const [testCases, setTestCases] = useState<DraftTestCase[]>([
    { stdin: '', expectedOutput: '', isVisible: true },
  ])
  const [timeLimitSeconds, setTimeLimitSeconds] = useState(10)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const updateTestCase = (index: number, patch: Partial<DraftTestCase>) => {
    setTestCases((prev) => prev.map((testCase, idx) => (idx === index ? { ...testCase, ...patch } : testCase)))
  }

  const addTestCase = () => {
    setTestCases((prev) => [...prev, { stdin: '', expectedOutput: '', isVisible: false }])
  }

  const removeTestCase = (index: number) => {
    setTestCases((prev) => {
      if (prev.length <= 1) {
        return prev
      }
      return prev.filter((_, idx) => idx !== index)
    })
  }

  const parseAssignedUsernames = () => Array.from(new Set(
    assignedUsernames
      .split(',')
      .map((username) => username.trim().toLowerCase())
      .filter(Boolean),
  ))

  const submit = async () => {
    setError('')
    setLoading(true)

    try {
      const res = mode === 'REPO_GROUNDED'
        ? await createRepoGroundedChallenge({
            badgeToken,
            preferredLanguage: language as 'python' | 'javascript' | 'java',
            accessMode,
            assignedCandidateUsernames: parseAssignedUsernames(),
            timeLimitSeconds,
          })
        : await createChallenge({
            title,
            description,
            language,
            accessMode,
            assignedCandidateUsernames: parseAssignedUsernames(),
            starterCode,
            referenceSolution,
            timeLimitSeconds,
            testCases: testCases.map((testCase) => ({
              stdin: testCase.stdin,
              expectedOutput: testCase.expectedOutput,
              isVisible: testCase.isVisible,
            })),
          })

      const challengeId = res.data?.id
      if (!challengeId) {
        setError('Challenge created but no challenge id was returned.')
        return
      }

      router.push(`/challenge/${challengeId}`)
    } catch (err) {
      const parsed = parseApiError(err, 'Could not create challenge.')
      setError(parsed.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: '#05070B', color: '#fff', fontFamily: 'Outfit, sans-serif', padding: '36px 16px' }}>
      <style dangerouslySetInnerHTML={{ __html: `
        @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap');
        input::placeholder,textarea::placeholder{color:rgba(255,255,255,0.33)}
      ` }} />

      <div style={{ maxWidth: 920, margin: '0 auto' }}>
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12, color: '#8AA4FF', letterSpacing: '0.12em' }}>
            RECRUITER MODE
          </div>
          <h1 style={{ margin: '8px 0 4px', fontSize: 34, fontWeight: 800 }}>Create Coding Challenge</h1>
          <p style={{ margin: 0, color: 'rgba(255,255,255,0.64)' }}>Share this challenge link with candidates and review submissions.</p>

            <div style={{ marginTop: 12, display: 'inline-flex', border: '1px solid rgba(255,255,255,0.16)', borderRadius: 999, overflow: 'hidden' }}>
              <button
                type="button"
                onClick={() => setMode('MANUAL')}
                style={{
                  border: 'none',
                  padding: '8px 12px',
                  background: mode === 'MANUAL' ? 'rgba(212,255,0,0.2)' : 'transparent',
                  color: mode === 'MANUAL' ? '#D4FF00' : 'rgba(255,255,255,0.65)',
                  fontFamily: 'JetBrains Mono, monospace',
                  fontSize: 12,
                  cursor: 'pointer',
                }}
              >
                Manual
              </button>
              <button
                type="button"
                onClick={() => {
                  setMode('REPO_GROUNDED')
                  setAccessMode('ASSIGNED')
                }}
                style={{
                  border: 'none',
                  padding: '8px 12px',
                  background: mode === 'REPO_GROUNDED' ? 'rgba(96,165,250,0.18)' : 'transparent',
                  color: mode === 'REPO_GROUNDED' ? '#93C5FD' : 'rgba(255,255,255,0.65)',
                  fontFamily: 'JetBrains Mono, monospace',
                  fontSize: 12,
                  cursor: 'pointer',
                }}
              >
                Repo-grounded (Phase 4)
              </button>
            </div>

            <div style={{ marginTop: 12, display: 'inline-flex', border: '1px solid rgba(255,255,255,0.16)', borderRadius: 999, overflow: 'hidden' }}>
              <button
                type="button"
                onClick={() => setAccessMode('OPEN')}
                style={{
                  border: 'none',
                  padding: '8px 12px',
                  background: accessMode === 'OPEN' ? 'rgba(52,211,153,0.2)' : 'transparent',
                  color: accessMode === 'OPEN' ? '#6EE7B7' : 'rgba(255,255,255,0.65)',
                  fontFamily: 'JetBrains Mono, monospace',
                  fontSize: 12,
                  cursor: 'pointer',
                }}
              >
                Open challenge
              </button>
              <button
                type="button"
                onClick={() => setAccessMode('ASSIGNED')}
                style={{
                  border: 'none',
                  padding: '8px 12px',
                  background: accessMode === 'ASSIGNED' ? 'rgba(251,191,36,0.2)' : 'transparent',
                  color: accessMode === 'ASSIGNED' ? '#FCD34D' : 'rgba(255,255,255,0.65)',
                  fontFamily: 'JetBrains Mono, monospace',
                  fontSize: 12,
                  cursor: 'pointer',
                }}
              >
                Assigned challenge
              </button>
            </div>
        </div>

        {error && <ErrorBanner message={error} compact />}

        <div style={{ display: 'grid', gap: 14 }}>
          {mode === 'MANUAL' ? (
            <>
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Challenge title"
                style={inputStyle}
              />

              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Describe the challenge clearly."
                style={{ ...inputStyle, minHeight: 120, resize: 'vertical' }}
              />
            </>
          ) : (
            <>
              <input
                value={badgeToken}
                onChange={(e) => setBadgeToken(e.target.value)}
                placeholder="Badge token (e.g. sp_xxx)"
                style={inputStyle}
              />
              <div style={{ border: '1px solid rgba(96,165,250,0.3)', borderRadius: 12, padding: 12, background: 'rgba(2,6,23,0.55)', color: 'rgba(255,255,255,0.75)', fontSize: 13, lineHeight: 1.55 }}>
                Repo-grounded mode auto-generates title, prompt, starter code, reference solution, and hidden tests from the candidate repository summary linked to this badge.
              </div>
            </>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <select value={language} onChange={(e) => setLanguage(e.target.value)} style={inputStyle}>
              <option value="python">python</option>
              <option value="javascript">javascript</option>
              <option value="java">java</option>
            </select>
            <input
              type="number"
              min={1}
              max={120}
              value={timeLimitSeconds}
              onChange={(e) => setTimeLimitSeconds(Number(e.target.value))}
              style={inputStyle}
              placeholder="Time limit (seconds)"
            />
          </div>

          {accessMode === 'ASSIGNED' && (
            <>
              <input
                value={assignedUsernames}
                onChange={(e) => setAssignedUsernames(e.target.value)}
                placeholder="Candidate usernames (comma-separated)"
                style={inputStyle}
              />
              <div style={{ border: '1px solid rgba(251,191,36,0.3)', borderRadius: 12, padding: 12, background: 'rgba(30,20,3,0.45)', color: 'rgba(255,255,255,0.8)', fontSize: 13, lineHeight: 1.55 }}>
                Only assigned candidates can submit in this mode. {mode === 'REPO_GROUNDED' ? 'If left empty, the badge owner is auto-assigned.' : ''}
              </div>
            </>
          )}

          {mode === 'MANUAL' && (
            <>
              <textarea
                value={starterCode}
                onChange={(e) => setStarterCode(e.target.value)}
                placeholder="Starter code shown to the candidate"
                style={{ ...editorStyle, minHeight: 170 }}
              />

              <textarea
                value={referenceSolution}
                onChange={(e) => setReferenceSolution(e.target.value)}
                placeholder="Reference solution used for similarity scoring"
                style={{ ...editorStyle, minHeight: 170 }}
              />
            </>
          )}

          {mode === 'MANUAL' && testCases.map((testCase, index) => (
            <div
              key={`test-case-${index}`}
              style={{
                display: 'grid',
                gap: 10,
                border: '1px solid rgba(255,255,255,0.12)',
                borderRadius: 12,
                padding: 12,
                background: 'rgba(6,10,18,0.62)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
                <div style={{ fontWeight: 700, fontSize: 13 }}>Test case #{index + 1}</div>
                <button
                  type="button"
                  onClick={() => removeTestCase(index)}
                  disabled={testCases.length === 1}
                  style={secondaryButtonStyle}
                >
                  Remove
                </button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <textarea
                  value={testCase.stdin}
                  onChange={(e) => updateTestCase(index, { stdin: e.target.value })}
                  placeholder="Test input (stdin)"
                  style={{ ...editorStyle, minHeight: 100 }}
                />
                <textarea
                  value={testCase.expectedOutput}
                  onChange={(e) => updateTestCase(index, { expectedOutput: e.target.value })}
                  placeholder="Expected output"
                  style={{ ...editorStyle, minHeight: 100 }}
                />
              </div>

              <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'rgba(255,255,255,0.86)' }}>
                <input
                  type="checkbox"
                  checked={testCase.isVisible}
                  onChange={(e) => updateTestCase(index, { isVisible: e.target.checked })}
                />
                Show expected output to candidate for this test case
              </label>
            </div>
          ))}

          {mode === 'MANUAL' && (
            <>
              <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.68)', fontFamily: 'JetBrains Mono, monospace' }}>
                Visibility summary: {testCases.filter((testCase) => testCase.isVisible).length} visible / {testCases.length} total
              </div>

              <button type="button" onClick={addTestCase} style={secondaryButtonStyle}>
                Add test case
              </button>
            </>
          )}

          <button
            onClick={submit}
            disabled={
              loading ||
              (mode === 'MANUAL'
                ? (!title.trim() ||
                  !description.trim() ||
                  (accessMode === 'ASSIGNED' && parseAssignedUsernames().length === 0) ||
                  testCases.length === 0 ||
                  testCases.some((testCase) => !testCase.expectedOutput.trim()))
                : !badgeToken.trim())
            }
            style={{
              border: 'none',
              background: 'linear-gradient(100deg, #A9D0FF 0%, #74F2CE 100%)',
              color: '#081018',
              fontSize: 14,
              fontWeight: 800,
              padding: '12px 18px',
              borderRadius: 12,
              cursor: loading ? 'wait' : 'pointer',
              opacity: loading ? 0.75 : 1,
            }}
          >
            {loading ? 'Creating challenge...' : mode === 'REPO_GROUNDED' ? 'Generate repo-grounded challenge' : 'Create challenge'}
          </button>
        </div>
      </div>
    </div>
  )
}

const inputStyle: CSSProperties = {
  width: '100%',
  padding: '12px 14px',
  borderRadius: 12,
  border: '1px solid rgba(255,255,255,0.15)',
  background: 'rgba(6,10,18,0.9)',
  color: '#fff',
  fontSize: 14,
  outline: 'none',
  boxSizing: 'border-box',
}

const editorStyle: CSSProperties = {
  ...inputStyle,
  fontFamily: 'JetBrains Mono, monospace',
  fontSize: 12,
  lineHeight: 1.5,
  resize: 'vertical',
}

const secondaryButtonStyle: CSSProperties = {
  border: '1px solid rgba(255,255,255,0.22)',
  background: 'rgba(6,10,18,0.75)',
  color: '#E7F0FF',
  fontSize: 13,
  fontWeight: 700,
  padding: '8px 12px',
  borderRadius: 10,
  cursor: 'pointer',
}
