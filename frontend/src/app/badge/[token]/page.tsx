'use client'
import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { motion } from 'framer-motion'
import { RadarChart, Radar, PolarGrid, PolarAngleAxis, ResponsiveContainer, Tooltip } from 'recharts'
import { getBadge } from '@/lib/api'
import { parseApiError } from '@/lib/apiError'
import ErrorBanner from '@/app/components/ErrorBanner'
import SuccessBanner from '@/app/components/SuccessBanner'

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
  technicalScore?: number
  integrityAdjustedScore?: number
  executionAdjustedScore?: number
  integrityPenaltyTotal?: number
  integrityPenaltyBreakdown?: {
    pastePenalty?: number
    speedPenalty?: number
    tabSwitchPenalty?: number
    copyPenalty?: number
    coachingPatternPenalty?: number
    copyEvents?: number
    coachingPatternDetected?: number
  }
  executionBackedSignal?: {
    totalAttempts: number
    passedCount: number
    failedCount: number
    errorCount: number
    timeoutCount: number
    passRatePercent: number
    avgExecutionScore: number
    latestStatus: string
    executionPenalty: number
    executionAdjustedScore: number
    signalLevel: 'NO_SIGNAL' | 'LOW_RISK' | 'MEDIUM_RISK' | 'HIGH_RISK'
  }
  scoreByQuestionType?: Record<string, number>
  weightedScoringEnabled?: boolean
  codeWeightPercent?: number
  conceptualWeightPercent?: number
  backendScore: number
  apiDesignScore: number
  errorHandlingScore: number
  codeQualityScore: number
  documentationScore: number
  confidenceTier?: 'High' | 'Medium' | 'Low'
  tabSwitches?: number
  pasteCount?: number
  avgAnswerSeconds?: number
  repoAttemptCount?: number
  answeredCount?: number
  totalQuestions?: number
  skippedCount?: number
  followUpRequiredCount?: number
  followUpAnsweredCount?: number
  evaluationComplete?: boolean
  confidenceExplanation?: string
  questionResults?: {
    questionNumber: number
    difficulty: string
    fileReference: string
    questionText: string
    questionCodeSnippet?: string
    skipped?: boolean
    answerLength?: number
    maskedAnswerExcerpt?: string
    accuracyScore?: number
    depthScore?: number
    specificityScore?: number
    compositeScore?: number
    aiFeedback?: string
    keyPoints?: string[]
  }[]
  issuedAt: string
}

const SKILLS = [
  { key: 'backendScore', label: 'Backend Logic', color: '#D4FF00' },
  { key: 'apiDesignScore', label: 'API Design', color: '#60A5FA' },
  { key: 'errorHandlingScore', label: 'Error Handling', color: '#F59E0B' },
  { key: 'codeQualityScore', label: 'Code Quality', color: '#34D399' },
  { key: 'documentationScore', label: 'Documentation', color: '#F472B6' },
] as const

const getSkillScore = (badge: BadgeData, key: (typeof SKILLS)[number]['key']) => badge[key]

export default function BadgePage() {
  const params = useParams()
  const router = useRouter()
  const token = typeof params?.token === 'string' ? params.token : ''
  const [badge, setBadge] = useState<BadgeData | null>(null)
  const [loading, setLoading] = useState(true)
  const [copied, setCopied] = useState(false)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    if (!token) {
      setLoadError('Badge token is missing.')
      setLoading(false)
      return
    }

    getBadge(token)
      .then(res => {
        setBadge(res.data)
        setLoading(false)
      })
      .catch((err) => {
        const parsed = parseApiError(err, 'Could not load badge right now.')
        setLoadError(parsed.message)
        setLoading(false)
      })
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

  const technicalScore = badge?.technicalScore ?? badge?.overallScore ?? 0
  const adjustedScore = badge?.integrityAdjustedScore ?? badge?.overallScore ?? 0
  const executionAdjustedScore = badge?.executionAdjustedScore ?? adjustedScore
  const executionSignal = badge?.executionBackedSignal
  const codeScore = badge?.scoreByQuestionType?.CODE_GROUNDED ?? 0
  const conceptScore = badge?.scoreByQuestionType?.CONCEPTUAL ?? 0
  const conceptGap = codeScore - conceptScore
  const copyEvents = badge?.integrityPenaltyBreakdown?.copyEvents ?? 0
  const copyPenalty = badge?.integrityPenaltyBreakdown?.copyPenalty ?? 0
  const coachingPatternPenalty = badge?.integrityPenaltyBreakdown?.coachingPatternPenalty ?? 0
  const coachingPatternDetected = (badge?.integrityPenaltyBreakdown?.coachingPatternDetected ?? 0) > 0
  const integrityRiskFlag = copyEvents > 0 || copyPenalty > 0 || coachingPatternPenalty > 0 || coachingPatternDetected
  const confidenceColor = badge?.confidenceTier === 'High'
    ? '#34D399'
    : badge?.confidenceTier === 'Medium'
      ? '#F59E0B'
      : '#EF4444'

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
        {loadError && <ErrorBanner message={loadError} code="BADGE_LOAD_ERROR" compact />}
        <button onClick={() => router.push('/')}
          style={{ background: '#D4FF00', color: '#000', border: 'none', padding: '12px 28px', borderRadius: 10, fontWeight: 700, cursor: 'pointer', marginTop: 14 }}>
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
            style={{ padding: '8px 16px', background: copied ? 'rgba(52,211,153,0.15)' : 'rgba(255,255,255,0.06)', border: `1px solid ${copied ? 'rgba(52,211,153,0.4)' : 'rgba(255,255,255,0.1)'}`, borderRadius: 8, color: copied ? '#34D399' : 'rgba(255,255,255,0.6)', fontSize: 13, fontWeight: 600, cursor: 'pointer', fontFamily: 'Outfit, sans-serif' }}>
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

        {copied && <SuccessBanner message="Badge URL copied to clipboard." compact />}

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
                {badge.confidenceTier && (
                  <div title="Confidence reflects answer completeness and consistency, not just score." style={{ marginTop: 8, display: 'inline-flex', alignItems: 'center', gap: 6, padding: '4px 8px', borderRadius: 999, border: `1px solid ${confidenceColor}60`, background: `${confidenceColor}1A`, color: confidenceColor, fontSize: 11, fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>
                    Confidence: {badge.confidenceTier}
                  </div>
                )}
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: 52, fontWeight: 900, color: scoreColor, lineHeight: 1, letterSpacing: '-0.04em', fontFamily: 'JetBrains Mono, monospace' }}>{adjustedScore}</div>
                <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.25)', marginTop: 2 }}>integrity-adjusted score</div>
                <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.4)', marginTop: 4, fontFamily: 'JetBrains Mono, monospace' }}>
                  technical {technicalScore} · penalty -{badge.integrityPenaltyTotal ?? 0}
                </div>
                <div style={{ fontSize: 11, color: 'rgba(96,165,250,0.8)', marginTop: 3, fontFamily: 'JetBrains Mono, monospace' }}>
                  execution-adjusted {executionAdjustedScore}
                </div>
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

            {/* Skill breakdown — Radar Chart */}
            <div style={{ marginBottom: 24 }}>
              <h3 style={{ fontSize: 13, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.45)', marginBottom: 12, fontWeight: 600, letterSpacing: '0.08em', textTransform: 'uppercase' }}>
                Skill Breakdown
              </h3>
              
              {/* Radar Chart */}
              {badge && (
                <div style={{ background: 'rgba(212,255,0,0.02)', border: '1px solid rgba(212,255,0,0.08)', borderRadius: 16, padding: 16, marginBottom: 12 }}>
                  <div style={{ width: '100%', height: 280, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <ResponsiveContainer width="100%" height={280}>
                      <RadarChart
                        data={[
                          { skill: 'Backend', score: badge.backendScore || badge.overallScore },
                          { skill: 'API Design', score: badge.apiDesignScore || badge.overallScore },
                          { skill: 'Error Handling', score: badge.errorHandlingScore || badge.overallScore },
                          { skill: 'Code Quality', score: badge.codeQualityScore || badge.overallScore },
                          { skill: 'Documentation', score: badge.documentationScore || badge.overallScore },
                        ]}
                        margin={{ top: 10, right: 20, bottom: 10, left: 20 }}
                      >
                        <PolarGrid stroke="rgba(255,255,255,0.05)" />
                        <PolarAngleAxis
                          dataKey="skill"
                          tick={{ fill: 'rgba(255,255,255,0.4)', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}
                        />
                        <Radar
                          name="Score"
                          dataKey="score"
                          stroke="#D4FF00"
                          fill="#D4FF00"
                          fillOpacity={0.15}
                          strokeWidth={2}
                        />
                        <Tooltip
                          contentStyle={{
                            background: '#0A0A0A',
                            border: '1px solid #D4FF00',
                            borderRadius: '6px',
                            fontFamily: 'JetBrains Mono, monospace',
                            fontSize: '12px',
                            color: '#D4FF00',
                            padding: '8px 12px',
                          }}
                          formatter={(value) => [`${Math.round(Number(value) || 0)}/100`, 'Score']}
                          labelStyle={{ color: 'rgba(255,255,255,0.5)' }}
                        />
                      </RadarChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}
              
              {/* Skill score pills */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))', gap: 12 }}>
                {SKILLS.map((skill) => (
                  <div key={skill.key} style={{ padding: '12px 16px', background: 'rgba(255,255,255,0.03)', border: `1px solid ${skill.color}20`, borderRadius: 12, textAlign: 'center' }}>
                    <div style={{ fontSize: 18, fontWeight: 800, color: skill.color, fontFamily: 'JetBrains Mono, monospace', marginBottom: 4 }}>
                      {getSkillScore(badge, skill.key)}
                    </div>
                    <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.4)', lineHeight: 1.4 }}>
                      {skill.label}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Verification metadata */}
            <div style={{ padding: '14px 16px', background: 'rgba(212,255,0,0.04)', border: '1px solid rgba(212,255,0,0.12)', borderRadius: 12 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
                {[
                  { label: 'Badge Token', value: badge.verificationToken.substring(0, 20) + '...' },
                  { label: 'Algorithm', value: 'HMAC-SHA256' },
                  { label: 'Repo Attempts', value: String(badge.repoAttemptCount ?? 1) },
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

            <div style={{ marginTop: 14, padding: '14px 16px', background: 'rgba(96,165,250,0.06)', border: '1px solid rgba(96,165,250,0.2)', borderRadius: 12 }}>
              <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(96,165,250,0.85)', marginBottom: 8, letterSpacing: '0.08em' }}>
                INTEGRITY SIGNALS
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 10 }}>
                <div>
                  <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Tab switches</div>
                  <div style={{ fontSize: 14, color: '#60A5FA', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{badge.tabSwitches ?? 0}</div>
                </div>
                <div>
                  <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Paste count</div>
                  <div style={{ fontSize: 14, color: '#60A5FA', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{badge.pasteCount ?? 0}</div>
                </div>
                <div>
                  <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Copy events</div>
                  <div style={{ fontSize: 14, color: '#60A5FA', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{copyEvents}</div>
                </div>
                <div>
                  <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Avg answer time</div>
                  <div style={{ fontSize: 14, color: '#60A5FA', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{badge.avgAnswerSeconds ?? 0}s</div>
                </div>
              </div>
            </div>

            <div style={{ marginTop: 14, padding: '14px 16px', background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.22)', borderRadius: 12 }}>
              <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(110,231,183,0.9)', marginBottom: 8, letterSpacing: '0.08em' }}>
                EXECUTION-BACKED EVIDENCE
              </div>
              {executionSignal && executionSignal.totalAttempts > 0 ? (
                <>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 10 }}>
                    <div>
                      <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Attempts</div>
                      <div style={{ fontSize: 14, color: '#6EE7B7', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{executionSignal.totalAttempts}</div>
                    </div>
                    <div>
                      <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Pass rate</div>
                      <div style={{ fontSize: 14, color: '#6EE7B7', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{executionSignal.passRatePercent}%</div>
                    </div>
                    <div>
                      <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Latest status</div>
                      <div style={{ fontSize: 14, color: '#6EE7B7', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{executionSignal.latestStatus}</div>
                    </div>
                    <div>
                      <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Risk level</div>
                      <div style={{ fontSize: 14, color: '#6EE7B7', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{executionSignal.signalLevel}</div>
                    </div>
                  </div>
                  <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(255,255,255,0.56)', fontFamily: 'JetBrains Mono, monospace' }}>
                    pass {executionSignal.passedCount} · fail {executionSignal.failedCount} · error {executionSignal.errorCount} · timeout {executionSignal.timeoutCount}
                  </div>
                  <div style={{ marginTop: 6, fontSize: 12, color: 'rgba(255,255,255,0.56)', fontFamily: 'JetBrains Mono, monospace' }}>
                    execution penalty -{executionSignal.executionPenalty} · adjusted with execution {executionAdjustedScore}
                  </div>
                </>
              ) : (
                <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.6)', lineHeight: 1.6 }}>
                  No recent coding-challenge submissions available. This badge currently reflects repository Q/A evidence and integrity telemetry only.
                </div>
              )}
            </div>

            {integrityRiskFlag && (
              <div style={{ marginTop: 14, padding: '14px 16px', background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.24)', borderRadius: 12 }}>
                <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(248,113,113,0.9)', marginBottom: 8, letterSpacing: '0.08em' }}>
                  INTEGRITY ALERT
                </div>
                <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.68)', lineHeight: 1.6 }}>
                  {copyEvents > 0 && `Question/context copy activity detected (${copyEvents}). `}
                  {coachingPatternDetected && 'Answer pattern shows high-accuracy but shallow-depth responses across multiple questions. '}
                  Recruiters should require a live explanation before making a decision.
                </div>
              </div>
            )}

            {!!badge.scoreByQuestionType && (
              <div style={{ marginTop: 14, padding: '14px 16px', background: 'rgba(96,165,250,0.06)', border: '1px solid rgba(96,165,250,0.2)', borderRadius: 12 }}>
                <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(96,165,250,0.85)', marginBottom: 8, letterSpacing: '0.08em' }}>
                  CODE VS CONCEPT
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                  <div>
                    <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Code-grounded</div>
                    <div style={{ fontSize: 14, color: '#60A5FA', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{codeScore}</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Conceptual</div>
                    <div style={{ fontSize: 14, color: '#60A5FA', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{conceptScore}</div>
                  </div>
                </div>
                {conceptGap >= 15 && (
                  <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(245,158,11,0.9)' }}>
                    Recruiters should probe architectural trade-offs and failure-mode reasoning in follow-up.
                  </div>
                )}
              </div>
            )}

            {typeof badge.followUpRequiredCount === 'number' && badge.followUpRequiredCount > 0 && (
              <div style={{ marginTop: 14, padding: '14px 16px', background: 'rgba(245,158,11,0.08)', border: '1px solid rgba(245,158,11,0.2)', borderRadius: 12 }}>
                <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(245,158,11,0.9)', marginBottom: 8, letterSpacing: '0.08em' }}>
                  FOLLOW-UP COMPLETION
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                  <div>
                    <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Required</div>
                    <div style={{ fontSize: 14, color: '#F59E0B', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{badge.followUpRequiredCount}</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginBottom: 4 }}>Answered</div>
                    <div style={{ fontSize: 14, color: '#F59E0B', fontFamily: 'JetBrains Mono, monospace', fontWeight: 700 }}>{badge.followUpAnsweredCount ?? 0}</div>
                  </div>
                </div>
              </div>
            )}

            <div style={{ marginTop: 14, padding: '14px 16px', background: 'rgba(212,255,0,0.05)', border: '1px solid rgba(212,255,0,0.2)', borderRadius: 12 }}>
              <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(212,255,0,0.85)', marginBottom: 8, letterSpacing: '0.08em' }}>
                HOW THIS SCORE WAS CALCULATED
              </div>
              <p style={{ margin: 0, color: 'rgba(255,255,255,0.58)', fontSize: 13, lineHeight: 1.6 }}>
                Answers were evaluated against repository code context using rubric dimensions: accuracy, depth, and code specificity.
                Recruiters can review per-question evidence below; this score is a strong screening signal, not a standalone hiring decision.
              </p>
              <div style={{ marginTop: 10, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <span style={{ padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(96,165,250,0.35)', background: 'rgba(96,165,250,0.1)', color: '#60A5FA', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}>
                  Answered: {badge.answeredCount ?? 0}/{badge.totalQuestions ?? 0}
                </span>
                <span style={{ padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(245,158,11,0.35)', background: 'rgba(245,158,11,0.1)', color: '#F59E0B', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}>
                  Skipped: {badge.skippedCount ?? 0}
                </span>
                <span style={{ padding: '4px 8px', borderRadius: 999, border: `1px solid ${(badge.evaluationComplete ? '#34D399' : '#EF4444')}55`, background: `${(badge.evaluationComplete ? '#34D399' : '#EF4444')}15`, color: badge.evaluationComplete ? '#34D399' : '#EF4444', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}>
                  Evaluation: {badge.evaluationComplete ? 'Complete' : 'Incomplete'}
                </span>
                <span style={{ padding: '4px 8px', borderRadius: 999, border: '1px solid rgba(245,158,11,0.35)', background: 'rgba(245,158,11,0.1)', color: '#F59E0B', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}>
                  Integrity penalty: -{badge.integrityPenaltyTotal ?? 0}
                </span>
              </div>
              {badge.integrityPenaltyBreakdown && (
                <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(255,255,255,0.52)', fontFamily: 'JetBrains Mono, monospace' }}>
                  paste {badge.integrityPenaltyBreakdown.pastePenalty ?? 0} · copy {badge.integrityPenaltyBreakdown.copyPenalty ?? 0} · speed {badge.integrityPenaltyBreakdown.speedPenalty ?? 0} · tab {badge.integrityPenaltyBreakdown.tabSwitchPenalty ?? 0} · pattern {badge.integrityPenaltyBreakdown.coachingPatternPenalty ?? 0}
                </div>
              )}
              {badge.confidenceExplanation && (
                <p style={{ marginTop: 10, marginBottom: 0, color: 'rgba(255,255,255,0.52)', fontSize: 12, lineHeight: 1.5 }}>
                  {badge.confidenceExplanation}
                </p>
              )}
              <p style={{ marginTop: 10, marginBottom: 0, color: 'rgba(255,255,255,0.52)', fontSize: 12, lineHeight: 1.5, fontFamily: 'JetBrains Mono, monospace' }}>
                Scoring mode: {badge.weightedScoringEnabled ? 'weighted' : 'equal'} {badge.weightedScoringEnabled ? `(${badge.codeWeightPercent ?? 60}% code · ${badge.conceptualWeightPercent ?? 40}% concept)` : '(per-question average)'}
              </p>
            </div>

            {badge.questionResults && badge.questionResults.length > 0 && (
              <div style={{ marginTop: 14, padding: '14px 16px', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 12 }}>
                <div style={{ fontSize: 10, fontFamily: 'JetBrains Mono, monospace', color: 'rgba(255,255,255,0.75)', marginBottom: 10, letterSpacing: '0.08em' }}>
                  QUESTION EVIDENCE
                </div>
                <div style={{ display: 'grid', gap: 10 }}>
                  {badge.questionResults.map((q) => (
                    <div key={q.questionNumber} style={{ border: '1px solid rgba(255,255,255,0.08)', borderRadius: 10, padding: 12, background: 'rgba(0,0,0,0.2)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                        <div style={{ fontSize: 13, fontWeight: 700 }}>Q{q.questionNumber}: {q.questionText}</div>
                        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                          <span style={{ fontSize: 11, color: 'rgba(255,255,255,0.6)', border: '1px solid rgba(255,255,255,0.15)', borderRadius: 999, padding: '2px 8px' }}>{q.fileReference}</span>
                          <span style={{ fontSize: 11, color: q.difficulty === 'EASY' ? '#34D399' : q.difficulty === 'MEDIUM' ? '#F59E0B' : '#F472B6', border: '1px solid rgba(255,255,255,0.2)', borderRadius: 999, padding: '2px 8px' }}>{q.difficulty}</span>
                          {q.skipped && (
                            <span style={{ fontSize: 11, color: '#F59E0B', border: '1px solid rgba(245,158,11,0.4)', borderRadius: 999, padding: '2px 8px' }}>Skipped</span>
                          )}
                        </div>
                      </div>

                      <div style={{ marginTop: 8, display: 'grid', gridTemplateColumns: 'repeat(4,minmax(0,1fr))', gap: 6 }}>
                        <div style={{ padding: 7, borderRadius: 8, background: 'rgba(255,255,255,0.03)', fontSize: 12, fontFamily: 'JetBrains Mono, monospace' }}>A {q.accuracyScore ?? 0}/10</div>
                        <div style={{ padding: 7, borderRadius: 8, background: 'rgba(255,255,255,0.03)', fontSize: 12, fontFamily: 'JetBrains Mono, monospace' }}>D {q.depthScore ?? 0}/10</div>
                        <div style={{ padding: 7, borderRadius: 8, background: 'rgba(255,255,255,0.03)', fontSize: 12, fontFamily: 'JetBrains Mono, monospace' }}>S {q.specificityScore ?? 0}/10</div>
                        <div style={{ padding: 7, borderRadius: 8, background: 'rgba(255,255,255,0.03)', fontSize: 12, fontFamily: 'JetBrains Mono, monospace' }}>Len {q.answerLength ?? 0}</div>
                      </div>

                      <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(255,255,255,0.55)' }}>
                        <b>Answer excerpt:</b> {q.maskedAnswerExcerpt || (q.skipped ? 'Question skipped by candidate.' : 'Not available.')}
                      </div>

                      {q.questionCodeSnippet && (
                        <div style={{ marginTop: 8, border: '1px solid rgba(96,165,250,0.2)', borderRadius: 8, overflow: 'hidden' }}>
                          <div style={{ padding: '6px 10px', borderBottom: '1px solid rgba(96,165,250,0.18)', fontSize: 10, color: 'rgba(147,197,253,0.88)', letterSpacing: '0.08em', fontFamily: 'JetBrains Mono, monospace' }}>
                            CODE CONTEXT
                          </div>
                          <pre style={{ margin: 0, padding: '10px 12px', maxHeight: 180, overflow: 'auto', fontFamily: 'JetBrains Mono, monospace', fontSize: 11, lineHeight: 1.45, color: 'rgba(226,232,240,0.9)', background: 'rgba(2,6,23,0.5)' }}>
                            {q.questionCodeSnippet}
                          </pre>
                        </div>
                      )}

                      {q.keyPoints && q.keyPoints.length > 0 && (
                        <ul style={{ marginTop: 8, marginBottom: 0, paddingLeft: 18, color: 'rgba(255,255,255,0.62)', fontSize: 12, lineHeight: 1.5 }}>
                          {q.keyPoints.map((point, index) => (
                            <li key={index}>{point}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
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