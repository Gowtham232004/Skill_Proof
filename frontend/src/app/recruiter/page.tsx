'use client'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { motion, AnimatePresence } from 'framer-motion'
import { api } from '@/lib/api'

interface Candidate {
  sessionId: number
  githubUsername: string
  avatarUrl: string
  displayName: string
  repoName: string
  repoOwner?: string
  overallScore: number
  backendScore: number
  apiDesignScore: number
  errorHandlingScore: number
  codeQualityScore: number
  documentationScore: number
  badgeToken: string
  issuedAt: string
  primaryLanguage: string
}

const DIMS = [
  { key: 'backendScore', label: 'Backend', short: 'BK', color: '#D4FF00' },
  { key: 'apiDesignScore', label: 'API', short: 'AP', color: '#60A5FA' },
  { key: 'errorHandlingScore', label: 'Errors', short: 'ER', color: '#F59E0B' },
  { key: 'codeQualityScore', label: 'Quality', short: 'QU', color: '#34D399' },
  { key: 'documentationScore', label: 'Docs', short: 'DO', color: '#F472B6' },
] as const

export default function RecruiterPage() {
  const router = useRouter()
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<Candidate | null>(null)
  const [search, setSearch] = useState('')
  const [sortBy, setSortBy] = useState<'score' | 'date'>('score')
  const [user, setUser] = useState<any>(null)

  useEffect(() => {
    const token = localStorage.getItem('sp_token')
    const userStr = localStorage.getItem('sp_user')
    if (!token) { router.push('/'); return }
    if (userStr) setUser(JSON.parse(userStr))

    api.get('/api/recruiter/candidates')
      .then(res => { setCandidates(res.data); setLoading(false) })
      .catch(() => {
        // Demo fallback
        setCandidates([
          { sessionId: 1, githubUsername: 'Gowtham232004', avatarUrl: '', displayName: 'Gowtham M S', repoName: 'Automl', overallScore: 78, backendScore: 85, apiDesignScore: 72, errorHandlingScore: 68, codeQualityScore: 88, documentationScore: 55, badgeToken: 'sp_demo', issuedAt: new Date().toISOString(), primaryLanguage: 'TypeScript' },
          { sessionId: 2, githubUsername: 'priya_s', avatarUrl: '', displayName: 'Priya Sharma', repoName: 'ecommerce-api', overallScore: 87, backendScore: 92, apiDesignScore: 85, errorHandlingScore: 80, codeQualityScore: 90, documentationScore: 70, badgeToken: 'sp_demo2', issuedAt: new Date().toISOString(), primaryLanguage: 'Java' },
          { sessionId: 3, githubUsername: 'arjun_n', avatarUrl: '', displayName: 'Arjun Nair', repoName: 'ml-pipeline', overallScore: 71, backendScore: 75, apiDesignScore: 68, errorHandlingScore: 60, codeQualityScore: 82, documentationScore: 50, badgeToken: 'sp_demo3', issuedAt: new Date().toISOString(), primaryLanguage: 'Python' },
        ])
        setLoading(false)
      })
  }, [])

  const filtered = candidates
    .filter(c =>
      c.displayName.toLowerCase().includes(search.toLowerCase()) ||
      c.githubUsername.toLowerCase().includes(search.toLowerCase()) ||
      c.repoName.toLowerCase().includes(search.toLowerCase())
    )
    .sort((a, b) => sortBy === 'score'
      ? b.overallScore - a.overallScore
      : new Date(b.issuedAt).getTime() - new Date(a.issuedAt).getTime()
    )

  const avg = candidates.length > 0
    ? Math.round(candidates.reduce((s, c) => s + c.overallScore, 0) / candidates.length)
    : 0

  const scoreColor = (s: number) => s >= 80 ? '#34D399' : s >= 60 ? '#F59E0B' : '#F472B6'

  return (
    <div style={{ minHeight: '100vh', background: '#000', color: '#fff', fontFamily: 'Outfit, sans-serif' }}>
      <style dangerouslySetInnerHTML={{ __html: `
        @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500&display=swap');
        @keyframes spin{to{transform:rotate(360deg)}}
        input::placeholder{color:rgba(255,255,255,0.2)}
        ::-webkit-scrollbar{width:4px}
        ::-webkit-scrollbar-thumb{background:rgba(212,255,0,0.2);border-radius:2px}
      ` }} />

      {/* Nav */}
      <nav style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 100, background: 'rgba(0,0,0,0.92)', backdropFilter: 'blur(20px)', borderBottom: '1px solid rgba(255,255,255,0.06)', height: 60, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <motion.div whileHover={{ scale: 1.05 }} onClick={() => router.push('/')} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}>
            <div style={{ width: 32, height: 32, borderRadius: 8, background: '#D4FF00', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'JetBrains Mono, monospace', fontWeight: 800, fontSize: 11, color: '#000' }}>SP</div>
            <span style={{ fontWeight: 900, fontSize: 16 }}>SkillProof</span>
          </motion.div>
          <div style={{ height: 18, width: 1, background: 'rgba(255,255,255,0.1)' }} />
          <span style={{ fontSize: 12, fontFamily: 'JetBrains Mono, monospace', color: '#D4FF00', fontWeight: 600, letterSpacing: '0.1em' }}>RECRUITER DASHBOARD</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {user && <span style={{ fontSize: 13, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.35)' }}>@{user.githubUsername}</span>}
          <motion.button whileHover={{ scale: 1.02 }} onClick={() => router.push('/verify')}
            style={{ padding: '8px 16px', background: '#D4FF00', border: 'none', borderRadius: 8, color: '#000', fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>
            + New Candidate
          </motion.button>
        </div>
      </nav>

      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '80px 24px 60px' }}>

        {/* Stats */}
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
          style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 28 }}>
          {[
            { label: 'Total Verified', value: `${candidates.length}`, color: '#D4FF00' },
            { label: 'Avg Score', value: `${avg}/100`, color: '#34D399' },
            { label: 'Above 80', value: `${candidates.filter(c => c.overallScore >= 80).length}`, color: '#60A5FA' },
            { label: 'Top Score', value: candidates.length > 0 ? `${Math.max(...candidates.map(c => c.overallScore))}` : '—', color: '#A78BFA' },
          ].map((stat, i) => (
            <motion.div key={i} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.08 }}
              style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 14, padding: '18px 20px' }}>
              <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.3)', marginBottom: 8, letterSpacing: '0.08em' }}>{stat.label.toUpperCase()}</div>
              <div style={{ fontSize: 30, fontWeight: 900, color: stat.color, letterSpacing: '-0.04em', fontFamily: 'JetBrains Mono, monospace' }}>{stat.value}</div>
            </motion.div>
          ))}
        </motion.div>

        <div style={{ display: 'grid', gridTemplateColumns: selected ? '1fr 340px' : '1fr', gap: 16, alignItems: 'start' }}>

          {/* List */}
          <div>
            {/* Filters */}
            <div style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by name, username, or repo..."
                style={{ flex: 1, padding: '10px 14px', background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 10, color: '#fff', fontSize: 14, fontFamily: 'Outfit, sans-serif', outline: 'none' }} />
              {(['score', 'date'] as const).map(s => (
                <motion.button key={s} whileHover={{ scale: 1.02 }} onClick={() => setSortBy(s)}
                  style={{ padding: '10px 16px', background: sortBy === s ? 'rgba(212,255,0,0.1)' : 'rgba(255,255,255,0.04)', border: `1px solid ${sortBy === s ? 'rgba(212,255,0,0.3)' : 'rgba(255,255,255,0.08)'}`, borderRadius: 10, color: sortBy === s ? '#D4FF00' : 'rgba(255,255,255,0.4)', fontSize: 12, fontWeight: 600, cursor: 'pointer', fontFamily: 'JetBrains Mono, monospace', transition: 'all 0.2s', whiteSpace: 'nowrap' }}>
                  ↓ {s === 'score' ? 'Score' : 'Date'}
                </motion.button>
              ))}
            </div>

            {/* Header */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 70px 260px 90px', gap: 12, padding: '8px 16px', marginBottom: 6 }}>
              {['CANDIDATE', 'SCORE', 'SKILL BREAKDOWN', 'ACTION'].map(h => (
                <span key={h} style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.2)', letterSpacing: '0.1em' }}>{h}</span>
              ))}
            </div>

            {loading ? (
              <div style={{ textAlign: 'center', padding: '60px 0' }}>
                <div style={{ width: 32, height: 32, border: '2px solid rgba(212,255,0,0.2)', borderTopColor: '#D4FF00', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto' }} />
              </div>
            ) : filtered.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '60px 0', color: 'rgba(255,255,255,0.2)', fontFamily: 'JetBrains Mono, monospace', fontSize: 13 }}>No candidates found</div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {filtered.map((c, i) => {
                  const sc = scoreColor(c.overallScore)
                  const isSelected = selected?.sessionId === c.sessionId
                  return (
                    <motion.div key={c.sessionId}
                      initial={{ opacity: 0, x: -16 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.05 }}
                      whileHover={{ x: 3 }} onClick={() => setSelected(isSelected ? null : c)}
                      style={{ display: 'grid', gridTemplateColumns: '1fr 70px 260px 90px', gap: 12, alignItems: 'center', padding: '14px 16px', background: isSelected ? 'rgba(212,255,0,0.04)' : '#0A0A0A', border: `1px solid ${isSelected ? 'rgba(212,255,0,0.25)' : 'rgba(255,255,255,0.06)'}`, borderRadius: 14, cursor: 'pointer', transition: 'border-color 0.2s' }}>

                      {/* Identity */}
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
                        <div style={{ width: 36, height: 36, borderRadius: 10, background: `${sc}15`, color: sc, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 14, flexShrink: 0 }}>
                          {(c.displayName || c.githubUsername)[0].toUpperCase()}
                        </div>
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.displayName || c.githubUsername}</div>
                          <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.3)' }}>/{c.repoName} {c.primaryLanguage && `· ${c.primaryLanguage}`}</div>
                        </div>
                      </div>

                      {/* Score */}
                      <div style={{ fontSize: 26, fontWeight: 900, color: sc, fontFamily: 'JetBrains Mono, monospace', letterSpacing: '-0.03em' }}>{c.overallScore}</div>

                      {/* Skill bars */}
                      <div style={{ display: 'flex', gap: 6 }}>
                        {DIMS.map(dim => (
                          <div key={dim.key} style={{ flex: 1 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
                              <span style={{ fontSize: 9, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.2)' }}>{dim.short}</span>
                              <span style={{ fontSize: 9, fontWeight: 700, color: dim.color, fontFamily: 'JetBrains Mono, monospace' }}>{(c as any)[dim.key]}</span>
                            </div>
                            <div style={{ height: 3, background: 'rgba(255,255,255,0.05)', borderRadius: 2 }}>
                              <motion.div initial={{ width: 0 }} animate={{ width: `${(c as any)[dim.key]}%` }}
                                transition={{ duration: 0.8, ease: 'easeOut' }}
                                style={{ height: '100%', background: dim.color, borderRadius: 2 }} />
                            </div>
                          </div>
                        ))}
                      </div>

                      {/* Action */}
                      <motion.button whileHover={{ scale: 1.04 }} whileTap={{ scale: 0.97 }}
                        onClick={e => { e.stopPropagation(); router.push(`/badge/${c.badgeToken}`) }}
                        style={{ padding: '7px 12px', background: 'rgba(212,255,0,0.08)', border: '1px solid rgba(212,255,0,0.2)', borderRadius: 8, color: '#D4FF00', fontSize: 12, fontWeight: 700, cursor: 'pointer', fontFamily: 'JetBrains Mono, monospace' }}>
                        Badge →
                      </motion.button>
                    </motion.div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Side panel */}
          <AnimatePresence>
            {selected && (
              <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 20 }}
                transition={{ duration: 0.3 }}
                style={{ background: '#0A0A0A', border: '1px solid rgba(212,255,0,0.2)', borderRadius: 20, padding: 24, position: 'sticky', top: 76, maxHeight: 'calc(100vh - 96px)', overflowY: 'auto' }}>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
                  <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: '#D4FF00', letterSpacing: '0.1em' }}>CANDIDATE DETAIL</div>
                  <button onClick={() => setSelected(null)} style={{ background: 'none', border: 'none', color: 'rgba(255,255,255,0.3)', fontSize: 20, cursor: 'pointer', lineHeight: 1 }}>×</button>
                </div>

                {/* Score ring */}
                <div style={{ textAlign: 'center', marginBottom: 20 }}>
                  {(() => {
                    const sc = selected.overallScore
                    const color = scoreColor(sc)
                    const r = 40, circ = 2 * Math.PI * r
                    return (
                      <svg width="100" height="100" viewBox="0 0 100 100" style={{ display: 'block', margin: '0 auto' }}>
                        <circle cx="50" cy="50" r={r} fill="none" stroke="rgba(255,255,255,0.05)" strokeWidth="7" />
                        <motion.circle cx="50" cy="50" r={r} fill="none" stroke={color} strokeWidth="7"
                          strokeLinecap="round" strokeDasharray={circ}
                          initial={{ strokeDashoffset: circ }}
                          animate={{ strokeDashoffset: circ * (1 - sc / 100) }}
                          transition={{ duration: 1.5, ease: 'easeOut' }}
                          transform="rotate(-90 50 50)"
                          style={{ filter: `drop-shadow(0 0 6px ${color}60)` }} />
                        <text x="50" y="48" textAnchor="middle" dominantBaseline="middle"
                          fill={color} fontSize="18" fontWeight="900" fontFamily="JetBrains Mono, monospace">{sc}</text>
                        <text x="50" y="62" textAnchor="middle" fill="rgba(255,255,255,0.25)" fontSize="9" fontFamily="JetBrains Mono, monospace">/100</text>
                      </svg>
                    )
                  })()}
                  <div style={{ fontSize: 17, fontWeight: 800, marginTop: 8 }}>{selected.displayName}</div>
                  <div style={{ fontSize: 12, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.35)', marginTop: 3 }}>@{selected.githubUsername} · {selected.repoName}</div>
                </div>

                {/* Skill breakdown */}
                <div style={{ marginBottom: 20 }}>
                  {DIMS.map(dim => (
                    <div key={dim.key} style={{ marginBottom: 10 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                        <span style={{ fontSize: 12, color: 'rgba(255,255,255,0.45)' }}>{dim.label}</span>
                        <span style={{ fontSize: 12, fontWeight: 800, color: dim.color, fontFamily: 'JetBrains Mono, monospace' }}>{(selected as any)[dim.key]}</span>
                      </div>
                      <div style={{ height: 4, background: 'rgba(255,255,255,0.05)', borderRadius: 2 }}>
                        <motion.div initial={{ width: 0 }} animate={{ width: `${(selected as any)[dim.key]}%` }}
                          transition={{ duration: 1, ease: 'easeOut' }}
                          style={{ height: '100%', background: dim.color, borderRadius: 2 }} />
                      </div>
                    </div>
                  ))}
                </div>

                {/* Meta */}
                <div style={{ padding: '12px 14px', background: 'rgba(255,255,255,0.03)', borderRadius: 10, marginBottom: 16 }}>
                  <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.25)', marginBottom: 8, letterSpacing: '0.08em' }}>VERIFICATION INFO</div>
                  <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.4)', fontFamily: 'JetBrains Mono, monospace', lineHeight: 1.8 }}>
                    <div>Repo: {selected.repoOwner || selected.githubUsername}/{selected.repoName}</div>
                    <div>Issued: {new Date(selected.issuedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</div>
                    <div>Algorithm: HMAC-SHA256</div>
                  </div>
                </div>

                {/* Actions */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <motion.button whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
                    onClick={() => router.push(`/badge/${selected.badgeToken}`)}
                    style={{ width: '100%', padding: '12px', background: '#D4FF00', border: 'none', borderRadius: 10, color: '#000', fontWeight: 800, fontSize: 14, cursor: 'pointer', fontFamily: 'Outfit, sans-serif' }}>
                    View Full Badge →
                  </motion.button>
                  <motion.button whileHover={{ scale: 1.02 }}
                    onClick={() => { navigator.clipboard.writeText(`http://localhost:3000/badge/${selected.badgeToken}`); }}
                    style={{ width: '100%', padding: '12px', background: 'transparent', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, color: 'rgba(255,255,255,0.6)', fontWeight: 600, fontSize: 13, cursor: 'pointer', fontFamily: 'Outfit, sans-serif' }}>
                    Copy Badge URL
                  </motion.button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  )
}