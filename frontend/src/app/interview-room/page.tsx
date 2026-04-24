'use client'

import { useEffect, useState } from 'react'

export const dynamic = 'force-dynamic'

export default function InterviewRoomPage() {
  const [roomUrl, setRoomUrl] = useState<string | null>(null)
  const [role, setRole] = useState<'recruiter' | 'candidate' | 'participant'>('participant')
  const [paramChecked, setParamChecked] = useState(false)
  const [redirecting, setRedirecting] = useState(false)

  useEffect(() => {
    const url = new URL(window.location.href)
    const room = url.searchParams.get('room')
    const roleParam = (url.searchParams.get('role') || '').toLowerCase()
    setRoomUrl(room)
    if (roleParam === 'recruiter') {
      setRole('recruiter')
    } else if (roleParam === 'candidate') {
      setRole('candidate')
    } else {
      setRole('participant')
    }
    setParamChecked(true)
  }, [])

  useEffect(() => {
    if (!roomUrl) {
      return
    }

    // Use native Jitsi UI for a clean, standard call experience.
    setRedirecting(true)
    const timer = window.setTimeout(() => {
      window.location.href = roomUrl
    }, 450)

    return () => window.clearTimeout(timer)
  }, [roomUrl])

  if (!paramChecked) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center">
        <p className="text-[#D4FF00] font-mono animate-pulse">Loading video room...</p>
      </div>
    )
  }

  if (!roomUrl) {
    return (
      <div className="min-h-screen bg-black flex items-center justify-center">
        <p className="text-red-400 font-mono">No room URL provided</p>
      </div>
    )
  }

  const roleLabel = role === 'recruiter' ? 'Recruiter' : role === 'candidate' ? 'Candidate' : 'Participant'

  return (
    <div className="min-h-screen bg-black text-white flex items-center justify-center p-6">
      <div className="w-full max-w-xl border border-white/15 rounded-xl p-6 bg-[#080808]">
        <p className="text-[#D4FF00] font-mono text-xs uppercase tracking-widest mb-2">SkillProof Video Interview</p>
        <p className="text-white font-semibold mb-2">Opening Native Jitsi Room</p>
        <p className="text-gray-400 text-sm mb-4">
          {roleLabel} view is launching with the standard Jitsi interface for a normal video-call experience.
        </p>
        <code className="block w-full border border-white/15 rounded p-3 bg-black/60 text-gray-200 text-xs break-all mb-4">
          {roomUrl}
        </code>
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              if (roomUrl) {
                window.location.href = roomUrl
              }
            }}
            className="px-4 py-2 rounded bg-[#D4FF00] text-black font-semibold text-sm"
          >
            {redirecting ? 'Opening...' : 'Open Now'}
          </button>
          <button
            onClick={() => window.open(roomUrl, '_blank', 'noopener,noreferrer')}
            className="px-4 py-2 rounded border border-white/25 text-white/85 text-sm"
          >
            Open In New Tab
          </button>
        </div>
      </div>
    </div>
  )
}
