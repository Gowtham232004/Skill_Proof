'use client'
import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { motion } from 'framer-motion'
import { getBadge } from '@/lib/api'

interface BadgeData {
  valid: boolean
  verificationToken: string
  badgeUrl: string
  githubUsername: string
  avatarUrl: string
  displayName: string
  repoName: string
  repoOwner: string
  repoDescription: string
  primaryLanguage: string
  frameworksDetected: string[]
  overallScore: number
  backendScore: number
  apiDesignScore: number
  errorHandlingScore: number
  codeQualityScore: number
  documentationScore: number
  issuedAt: string
}

const SKILLS = [
  { key: 'backendScore', label: 'Backend Logic', color: '#D4FF00' },
  { key: 'apiDesignScore', label: 'API Design', color: '#60A5FA' },
  { key: 'errorHandlingScore', label: 'Error Handling', color: '#F59E0B' },
  { key: 'codeQualityScore', label: 'Code Quality', color: '#34D399' },
  { key: 'documentationScore', label: 'Documentation', color: '#F472B6' },
] as const

export default function BadgePage() {
  const params = useParams()
  const router = useRouter()
  const token = params.token as string
  const [badge, setBadge] = useState<BadgeData | null>(null)
  const [loading, setLoading] = useState(true)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    getBadge(token)
      .then(res => { setBadge(res.data); setLoading(false) })
      .catch(() => setLoading(false))
  }, [token])

  const handleCopy = () => {
    navigator.clipboard.writeText(window.location.href)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const scoreColor = badge
    ? badge.overallScore >= 80 ? '#34D399'
    : badge.overallScore >= 60 ? '#F59E0B' : '#F472B6'
    : '#D4FF00'

  if (loading) return (
    <div style={{ minHeight: '100vh', background: '#000', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ textAlign: 'center' }}>
        <div style={{ width: 48, height: 48, border: '2px solid rgba(212,255,0,0.2)', borderTopColor: '#D4FF00', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 16px' }} />
        <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
        <p style={{ color: 'rgba(255,255,255,0.3)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>Verifying badge...</p>
      </div>
    </div>
  )

  if (!badge || !badge.valid) return (
    <div style={{ minHeight: '100vh', background: '#000', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'Outfit, sans-serif' }}>
      <div style={{ textAlign: 'center', padding: 40 }}>
        <div style={{ fontSize: 48, marginBottom: 20 }}>✗</div>
        <h1 style={{ fontSize: 28, fontWeight: 900, color: '#F472B6', marginBottom: 12 }}>Badge Not Found</h1>
        <p style={{ color: 'rgba(255,255,255,0.4)', marginBottom: 28 }}>This badge token is invalid or has been revoked.</p>
        <button onClick={() => router.push('/')}
          style={{ background: '#D4FF00', color: '#000', border: 'none', padding: '12px 28px', borderRadius: 10, fontWeight: 700, cursor: 'pointer' }}>
          Go to SkillProof
        </button>
      </div>
    </div>
  )

  return (
    <div style={{ minHeight: '100vh', background: '#000', color: '#fff', fontFamily: 'Outfit, sans-serif' }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500&display=swap');
        @keyframes spin{to{transform:rotate(360deg)}}
        body::before{content:'';position:fixed;inset:0;background-image:url("data:image/svg+xml,%3Csvg viewBox='0 0 512 512' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.03'/%3E%3C/svg%3E");pointer-events:none;z-index:9999}
      `}</style>

      {/* Nav */}
      <nav style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 100, background: 'rgba(0,0,0,0.9)', backdropFilter: 'blur(20px)', borderBottom: '1px solid rgba(255,255,255,0.06)', height: 56, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px' }}>
        <motion.div whileHover={{ scale: 1.05 }} onClick={() => router.push('/')} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}>
          <div style={{ width: 30, height: 30, borderRadius: 7, background: '#D4FF00', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'JetBrains Mono, monospace', fontWeight: 800, fontSize: 10, color: '#000' }}>SP</div>
          <span style={{ fontWeight: 900, fontSize: 15 }}>SkillProof</span>
        </motion.div>
        <div style={{ display: 'flex', gap: 10 }}>
          <motion.button whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}
            onClick={handleCopy}
            style={{ padding: '8px 16px', background: copied ? 'rgba(52,211,153,0.15)' : 'rgba(255,255,255,0.06)', border: `1px solid ${copied ? 'rgba(52,211,153,0.4)' : 'rgba(255,255,255,0.1)'}`, borderRadius: 8, color: copied ? '#34D399' : 'rgba(255,255,255,0.6)', fontSize: 13, fontWeight: 600, cursor: 'pointer', fontFamily: 'Outfit, sans-serif', transition: 'all 0.2s' }}>
            {copied ? '✓ Copied!' : '⎘ Share Badge'}
          </motion.button>
          <motion.button whileHover={{ scale: 1.02 }} onClick={() => router.push('/verify')}
            style={{ padding: '8px 16px', background: '#D4FF00', border: 'none', borderRadius: 8, color: '#000', fontSize: 13, fontWeight: 700, cursor: 'pointer', fontFamily: 'Outfit, sans-serif' }}>
            Verify My Portfolio
          </motion.button>
        </div>
      </nav>

      {/* Background glow */}
      <div style={{ position: 'fixed', top: '20%', left: '50%', transform: 'translateX(-50%)', width: 600, height: 400, background: `radial-gradient(ellipse, ${scoreColor}08 0%, transparent 65%)`, pointerEvents: 'none' }} />

      <div style={{ maxWidth: 680, margin: '0 auto', padding: '80px 24px 60px' }}>

        {/* Verified header */}
        <motion.div initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }}
          style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 16px', borderRadius: 100, background: 'rgba(52,211,153,0.1)', border: '1px solid rgba(52,211,153,0.3)', marginBottom: 16 }}>
            <span style={{ color: '#34D399', fontSize: 14 }}>✓</span>
            <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 11, color: '#34D399', fontWeight: 600, letterSpacing: '0.1em' }}>VERIFIED BY SKILLPROOF</span>
          </div>
          <h1 style={{ fontSize: 'clamp(28px, 5vw, 44px)', fontWeight: 900, letterSpacing: '-0.03em', margin: 0 }}>
            Developer Credential
          </h1>
        </motion.div>

        {/* Main badge card */}
        <motion.div initial={{ opacity: 0, y: 30, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
          style={{ background: '#0A0A0A', border: `1px solid ${scoreColor}30`, borderRadius: 24, overflow: 'hidden', marginBottom: 16, position: 'relative', boxShadow: `0 0 80px ${scoreColor}08` }}>

          {/* Top color band */}
          <div style={{ height: 4, background: `linear-gradient(90deg, ${scoreColor}, #60A5FA, #A78BFA)` }} />

          <div style={{ padding: 28 }}>
            {/* Developer identity */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 28, paddingBottom: 24, borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
              <div style={{ position: 'relative' }}>
                {badge.avatarUrl ? (
                  <img src={badge.avatarUrl} alt="" style={{ width: 56, height: 56, borderRadius: 14, border: `2px solid ${scoreColor}40` }} />
                ) : (
                  <div style={{ width: 56, height: 56, borderRadius: 14, background: `${scoreColor}20`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, fontWeight: 800, color: scoreColor }}>
                    {(badge.displayName || badge.githubUsername || 'U')[0].toUpperCase()}
                  </div>
                )}
                <div style={{ position: 'absolute', bottom: -4, right: -4, width: 20, height: 20, borderRadius: '50%', background: '#34D399', border: '2px solid #0A0A0A', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, color: '#000', fontWeight: 900 }}>✓</div>
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 20, fontWeight: 800, letterSpacing: '-0.02em' }}>{badge.displayName || badge.githubUsername}</div>
                <div style={{ fontSize: 13, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.35)', marginTop: 3 }}>@{badge.githubUsername}</div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: 52, fontWeight: 900, color: scoreColor, lineHeight: 1, letterSpacing: '-0.04em', fontFamily: 'JetBrains Mono, monospace' }}>{badge.overallScore}</div>
                <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.25)', marginTop: 2 }}>overall score</div>
              </div>
            </div>

            {/* Repo info */}
            <div style={{ padding: '14px 16px', background: 'rgba(255,255,255,0.03)', borderRadius: 12, border: '1px solid rgba(255,255,255,0.05)', marginBottom: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                <div>
                  <div style={{ fontSize: 15, fontWeight: 700, color: scoreColor, marginBottom: 3 }}>
                    {badge.repoOwner}/{badge.repoName}
                  </div>
                  {badge.repoDescription && (
                    <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.4)', lineHeight: 1.5 }}>{badge.repoDescription}</div>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 6, flexShrink: 0, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                  {badge.primaryLanguage && (
                    <span style={{ padding: '3px 8px', borderRadius: 6, background: 'rgba(255,255,255,0.06)', fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.4)' }}>
                      {badge.primaryLanguage}
                    </span>
                  )}
                  {badge.frameworksDetected?.slice(0, 2).map((f, i) => (
                    <span key={i} style={{ padding: '3px 8px', borderRadius: 6, background: `${scoreColor}10`, fontSize: 11, fontFamily: 'JetBrains Mono, monospace', color: scoreColor }}>
                      {f}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            {/* Skill scores */}
            <div style={{ marginBottom: 24 }}>
              {SKILLS.map((skill, i) => (
                <div key={skill.key} style={{ marginBottom: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
                    <span style={{ fontSize: 13, color: 'rgba(255,255,255,0.45)' }}>{skill.label}</span>
                    <span style={{ fontSize: 13, fontWeight: 800, color: skill.color, fontFamily: 'JetBrains Mono, monospace' }}>
                      {(badge as any)[skill.key]}
                    </span>
                  </div>
                  <div style={{ height: 5, background: 'rgba(255,255,255,0.05)', borderRadius: 3, overflow: 'hidden' }}>
                    <motion.div
                      initial={{ width: 0 }}
                      animate={{ width: `${(badge as any)[skill.key]}%` }}
                      transition={{ duration: 1.2, delay: i * 0.1, ease: 'easeOut' }}
                      style={{ height: '100%', background: skill.color, borderRadius: 3, boxShadow: `0 0 8px ${skill.color}50` }}
                    />
                  </div>
                </div>
              ))}
            </div>

            {/* Verification metadata */}
            <div style={{ padding: '14px 16px', background: 'rgba(212,255,0,0.04)', border: '1px solid rgba(212,255,0,0.12)', borderRadius: 12 }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                {[
                  { label: 'Badge Token', value: badge.verificationToken.substring(0, 20) + '...' },
                  { label: 'Algorithm', value: 'HMAC-SHA256' },
                  { label: 'Issued', value: badge.issuedAt ? new Date(badge.issuedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : 'March 2026' },
                  { label: 'Status', value: '✓ Tamper-proof' },
                ].map((item, i) => (
                  <div key={i}>
                    <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(212,255,0,0.4)', marginBottom: 3, letterSpacing: '0.08em' }}>{item.label.toUpperCase()}</div>
                    <div style={{ fontSize: 12, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.5)' }}>{item.value}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </motion.div>

        {/* Recruiter CTA */}
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 }}
          style={{ padding: '20px 24px', background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 16, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 700, marginBottom: 4 }}>Are you hiring?</div>
            <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.4)' }}>Verify your candidates with SkillProof. Free to start.</div>
          </div>
          <motion.button whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}
            onClick={() => router.push('/#pricing')}
            style={{ padding: '12px 24px', background: '#D4FF00', color: '#000', border: 'none', borderRadius: 10, fontWeight: 800, fontSize: 14, cursor: 'pointer', fontFamily: 'Outfit, sans-serif', whiteSpace: 'nowrap' }}>
            Verify a Candidate →
          </motion.button>
        </motion.div>

        {/* Footer note */}
        <div style={{ textAlign: 'center', marginTop: 24, fontSize: 12, color: 'rgba(255,255,255,0.15)', fontFamily: 'JetBrains Mono, monospace' }}>
          skillproof.dev/badge/{token} · Scores are indicative only
        </div>
      </div>
    </div>
  )
}