'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'next/navigation'

import { openPrReview, submitPrReview } from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import type { PrReviewCommentInput, PrReviewResponse, PrReviewSeverity } from '@/types/pr-review'
import NotificationBell from '@/components/NotificationBell'

// Challenge duration: 5 minutes in seconds
const CHALLENGE_DURATION_SECONDS = 5 * 60

export default function PrReviewPage() {
  const params = useParams()
  const token = String(params.token || '')
  const startTimeRef = useRef<number>(Date.now())
  const autoSubmitTimerRef = useRef<NodeJS.Timeout | null>(null)

  const [review, setReview] = useState<PrReviewResponse | null>(null)
  const [comments, setComments] = useState<PrReviewCommentInput[]>([])
  const [selectedLine, setSelectedLine] = useState<number | null>(null)
  const [newComment, setNewComment] = useState('')
  const [newSeverity, setNewSeverity] = useState<PrReviewSeverity>('IMPORTANT')
  const [submitted, setSubmitted] = useState(false)
  const [result, setResult] = useState<PrReviewResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [timeRemaining, setTimeRemaining] = useState(CHALLENGE_DURATION_SECONDS)
  const [autoSubmitTriggered, setAutoSubmitTriggered] = useState(false)

  // Timer countdown effect
  useEffect(() => {
    const timerInterval = setInterval(() => {
      const elapsed = Math.floor((Date.now() - startTimeRef.current) / 1000)
      const remaining = Math.max(0, CHALLENGE_DURATION_SECONDS - elapsed)
      setTimeRemaining(remaining)

      // Auto-submit when time is up
      if (remaining === 0 && !autoSubmitTriggered && !submitted) {
        setAutoSubmitTriggered(true)
        clearInterval(timerInterval)
      }
    }, 1000)

    return () => clearInterval(timerInterval)
  }, [autoSubmitTriggered, submitted])

  // Auto-submit trigger effect
  useEffect(() => {
    if (autoSubmitTriggered && !submitted && !submitting) {
      void performAutoSubmit()
    }
  }, [autoSubmitTriggered, submitted, submitting])

  const performAutoSubmit = async () => {
    try {
      setSubmitting(true)
      const timeTaken = Math.floor((Date.now() - startTimeRef.current) / 1000)
      
      // Create a minimal submission with existing comments or empty if none
      const response = await submitPrReview(token, { 
        comments: comments.length > 0 ? comments : [], 
        timeTaken 
      })
      
      setResult(response.data)
      setSubmitted(true)
      setError('Time expired. Your review has been auto-submitted.')
    } catch (err) {
      const parsed = parseApiError(err, 'Auto-submission failed.')
      setError(parsed.message)
      setAutoSubmitTriggered(false) // Reset so manual submission can be attempted
    } finally {
      setSubmitting(false)
    }
  }

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  const getTimeStatus = (remaining: number) => {
    if (remaining <= 60) return 'text-red-400'
    if (remaining <= 300) return 'text-yellow-400'
    return 'text-green-400'
  }

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true)
        const res = await openPrReview(token)
        setReview(res.data)
        if (res.data.status === 'COMPLETED') {
          setSubmitted(true)
          setResult(res.data)
        }
      } catch (err) {
        const parsed = parseApiError(err, 'Failed to load review challenge.')
        setError(parsed.message)
      } finally {
        setLoading(false)
      }
    }

    if (token) {
      void load()
    }
  }, [token])

  const codeLines = useMemo(() => review?.modifiedCode?.split('\n') ?? [], [review?.modifiedCode])

  const getLineComment = (lineNumber: number) => comments.find(c => c.lineNumber === lineNumber)

  const addComment = () => {
    if (!selectedLine || !newComment.trim()) {
      return
    }

    const cleaned = newComment.trim()
    const withoutLine = comments.filter(c => c.lineNumber !== selectedLine)
    setComments([...withoutLine, { lineNumber: selectedLine, comment: cleaned, severity: newSeverity }])
    setNewComment('')
    setSelectedLine(null)
  }

  const editComment = (lineNumber: number) => {
    const existing = comments.find(c => c.lineNumber === lineNumber)
    if (existing) {
      setSelectedLine(lineNumber)
      setNewComment(existing.comment)
      setNewSeverity(existing.severity)
    }
  }

  const removeComment = (lineNumber: number) => {
    setComments(comments.filter(c => c.lineNumber !== lineNumber))
  }

  const hasMeaningfulCommentQuality = (commentText: string) => {
    const normalized = commentText.trim().toLowerCase()
    
    // Reject if too short
    if (normalized.length < 15) {
      return false
    }

    // Reject obvious random text (repeated patterns like "jkdfjafhafat")
    if (/(.)\1{3,}/.test(normalized)) {
      return false // Repeated characters
    }

    // Reject keyboard mashing patterns
    if (/[qwerty]{5,}|[asdfgh]{5,}|[zxcvbn]{5,}/.test(normalized)) {
      return false
    }

    // Reject generic placeholders
    if (/\b(error is here|big error|issue here|wrong here|bug here|test|lorem)\b/.test(normalized)) {
      return false
    }

    // Must have at least 3-4 real words (not just random chars)
    const words = normalized.split(/\s+/).filter(w => /^[a-z]+$/.test(w) && w.length >= 3)
    if (words.length < 3) {
      return false
    }

    // Should have some technical signal OR meaningful sentence
    const hasTechSignal = /\b(null|exception|invalid|wrong|fails|error|unsafe|mismatch|bug|condition|check|should|must|replace|change|handle|throw|return|validate)\b/.test(normalized)
    const hasLength = normalized.length >= 25 // Meaningful comments are usually longer
    
    return hasTechSignal || hasLength
  }

  const submitReviewHandler = async () => {
    if (!token) {
      setError('Invalid review token.')
      return
    }

    // Validate comment quality only if comments exist
    if (comments.length > 0) {
      const lowQuality = comments.find(c => !hasMeaningfulCommentQuality(c.comment))
      if (lowQuality) {
        setError(`Comment on line ${lowQuality.lineNumber} is too vague. Explain both the issue and the fix.`)
        return
      }
    }

    try {
      setSubmitting(true)
      setError('')
      const timeTaken = Math.floor((Date.now() - startTimeRef.current) / 1000)
      // Allow submission with empty comments or with meaningful comments
      const response = await submitPrReview(token, { comments, timeTaken })
      setResult(response.data)
      setSubmitted(true)
    } catch (err) {
      const parsed = parseApiError(err, 'Submission failed.')
      setError(parsed.message)
    } finally {
      setSubmitting(false)
    }
  }

  const severityClass = (severity: PrReviewSeverity) => {
    if (severity === 'CRITICAL') {
      return 'text-red-400 bg-red-500/10 border-red-500/30'
    }
    if (severity === 'IMPORTANT') {
      return 'text-yellow-400 bg-yellow-500/10 border-yellow-500/30'
    }
    return 'text-blue-400 bg-blue-500/10 border-blue-500/30'
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center">
        <div className="text-[#D4FF00] font-mono animate-pulse">Loading code review...</div>
      </div>
    )
  }

  if (error && !review) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center">
        <div className="text-red-400 font-mono text-center">
          <p className="text-xl mb-2">Error</p>
          <p className="text-sm text-gray-500">{error}</p>
        </div>
      </div>
    )
  }

  if (submitted && result) {
    const score = result.overallScore || 0
    return (
      <div className="min-h-screen bg-black flex flex-col">
        <div className="border-b border-white/10 px-6 py-3">
          <span className="text-[#D4FF00] font-mono font-bold">SkillProof</span>
          <span className="text-gray-500 font-mono text-sm ml-2">Review Results</span>
        </div>
        
        <div className="flex-1 overflow-auto p-6 max-w-6xl mx-auto w-full">
          <div className="text-center mb-8">
            <p className="text-[#D4FF00] font-mono text-xs uppercase tracking-widest mb-6">Review Submitted</p>
            <div
              className={`font-mono font-bold text-7xl mb-4 ${
                score >= 70 ? 'text-green-400' : score >= 40 ? 'text-yellow-400' : 'text-red-400'
              }`}
            >
              {score}
              <span className="text-gray-500 text-3xl">/100</span>
            </div>
          </div>

          {/* Score metrics */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
            <div className="border border-white/10 rounded p-4">
              <p className="text-gray-500 font-mono text-xs mb-2">Bugs Identified</p>
              <p className="text-white font-mono font-bold text-2xl">{result.bugsFoundCount || 0}</p>
            </div>
            <div className="border border-white/10 rounded p-4">
              <p className="text-gray-500 font-mono text-xs mb-2">Comments Left</p>
              <p className="text-white font-mono font-bold text-2xl">{comments.length}</p>
            </div>
            <div className="border border-white/10 rounded p-4">
              <p className="text-gray-500 font-mono text-xs mb-2">Time Taken</p>
              <p className="text-white font-mono font-bold text-xl">{formatTime(result.timeTakenSeconds || 0)}</p>
            </div>
            <div className="border border-white/10 rounded p-4">
              <p className="text-gray-500 font-mono text-xs mb-2">Candidate</p>
              <p className="text-white font-mono font-bold text-sm truncate">{result.candidateUsername}</p>
            </div>
          </div>

          {/* AI Feedback */}
          {result.aiFeedback && (
            <div className="border border-white/10 rounded p-6 mb-8">
              <p className="text-[#D4FF00] font-mono text-xs uppercase tracking-widest mb-4">AI Feedback</p>
              <p className="text-gray-300 font-mono text-sm leading-relaxed whitespace-pre-wrap">{result.aiFeedback}</p>
            </div>
          )}

          {/* Review Comments */}
          {comments.length > 0 && (
            <div className="border border-white/10 rounded p-6 mb-8">
              <p className="text-[#D4FF00] font-mono text-xs uppercase tracking-widest mb-4">Your Comments ({comments.length})</p>
              <div className="space-y-3">
                {comments.map((comment) => (
                  <div key={`${comment.lineNumber}-${comment.comment}`} className="border-l-2 border-yellow-500/50 pl-3 py-2">
                    <div className="flex items-start gap-3">
                      <span className={`font-mono text-xs font-bold px-2 py-1 rounded ${
                        comment.severity === 'CRITICAL' ? 'bg-red-500/10 text-red-400' :
                        comment.severity === 'IMPORTANT' ? 'bg-yellow-500/10 text-yellow-400' :
                        'bg-blue-500/10 text-blue-400'
                      }`}>
                        {comment.severity}
                      </span>
                      <div className="flex-1">
                        <p className="text-gray-500 font-mono text-xs mb-1">Line {comment.lineNumber}</p>
                        <p className="text-gray-300 font-mono text-sm">{comment.comment}</p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Code Context */}
          {result.modifiedCode && (
            <div className="border border-white/10 rounded p-6">
              <p className="text-[#D4FF00] font-mono text-xs uppercase tracking-widest mb-4">Code Reviewed</p>
              <div className="bg-black/50 rounded border border-white/5 p-4 overflow-auto max-h-96">
                <pre className="font-mono text-sm text-gray-300">{result.modifiedCode}</pre>
              </div>
            </div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <div className="border-b border-white/10 px-6 py-3 flex items-center justify-between gap-4 flex-wrap">
        <div>
          <span className="text-[#D4FF00] font-mono font-bold">SkillProof</span>
          <span className="text-gray-500 font-mono text-sm ml-2">Code Review Challenge</span>
        </div>
        <div className="flex items-center gap-6 flex-wrap">
          <span className="text-gray-400 font-mono text-sm">{review?.filePath}</span>
          <span className="text-gray-600 font-mono text-xs">{review?.repoName}</span>
          {/* Timer display */}
          <div className={`font-mono font-bold text-lg ${getTimeStatus(timeRemaining)}`}>
            {formatTime(timeRemaining)}
          </div>
          <NotificationBell />
          <button
            onClick={submitReviewHandler}
            disabled={submitting}
            className="px-5 py-2 bg-[#D4FF00] text-black font-mono font-bold text-sm rounded disabled:opacity-40"
          >
            {submitting ? 'Evaluating...' : `Submit Review (${comments.length} comment${comments.length !== 1 ? 's' : ''})`}
          </button>
        </div>
      </div>

      <div className="border-b border-white/10 px-6 py-3 bg-white/5">
        <p className="text-gray-400 font-mono text-xs">
          This snippet has one intentional bug. Click any line to leave a review comment and explain the issue.
        </p>
      </div>

      <div className="flex-1 flex flex-col lg:flex-row">
        <div className="flex-1 border-r border-white/10 overflow-auto">
          <div className="font-mono text-sm">
            {codeLines.map((line, index) => {
              const lineNumber = index + 1
              const comment = getLineComment(lineNumber)
              const selected = selectedLine === lineNumber

              return (
                <div key={lineNumber}>
                  <div
                    className={`flex items-start group cursor-pointer transition-colors ${
                      comment ? 'bg-yellow-500/10' : selected ? 'bg-[#D4FF00]/10' : 'hover:bg-white/5'
                    }`}
                    onClick={() => setSelectedLine(selected ? null : lineNumber)}
                  >
                    <span className="text-gray-700 font-mono text-xs w-12 py-1 px-3 select-none border-r border-white/5 text-right shrink-0">
                      {lineNumber}
                    </span>
                    <span className="text-gray-700 w-6 py-1 px-1 text-center opacity-0 group-hover:opacity-100 shrink-0">
                      {comment ? '●' : '+'}
                    </span>
                    <pre className={`py-1 px-2 flex-1 whitespace-pre-wrap break-words ${comment ? 'text-white' : 'text-gray-300'}`}>
                      {line || ' '}
                    </pre>
                  </div>

                  {selected && (
                    <div className="border-l-2 border-[#D4FF00] bg-[#D4FF00]/5 p-4 ml-12">
                      <div className="flex gap-2 mb-2">
                        <select
                          value={newSeverity}
                          onChange={e => setNewSeverity(e.target.value as PrReviewSeverity)}
                          className="bg-black border border-white/20 rounded px-2 py-1 text-white font-mono text-xs"
                        >
                          <option value="CRITICAL">CRITICAL</option>
                          <option value="IMPORTANT">IMPORTANT</option>
                          <option value="MINOR">MINOR</option>
                        </select>
                        <span className="text-gray-500 font-mono text-xs self-center">Line {lineNumber}</span>
                      </div>
                      <textarea
                        value={newComment}
                        onChange={e => setNewComment(e.target.value)}
                        placeholder="Explain what is wrong, why it fails, and what should change."
                        className="w-full bg-black border border-white/20 rounded p-2 text-white font-mono text-sm resize-none outline-none focus:border-[#D4FF00]/50 h-20"
                        autoFocus
                      />
                      <div className="flex gap-2 mt-2">
                        <button
                          onClick={addComment}
                          disabled={!newComment.trim()}
                          className="px-3 py-1 bg-[#D4FF00] text-black font-mono text-xs rounded disabled:opacity-40"
                        >
                          {comment ? 'Update' : 'Add'} Comment
                        </button>
                        <button
                          onClick={() => {
                            setSelectedLine(null)
                            setNewComment('')
                            setNewSeverity('IMPORTANT')
                          }}
                          className="px-3 py-1 border border-white/20 text-gray-400 font-mono text-xs rounded"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}

                  {comment && !selected && (
                    <div className="border-l-2 border-yellow-500/50 bg-yellow-500/5 px-4 py-2 ml-12 flex items-start justify-between gap-4">
                      <div>
                        <span className={`font-mono text-xs font-bold border px-1 rounded mr-2 ${severityClass(comment.severity)}`}>
                          {comment.severity}
                        </span>
                        <span className="text-gray-300 font-mono text-sm">{comment.comment}</span>
                      </div>
                      <div className="flex gap-2 shrink-0">
                        <button
                          onClick={() => editComment(comment.lineNumber)}
                          className="text-gray-600 hover:text-blue-400 font-mono text-xs"
                        >
                          edit
                        </button>
                        <button
                          onClick={() => removeComment(comment.lineNumber)}
                          className="text-gray-600 hover:text-red-400 font-mono text-xs"
                        >
                          remove
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>

        <div className="w-full lg:w-72 p-6 border-l border-white/10 overflow-auto">
          <h3 className="text-white font-mono font-bold mb-4">Review Summary</h3>
          <p className="text-gray-500 font-mono text-xs mb-4">
            Add comments where needed, prioritize real issues, and submit once complete.
          </p>

          {comments.length === 0 ? (
            <div className="border border-white/10 rounded p-4 text-center">
              <p className="text-gray-600 font-mono text-xs">No comments yet. Click a line to start reviewing.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {comments
                .sort((a, b) => a.lineNumber - b.lineNumber)
                .map(c => (
                  <div key={c.lineNumber} className={`border rounded p-3 ${severityClass(c.severity)}`}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-mono text-xs font-bold">{c.severity}</span>
                      <span className="font-mono text-xs opacity-70">Line {c.lineNumber}</span>
                    </div>
                    <p className="font-mono text-xs leading-relaxed">{c.comment}</p>
                    <button
                      onClick={() => removeComment(c.lineNumber)}
                      className="text-xs opacity-50 hover:opacity-100 mt-1"
                    >
                      remove
                    </button>
                  </div>
                ))}
            </div>
          )}

          {error && (
            <div className="border border-red-500/30 bg-red-500/10 rounded p-3 mt-4">
              <p className="text-red-400 font-mono text-xs">{error}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
