'use client'
import { useEffect, useRef, useState, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import {
  motion, useScroll, useTransform, useInView,
  AnimatePresence, useMotionValue, useSpring
} from 'framer-motion'
import { getGitHubAuthUrl } from '@/lib/api'
import { OrbitalPipeline } from './components/OrbitalPipeline'

// ── Lenis smooth scroll ──────────────────────────────────────────────────────
function useLenis() {
  useEffect(() => {
    let lenis: any
    import('@studio-freight/lenis').then(({ default: Lenis }) => {
      lenis = new Lenis({ lerp: 0.00, wheelMultiplier:0.0, smoothWheel:false, syncTouch:false })
      const raf = (t: number) => { lenis.raf(t); requestAnimationFrame(raf) }
      requestAnimationFrame(raf)
    })
    return () => lenis?.destroy()
  }, [])
}

// ── Animated counter ─────────────────────────────────────────────────────────
function AnimCounter({ target, suffix = '', duration = 1800 }: { target: number; suffix?: string; duration?: number }) {
  const ref = useRef<HTMLSpanElement>(null)
  const inView = useInView(ref, { once: true })
  const [val, setVal] = useState(0)
  useEffect(() => {
    if (!inView) return
    const start = performance.now()
    const tick = (now: number) => {
      const p = Math.min((now - start) / duration, 1)
      const ease = 1 - Math.pow(1 - p, 3)
      setVal(Math.round(ease * target))
      if (p < 1) requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
  }, [inView, target, duration])
  return <span ref={ref}>{val}{suffix}</span>
}

// ── Text scramble hook ───────────────────────────────────────────────────────
function useScramble(text: string, active: boolean) {
  const [display, setDisplay] = useState(text)
  const CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&'
  useEffect(() => {
    if (!active) { setDisplay(text); return }
    let iter = 0
    const interval = setInterval(() => {
      setDisplay(text.split('').map((ch, i) => {
        if (i < iter) return ch
        if (ch === ' ') return ' '
        return CHARS[Math.floor(Math.random() * CHARS.length)]
      }).join(''))
      iter += 0.5
      if (iter >= text.length) clearInterval(interval)
    }, 30)
    return () => clearInterval(interval)
  }, [active, text])
  return display
}

// ── Fade-up wrapper ──────────────────────────────────────────────────────────
function FadeUp({ children, delay = 0, className = '' }: any) {
  const ref = useRef(null)
  const inView = useInView(ref, { once: true, margin: '-60px' })
  return (
    <motion.div ref={ref}
      initial={{ opacity: 0, y: 40 }}
      animate={inView ? { opacity: 1, y: 0 } : {}}
      transition={{ duration: 0.7, delay, ease: [0.22, 1, 0.36, 1] }}
      className={className}>
      {children}
    </motion.div>
  )
}

// ── Magnetic button ──────────────────────────────────────────────────────────
function MagneticBtn({ children, onClick, style, className }: any) {
  const ref = useRef<HTMLButtonElement>(null)
  const x = useMotionValue(0)
  const y = useMotionValue(0)
  const sx = useSpring(x, { stiffness: 200, damping: 20 })
  const sy = useSpring(y, { stiffness: 200, damping: 20 })
  const handleMove = (e: React.MouseEvent) => {
    const rect = ref.current!.getBoundingClientRect()
    x.set((e.clientX - rect.left - rect.width / 2) * 0.3)
    y.set((e.clientY - rect.top - rect.height / 2) * 0.3)
  }
  const handleLeave = () => { x.set(0); y.set(0) }
  return (
    <motion.button ref={ref} style={{ ...style, x: sx, y: sy }}
      className={className} onClick={onClick}
      onMouseMove={handleMove} onMouseLeave={handleLeave}
      whileTap={{ scale: 0.96 }}>
      {children}
    </motion.button>
  )
}

// ── Spotlight card ───────────────────────────────────────────────────────────
function SpotlightCard({ children, className = '', accentColor = 'rgba(212,255,0,0.07)' }: any) {
  const ref = useRef<HTMLDivElement>(null)
  const [spot, setSpot] = useState({ x: -999, y: -999, opacity: 0 })
  const handleMove = (e: React.MouseEvent) => {
    const rect = ref.current?.getBoundingClientRect()
    if (!rect) return
    setSpot({ x: e.clientX - rect.left, y: e.clientY - rect.top, opacity: 1 })
  }
  return (
    <div ref={ref} onMouseMove={handleMove} onMouseLeave={() => setSpot(s => ({ ...s, opacity: 0 }))}
      className={`relative overflow-hidden ${className}`}
      style={{ background: '#0D0D0D', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 20 }}>
      <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none', transition: 'opacity 0.3s', opacity: spot.opacity,
        background: `radial-gradient(350px circle at ${spot.x}px ${spot.y}px, ${accentColor}, transparent 60%)` }} />
      {children}
    </div>
  )
}

// ── Tilt card (3D hover) ─────────────────────────────────────────────────────
function TiltCard({ children, style }: any) {
  const ref = useRef<HTMLDivElement>(null)
  const rotX = useMotionValue(0)
  const rotY = useMotionValue(0)
  const sRotX = useSpring(rotX, { stiffness: 150, damping: 20 })
  const sRotY = useSpring(rotY, { stiffness: 150, damping: 20 })
  const handleMove = (e: React.MouseEvent) => {
    const rect = ref.current!.getBoundingClientRect()
    const nx = (e.clientX - rect.left) / rect.width - 0.5
    const ny = (e.clientY - rect.top) / rect.height - 0.5
    rotX.set(-ny * 12)
    rotY.set(nx * 12)
  }
  return (
    <motion.div ref={ref} style={{ ...style, rotateX: sRotX, rotateY: sRotY, transformStyle: 'preserve-3d', transformPerspective: 1000 }}
      onMouseMove={handleMove} onMouseLeave={() => { rotX.set(0); rotY.set(0) }}>
      {children}
    </motion.div>
  )
}

// ── Marquee ──────────────────────────────────────────────────────────────────
const MARQUEE_ITEMS = [
  '⚡ Portfolio Verified', '✦ 10K+ Developers', '◈ Code-Grounded AI', '⬡ Docker Sandbox',
  '◎ Peer Review', '◉ Skill Gaps', '⬢ Hiring Pipeline', '✦ 500+ Companies',
  '⚡ 2min to Badge', '◈ No Generic Tests', '◎ HMAC Signed', '⬡ Real Code Only',
]
function Marquee({ reverse = false }) {
  const items = [...MARQUEE_ITEMS, ...MARQUEE_ITEMS]
  return (
    <div style={{ overflow: 'hidden', maskImage: 'linear-gradient(90deg,transparent,black 8%,black 92%,transparent)', WebkitMaskImage: 'linear-gradient(90deg,transparent,black 8%,black 92%,transparent)' }}>
      <div className={reverse ? 'marquee-right' : 'marquee-left'} style={{ display: 'flex', gap: 32, width: 'max-content' }}>
        {items.map((item, i) => (
          <span key={i} style={{ whiteSpace: 'nowrap', fontSize: 12, fontFamily: 'JetBrains Mono,monospace', color: i % 4 === 0 ? '#D4FF00' : 'rgba(255,255,255,0.25)', fontWeight: 600, letterSpacing: '0.05em' }}>
            {item}
          </span>
        ))}
      </div>
    </div>
  )
}

// ── FAQ Item ─────────────────────────────────────────────────────────────────
function FAQItem({ q, a }: { q: string; a: string }) {
  const [open, setOpen] = useState(false)
  return (
    <motion.div onClick={() => setOpen(!open)} whileHover={{ x: 4 }}
      style={{ borderBottom: '1px solid rgba(255,255,255,0.06)', padding: '22px 0', cursor: 'pointer' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
        <span style={{ fontSize: 16, fontWeight: 600, color: open ? '#D4FF00' : 'white', transition: 'color 0.2s', lineHeight: 1.4 }}>{q}</span>
        <motion.div animate={{ rotate: open ? 45 : 0 }} transition={{ duration: 0.25 }}
          style={{ width: 28, height: 28, borderRadius: 8, background: open ? '#D4FF00' : 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontSize: 18, color: open ? '#000' : '#D4FF00', lineHeight: 1, transition: 'background 0.2s' }}>+</motion.div>
      </div>
      <AnimatePresence>
        {open && (
          <motion.p initial={{ height: 0, opacity: 0, marginTop: 0 }} animate={{ height: 'auto', opacity: 1, marginTop: 14 }}
            exit={{ height: 0, opacity: 0, marginTop: 0 }} transition={{ duration: 0.3, ease: 'easeOut' }}
            style={{ overflow: 'hidden', color: 'rgba(255,255,255,0.45)', fontSize: 15, lineHeight: 1.75, margin: 0 }}>
            {a}
          </motion.p>
        )}
      </AnimatePresence>
    </motion.div>
  )
}

// ── Hero terminal (live pipeline log) ───────────────────────────────────────
const TERMINAL_LINES = [
  { t: '$ skillproof verify --repo=automl', c: '#D4FF00' },
  { t: '✓ GitHub OAuth token exchanged (287ms)', c: '#34D399' },
  { t: '✓ 93 files fetched → 15 relevant', c: '#34D399' },
  { t: '✓ Code summary: 30,609 chars extracted', c: '#34D399' },
  { t: '⟳ Generating 5 grounded questions...', c: '#A78BFA' },
  { t: '✓ Questions grounded in middleware.ts', c: '#34D399' },
  { t: '✓ Scores: 8.2 / 7.6 / 9.0 / 7.1 / 8.4', c: '#34D399' },
  { t: '✓ Badge signed: sp_1baa55015d...', c: '#D4FF00' },
]

function HeroTerminal() {
  const [visible, setVisible] = useState(0)
  useEffect(() => {
    if (visible >= TERMINAL_LINES.length) return
    const t = setTimeout(() => setVisible(v => v + 1), visible === 0 ? 800 : 400 + Math.random() * 300)
    return () => clearTimeout(t)
  }, [visible])
  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 1.2, duration: 0.6 }}
      style={{ background: '#080808', border: '1px solid rgba(212,255,0,0.12)', borderRadius: 16, padding: '18px 22px', fontFamily: 'JetBrains Mono,monospace', fontSize: 12, lineHeight: 1.8, maxWidth: 500, margin: '0 auto', textAlign: 'left', boxShadow: '0 0 60px rgba(212,255,0,0.04)' }}>
      <div style={{ display: 'flex', gap: 6, marginBottom: 14, alignItems: 'center' }}>
        <span style={{ width: 10, height: 10, borderRadius: '50%', background: '#ff5f56' }} />
        <span style={{ width: 10, height: 10, borderRadius: '50%', background: '#ffbd2e' }} />
        <span style={{ width: 10, height: 10, borderRadius: '50%', background: '#27c93f' }} />
        <span style={{ marginLeft: 10, color: 'rgba(255,255,255,0.15)', fontSize: 11 }}>pipeline.log</span>
        <span style={{ marginLeft: 'auto', width: 6, height: 6, borderRadius: '50%', background: '#27c93f', animation: 'pulse-lime 1.5s infinite' }} />
      </div>
      {TERMINAL_LINES.slice(0, visible).map((line, i) => (
        <motion.div key={i} initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: 0.3 }}
          style={{ color: line.c, display: 'flex', gap: 8 }}>
          <span style={{ color: 'rgba(255,255,255,0.15)', userSelect: 'none' }}>{String(i + 1).padStart(2, '0')}</span>
          {line.t}
        </motion.div>
      ))}
      {visible < TERMINAL_LINES.length && (
        <span style={{ display: 'inline-block', width: 8, height: 14, background: '#D4FF00', animation: 'pulse-lime 1s infinite', verticalAlign: 'middle' }} />
      )}
    </motion.div>
  )
}

// ── Gradient border wrapper ──────────────────────────────────────────────────
function GradBorder({ children, colors = ['#D4FF00', '#34D399', '#60A5FA'], style }: any) {
  return (
    <div style={{ position: 'relative', borderRadius: 22, padding: 1.5, background: `linear-gradient(135deg,${colors.join(',')})`, ...style }}>
      <div style={{ background: '#0D0D0D', borderRadius: 21, height: '100%' }}>
        {children}
      </div>
    </div>
  )
}

// ── Social proof strip ───────────────────────────────────────────────────────
const TESTIMONIALS = [
  { name: 'Aditya R.', role: 'SWE @ Razorpay', text: 'Got the interview in 48hrs of sharing my badge. Recruiter said it was the clearest signal she had seen.', score: 89 },
  { name: 'Sneha K.', role: 'Backend Dev', text: 'The questions were genuinely about my code. No way to fake it. Shows you actually understand your own project.', score: 84 },
  { name: 'Mohammed F.', role: 'Fullstack @ Zomato', text: 'Badges cut my application response rate from 4% to 23%. The HMAC verification gives companies confidence.', score: 91 },
  { name: 'Priya V.', role: 'ML Engineer', text: 'Better than any coding test. They ask about my actual architecture decisions, not LeetCode puzzles.', score: 78 },
  { name: 'Karan M.', role: 'DevOps Eng', text: 'The gap scanner found three real issues in my repo I hadn\'t noticed. Fixed them, rescore went up 11 points.', score: 86 },
  { name: 'Divya S.', role: 'React Dev', text: 'Companies stopped ghosting after I added the badge. Something verifiable in your portfolio changes everything.', score: 82 },
]

function TestimonialCard({ t }: { t: typeof TESTIMONIALS[0] }) {
  const [hovered, setHovered] = useState(false)
  const scrambled = useScramble(t.name, hovered)
  return (
    <motion.div whileHover={{ y: -6, borderColor: 'rgba(212,255,0,0.2)' }}
      onHoverStart={() => setHovered(true)} onHoverEnd={() => setHovered(false)}
      style={{ background: '#0A0A0A', border: '1px solid rgba(255,255,255,0.06)', borderRadius: 18, padding: 24, minWidth: 280, flexShrink: 0, cursor: 'default', transition: 'border-color 0.3s' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <div style={{ width: 38, height: 38, borderRadius: 10, background: 'rgba(212,255,0,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, color: '#D4FF00', fontSize: 14 }}>
          {t.name.split(' ').map(n => n[0]).join('')}
        </div>
        <div>
          <div style={{ fontWeight: 700, fontSize: 14, fontFamily: 'JetBrains Mono,monospace', color: '#D4FF00' }}>{scrambled}</div>
          <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.3)', marginTop: 1 }}>{t.role}</div>
        </div>
        <div style={{ marginLeft: 'auto', fontSize: 22, fontWeight: 900, color: '#D4FF00', letterSpacing: '-0.03em', fontFamily: 'JetBrains Mono,monospace' }}>{t.score}</div>
      </div>
      <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.45)', lineHeight: 1.7, margin: 0 }}>"{t.text}"</p>
    </motion.div>
  )
}

// ── MAIN PAGE ─────────────────────────────────────────────────────────────────
export default function LandingPage() {
  useLenis()
  const [loading, setLoading] = useState(false)
  const [navScrolled, setNavScrolled] = useState(false)
  const mouseX = useMotionValue(0)
  const mouseY = useMotionValue(0)
  const springX = useSpring(mouseX, { stiffness: 60, damping: 18 })
  const springY = useSpring(mouseY, { stiffness: 60, damping: 18 })

  const heroRef = useRef<HTMLElement>(null)
  const { scrollYProgress } = useScroll({ target: heroRef })
  const heroY = useTransform(scrollYProgress, [0, 1], [0, -100])
  const heroOpacity = useTransform(scrollYProgress, [0, 0.65], [1, 0])

  useEffect(() => {
    const onScroll = () => setNavScrolled(window.scrollY > 40)
    const onMouse = (e: MouseEvent) => { mouseX.set(e.clientX); mouseY.set(e.clientY) }
    window.addEventListener('scroll', onScroll)
    window.addEventListener('mousemove', onMouse)
    return () => { window.removeEventListener('scroll', onScroll); window.removeEventListener('mousemove', onMouse) }
  }, [])

  const handleLogin = async () => {
    setLoading(true)
    try {
      console.log('Fetching GitHub auth URL from /api/auth/github...')
      const res = await getGitHubAuthUrl()
      console.log('Response:', res.data)
      if (res.data?.url) {
        console.log('Redirecting to:', res.data.url)
        window.location.href = res.data.url
      } else {
        console.error('No URL in response:', res.data)
        setLoading(false)
      }
    } catch (err: any) {
      console.error('GitHub auth error:', err.response?.status, err.response?.data || err.message)
      setLoading(false)
    }
  }

  return (
    <div style={{ background: '#000', color: '#fff', fontFamily: 'Outfit,sans-serif', overflowX: 'hidden' }}>

      {/* Cursor glow */}
      <motion.div style={{ position: 'fixed', width: 500, height: 500, borderRadius: '50%', background: 'radial-gradient(circle, rgba(212,255,0,0.05) 0%, transparent 70%)', pointerEvents: 'none', zIndex: 9998, x: springX, y: springY, translateX: '-50%', translateY: '-50%' }} />

      {/* ── NAVBAR ───────────────────────────────────────────────────────────── */}
      <motion.nav
        animate={{ backdropFilter: navScrolled ? 'blur(24px)' : 'blur(0px)', backgroundColor: navScrolled ? 'rgba(0,0,0,0.88)' : 'transparent' }}
        transition={{ duration: 0.3 }}
        style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 1000, borderBottom: navScrolled ? '1px solid rgba(255,255,255,0.07)' : '1px solid transparent' }}>
        {navScrolled && <div style={{ position: 'absolute', bottom: -1, left: 0, right: 0, height: 1, background: 'linear-gradient(90deg,transparent,rgba(212,255,0,0.35),transparent)' }} />}
        <div style={{ maxWidth: 1200, margin: '0 auto', padding: '0 24px', height: 64, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <motion.div whileHover={{ scale: 1.05 }} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}>
            <div style={{ width: 34, height: 34, borderRadius: 9, background: '#D4FF00', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'JetBrains Mono,monospace', fontWeight: 800, fontSize: 11, color: '#000', letterSpacing: '0.05em' }}>SP</div>
            <span style={{ fontWeight: 900, fontSize: 17, letterSpacing: '-0.03em' }}>SkillProof</span>
          </motion.div>
          <div style={{ display: 'flex', gap: 32, alignItems: 'center' }}>
            {['How It Works', 'Features', 'Pricing', 'For Companies'].map(item => (
              <a key={item} href={`#${item.toLowerCase().replace(/ /g, '-')}`}
                style={{ color: 'rgba(255,255,255,0.4)', fontSize: 14, textDecoration: 'none', fontWeight: 500, transition: 'color 0.2s', position: 'relative' }}
                onMouseEnter={e => (e.currentTarget.style.color = '#D4FF00')}
                onMouseLeave={e => (e.currentTarget.style.color = 'rgba(255,255,255,0.4)')}>{item}</a>
            ))}
          </div>
          <MagneticBtn onClick={handleLogin}
            style={{ background: '#D4FF00', color: '#000', border: 'none', padding: '10px 22px', borderRadius: 10, fontWeight: 800, fontSize: 14, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8, fontFamily: 'Outfit,sans-serif' }}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
            {loading ? 'Loading…' : 'Login with GitHub'}
          </MagneticBtn>
        </div>
      </motion.nav>

      {/* ── HERO ─────────────────────────────────────────────────────────────── */}
      <section ref={heroRef} style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '130px 24px 80px', position: 'relative', overflow: 'hidden' }}>
        {/* Animated dot-grid background */}
        <div style={{ position: 'absolute', inset: 0, backgroundImage: 'radial-gradient(rgba(212,255,0,0.08) 1px,transparent 1px)', backgroundSize: '32px 32px', maskImage: 'radial-gradient(ellipse 80% 70% at 50% 50%,black 40%,transparent 100%)', WebkitMaskImage: 'radial-gradient(ellipse 80% 70% at 50% 50%,black 40%,transparent 100%)' }} />
        {/* Lime core glow */}
        <div style={{ position: 'absolute', top: '35%', left: '50%', transform: 'translate(-50%,-50%)', width: 700, height: 400, background: 'radial-gradient(ellipse,rgba(212,255,0,0.07) 0%,transparent 65%)', pointerEvents: 'none' }} />
        {/* Scan line */}
        <motion.div animate={{ y: [-20, typeof window !== 'undefined' ? window.innerHeight + 20 : 900] }} transition={{ duration: 6, repeat: Infinity, ease: 'linear', repeatDelay: 4 }}
          style={{ position: 'absolute', left: 0, right: 0, height: 1, background: 'linear-gradient(90deg,transparent,rgba(212,255,0,0.15),transparent)', pointerEvents: 'none', zIndex: 1 }} />

        <motion.div style={{ y: heroY, opacity: heroOpacity, position: 'relative', zIndex: 10, textAlign: 'center', maxWidth: 980 }}>
          {/* Live badge */}
          <motion.div initial={{ opacity: 0, scale: 0.85 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.5 }}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 18px', borderRadius: 100, background: 'rgba(212,255,0,0.08)', border: '1px solid rgba(212,255,0,0.22)', marginBottom: 44 }}>
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#D4FF00', display: 'inline-block' }}>
              <style>{`@keyframes pulse-lime{0%,100%{opacity:1}50%{opacity:.3}}`}</style>
            </span>
            <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', fontWeight: 600, letterSpacing: '0.08em' }}>LIVE · AI-Powered Developer Credibility Platform</span>
          </motion.div>

          {/* Headline */}
          <motion.h1 initial={{ opacity: 0, y: 60 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 1, delay: 0.1, ease: [0.22, 1, 0.36, 1] }}
            style={{ fontSize: 'clamp(54px,9vw,104px)', fontWeight: 900, lineHeight: 0.93, letterSpacing: '-0.045em', marginBottom: 32 }}>
            GitHub shows<br />
            <span style={{ color: 'rgba(255,255,255,0.18)', fontStyle: 'italic' }}>what</span> you built.<br />
            <span style={{ color: '#D4FF00', position: 'relative' }}>
              We prove
              <motion.span animate={{ scaleX: [0, 1] }} transition={{ duration: 0.6, delay: 0.8 }}
                style={{ position: 'absolute', bottom: -4, left: 0, right: 0, height: 4, background: '#D4FF00', borderRadius: 2, transformOrigin: 'left', display: 'block' }} />
            </span> you<br />
            understand it.
          </motion.h1>

          {/* Subtext */}
          <motion.p initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7, delay: 0.3, ease: [0.22, 1, 0.36, 1] }}
            style={{ fontSize: 18, color: 'rgba(255,255,255,0.4)', maxWidth: 500, margin: '0 auto 52px', lineHeight: 1.65, fontWeight: 400 }}>
            AI questions grounded in your actual code.<br />Verified badges recruiters trust. No generic tests.
          </motion.p>

          {/* CTAs */}
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.45 }}
            style={{ display: 'flex', gap: 14, justifyContent: 'center', flexWrap: 'wrap', marginBottom: 60 }}>
            <MagneticBtn onClick={handleLogin} className="shimmer-btn"
              style={{ background: '#D4FF00', color: '#000', border: 'none', padding: '17px 38px', borderRadius: 14, fontWeight: 900, fontSize: 16, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, fontFamily: 'Outfit,sans-serif', position: 'relative', overflow: 'hidden', boxShadow: '0 0 40px rgba(212,255,0,0.2)' }}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
              Verify My Portfolio Free
            </MagneticBtn>
            <motion.button whileHover={{ borderColor: 'rgba(212,255,0,0.5)', color: '#fff' }} whileTap={{ scale: 0.97 }}
              onClick={() => document.getElementById('how-it-works')?.scrollIntoView({ behavior: 'smooth' })}
              style={{ background: 'transparent', color: 'rgba(255,255,255,0.6)', border: '1px solid rgba(255,255,255,0.12)', padding: '17px 32px', borderRadius: 14, fontWeight: 600, fontSize: 16, cursor: 'pointer', fontFamily: 'Outfit,sans-serif', transition: 'border-color 0.2s, color 0.2s' }}>
              See How It Works ↓
            </motion.button>
          </motion.div>

          {/* Live terminal */}
          <HeroTerminal />

          {/* Animated stats */}
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.8, delay: 0.7 }}
            style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 52, flexWrap: 'wrap' }}>
            {[
              { n: 10000, suffix: '+', l: 'Developers' },
              { n: 500, suffix: '+', l: 'Companies' },
              { n: 94, suffix: '%', l: 'Accuracy' },
              { n: 2, suffix: 'min', l: 'To Badge' },
            ].map((s, i) => (
              <motion.div key={i} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.8 + i * 0.08 }}
                style={{ padding: '14px 26px', borderRadius: 14, background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', textAlign: 'center', minWidth: 110 }}>
                <div style={{ fontSize: 28, fontWeight: 900, color: '#D4FF00', letterSpacing: '-0.04em', fontFamily: 'JetBrains Mono,monospace' }}>
                  <AnimCounter target={s.n} suffix={s.suffix} />
                </div>
                <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.3)', marginTop: 3, fontFamily: 'JetBrains Mono,monospace', letterSpacing: '0.08em' }}>{s.l}</div>
              </motion.div>
            ))}
          </motion.div>
        </motion.div>

        {/* Scroll indicator */}
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 2.5 }}
          style={{ position: 'absolute', bottom: 36, left: '50%', transform: 'translateX(-50%)', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: 11, fontFamily: 'JetBrains Mono,monospace', color: 'rgba(255,255,255,0.2)', letterSpacing: '0.15em' }}>SCROLL</span>
          <motion.div animate={{ y: [0, 10, 0] }} transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}
            style={{ width: 1, height: 40, background: 'linear-gradient(to bottom,rgba(212,255,0,0.4),transparent)' }} />
        </motion.div>
      </section>

      {/* ── FLOATING BADGE PREVIEW ───────────────────────────────────────────── */}
      <section style={{ padding: '80px 24px 100px', maxWidth: 860, margin: '0 auto', textAlign: 'center' }}>
        <FadeUp>
          <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase' }}>// SAMPLE BADGE</span>
          <h2 style={{ fontSize: 'clamp(32px,4vw,50px)', fontWeight: 900, letterSpacing: '-0.03em', margin: '14px 0 48px', lineHeight: 1.08 }}>
            This is what recruiters see.
          </h2>
        </FadeUp>
        <FadeUp delay={0.2}>
          <TiltCard style={{ maxWidth: 520, margin: '0 auto' }}>
            <GradBorder colors={['#D4FF00', '#34D399', '#60A5FA', '#D4FF00']}>
              <motion.div animate={{ y: [0, -10, 0] }} transition={{ duration: 4.5, repeat: Infinity, ease: 'easeInOut' }}
                style={{ padding: 32, textAlign: 'left', borderRadius: 21 }}>
                {/* Badge header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
                  <div style={{ width: 46, height: 46, borderRadius: 13, background: '#D4FF00', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, fontSize: 14, color: '#000', fontFamily: 'JetBrains Mono,monospace' }}>SP</div>
                  <div>
                    <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.25)', fontFamily: 'JetBrains Mono,monospace' }}>skillproof.dev/badge/sp_1baa...</div>
                    <div style={{ fontSize: 13, color: '#D4FF00', fontWeight: 700, marginTop: 3 }}>✓ Verified · March 2026</div>
                  </div>
                  <div style={{ marginLeft: 'auto', padding: '4px 10px', background: 'rgba(212,255,0,0.1)', border: '1px solid rgba(212,255,0,0.2)', borderRadius: 8, fontSize: 10, fontFamily: 'JetBrains Mono,monospace', color: '#D4FF00' }}>TAMPER-PROOF</div>
                </div>
                {/* Dev row */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 24, padding: '14px 18px', background: 'rgba(255,255,255,0.03)', borderRadius: 14, border: '1px solid rgba(255,255,255,0.05)' }}>
                  <div style={{ width: 42, height: 42, borderRadius: 11, background: 'rgba(212,255,0,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, color: '#D4FF00', fontSize: 16 }}>G</div>
                  <div>
                    <div style={{ fontWeight: 800, fontSize: 15 }}>Gowtham M S</div>
                    <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.35)', fontFamily: 'JetBrains Mono,monospace', marginTop: 2 }}>@Gowtham232004 · Automl</div>
                  </div>
                  <div style={{ marginLeft: 'auto', textAlign: 'right' }}>
                    <div style={{ fontSize: 40, fontWeight: 900, color: '#D4FF00', lineHeight: 1, letterSpacing: '-0.04em' }}>82</div>
                    <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.25)', marginTop: 1 }}>/ 100</div>
                  </div>
                </div>
                {/* Skill bars */}
                {[
                  { label: 'Backend Logic', score: 88, color: '#D4FF00' },
                  { label: 'API Design', score: 76, color: '#60A5FA' },
                  { label: 'Code Quality', score: 91, color: '#34D399' },
                  { label: 'Error Handling', score: 62, color: '#F59E0B' },
                  { label: 'Documentation', score: 45, color: '#F472B6' },
                ].map((skill, i) => (
                  <div key={i} style={{ marginBottom: 11 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
                      <span style={{ fontSize: 12, color: 'rgba(255,255,255,0.4)' }}>{skill.label}</span>
                      <span style={{ fontSize: 12, fontWeight: 800, color: skill.color, fontFamily: 'JetBrains Mono,monospace' }}>{skill.score}</span>
                    </div>
                    <div style={{ height: 5, background: 'rgba(255,255,255,0.05)', borderRadius: 3, overflow: 'hidden' }}>
                      <motion.div initial={{ width: 0 }} whileInView={{ width: `${skill.score}%` }}
                        transition={{ duration: 1.4, delay: i * 0.1, ease: 'easeOut' }}
                        style={{ height: '100%', background: skill.color, borderRadius: 3, boxShadow: `0 0 8px ${skill.color}60` }} />
                    </div>
                  </div>
                ))}
                <div style={{ marginTop: 20, padding: '10px 14px', background: 'rgba(212,255,0,0.05)', border: '1px solid rgba(212,255,0,0.12)', borderRadius: 10, fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: 'rgba(212,255,0,0.55)', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ color: '#D4FF00' }}>⬡</span>
                  HMAC-SHA256 signed · Tamper-proof · Public verification URL
                </div>
              </motion.div>
            </GradBorder>
          </TiltCard>
        </FadeUp>
      </section>

      {/* ── THE PIPELINE ─────────────────────────────────────────────────────── */}
      <section style={{ padding: '100px 24px 120px', maxWidth: 1200, margin: '0 auto' }}>
        <FadeUp>
          <div style={{ textAlign: 'center', marginBottom: 72 }}>
            <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase' }}>// THE PIPELINE</span>
            <h2 style={{ fontSize: 'clamp(36px,5vw,66px)', fontWeight: 900, letterSpacing: '-0.035em', marginTop: 14, lineHeight: 1.04 }}>
              Seven layers of<br /><span style={{ color: 'rgba(255,255,255,0.18)' }}>pure engineering.</span>
            </h2>
            <p style={{ fontSize: 15, color: 'rgba(255,255,255,0.3)', marginTop: 14, fontFamily: 'JetBrains Mono,monospace' }}>Click any step · auto-advances every 3 seconds</p>
          </div>
        </FadeUp>
        <OrbitalPipeline />
      </section>

      {/* ── MARQUEE ──────────────────────────────────────────────────────────── */}
      <div style={{ padding: '36px 0', borderTop: '1px solid rgba(255,255,255,0.05)', borderBottom: '1px solid rgba(255,255,255,0.05)', overflow: 'hidden' }}>
        <div style={{ marginBottom: 18 }}><Marquee /></div>
        <Marquee reverse />
      </div>

      {/* ── HOW IT WORKS ─────────────────────────────────────────────────────── */}
      <section id="how-it-works" style={{ padding: '120px 24px', maxWidth: 1200, margin: '0 auto' }}>
        <FadeUp>
          <div style={{ textAlign: 'center', marginBottom: 80 }}>
            <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase' }}>// HOW IT WORKS</span>
            <h2 style={{ fontSize: 'clamp(36px,5vw,62px)', fontWeight: 900, letterSpacing: '-0.035em', marginTop: 14, lineHeight: 1.04 }}>
              Code to credential<br /><span style={{ color: 'rgba(255,255,255,0.22)' }}>in under 2 minutes.</span>
            </h2>
          </div>
        </FadeUp>
        <div style={{ position: 'relative' }}>
          {/* Connecting line */}
          <div style={{ position: 'absolute', top: 44, left: '12.5%', right: '12.5%', height: 1, background: 'linear-gradient(90deg,transparent,rgba(212,255,0,0.2),rgba(212,255,0,0.4),rgba(212,255,0,0.2),transparent)' }} />
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 4 }}>
            {[
              { n: '01', t: 'Connect GitHub', d: 'One-click OAuth. Read-only access to public repos. No write permission ever.' },
              { n: '02', t: 'Select a Repo', d: 'Pick any project. We fetch actual source files, not just the README.' },
              { n: '03', t: 'Answer AI Questions', d: '5 questions grounded in your specific code. Impossible to Google.' },
              { n: '04', t: 'Get Verified Badge', d: 'HMAC-signed badge page. Share it with any recruiter or company.' },
            ].map((step, i) => (
              <FadeUp key={i} delay={i * 0.12}>
                <motion.div whileHover={{ y: -8 }} transition={{ duration: 0.3 }}
                  style={{ padding: '36px 24px', textAlign: 'center', position: 'relative' }}>
                  <motion.div whileHover={{ scale: 1.12, background: 'rgba(212,255,0,0.15)', boxShadow: '0 0 30px rgba(212,255,0,0.15)' }}
                    transition={{ duration: 0.3 }}
                    style={{ width: 76, height: 76, borderRadius: 20, background: 'rgba(212,255,0,0.06)', border: '1px solid rgba(212,255,0,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 22px', fontFamily: 'JetBrains Mono,monospace', fontSize: 22, fontWeight: 900, color: '#D4FF00', transition: 'all 0.3s' }}>
                    {step.n}
                  </motion.div>
                  <h3 style={{ fontSize: 17, fontWeight: 800, marginBottom: 10, letterSpacing: '-0.01em' }}>{step.t}</h3>
                  <p style={{ fontSize: 14, color: 'rgba(255,255,255,0.38)', lineHeight: 1.65 }}>{step.d}</p>
                </motion.div>
              </FadeUp>
            ))}
          </div>
        </div>
      </section>

      {/* ── FEATURES BENTO ───────────────────────────────────────────────────── */}
      <section id="features" style={{ padding: '60px 24px 120px', maxWidth: 1200, margin: '0 auto' }}>
        <FadeUp>
          <div style={{ textAlign: 'center', marginBottom: 64 }}>
            <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase' }}>// PLATFORM FEATURES</span>
            <h2 style={{ fontSize: 'clamp(36px,5vw,62px)', fontWeight: 900, letterSpacing: '-0.035em', marginTop: 14, lineHeight: 1.04 }}>
              Not just another<br /><span style={{ color: 'rgba(255,255,255,0.22)' }}>coding test.</span>
            </h2>
          </div>
        </FadeUp>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 14 }}>
          {/* Hero card */}
          <motion.div initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }} transition={{ duration: 0.7 }}
            whileHover={{ scale: 1.015 }}
            style={{ gridColumn: 'span 2', gridRow: 'span 2', background: '#0D0D0D', borderRadius: 28, border: '1px solid rgba(212,255,0,0.18)', padding: 36, position: 'relative', overflow: 'hidden' }}>
            <motion.div style={{ position: 'absolute', width: 280, height: 280, background: 'radial-gradient(circle,#D4FF00 0%,transparent 70%)', filter: 'blur(50px)', top: -80, right: -60, pointerEvents: 'none' }}
              animate={{ x: [0, 30, 0], y: [0, -20, 0], opacity: [0.06, 0.12, 0.06] }} transition={{ duration: 8, repeat: Infinity }} />
            {/* Animated code lines */}
            <div style={{ position: 'absolute', top: 24, right: 28, opacity: 0.07, fontFamily: 'JetBrains Mono,monospace', fontSize: 11, lineHeight: 1.8, textAlign: 'right', pointerEvents: 'none' }}>
              {['async function withAuth(req)', '  const token = getToken(req)', '  if (!token) throw new Error', '  return verify(token, SECRET)', '// Q: what if verify() throws?'].map((l, i) => (
                <motion.div key={i} animate={{ opacity: [0.5, 1, 0.5] }} transition={{ duration: 2, delay: i * 0.3, repeat: Infinity }}>{l}</motion.div>
              ))}
            </div>
            <div style={{ position: 'relative', zIndex: 1 }}>
              <div style={{ fontSize: 52, color: '#D4FF00', marginBottom: 18, lineHeight: 1 }}>◈</div>
              <h3 style={{ fontSize: 30, fontWeight: 900, marginBottom: 14, letterSpacing: '-0.025em', lineHeight: 1.1 }}>Code-Grounded AI</h3>
              <p style={{ fontSize: 15, color: 'rgba(255,255,255,0.42)', maxWidth: 380, lineHeight: 1.7 }}>Questions generated from your actual code. Every session is unique — studying the repo is the intended behavior.</p>
              <div style={{ marginTop: 24, padding: '18px 22px', background: 'rgba(0,0,0,0.5)', borderRadius: 16, border: '1px solid rgba(212,255,0,0.1)' }}>
                <div style={{ fontSize: 11, fontFamily: 'JetBrains Mono,monospace', color: 'rgba(212,255,0,0.5)', marginBottom: 8, letterSpacing: '0.1em' }}>Q3 / MEDIUM — middleware.ts</div>
                <code style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 13, color: '#D4FF00', lineHeight: 1.6 }}>"In your withAuth() function, what happens if token verification throws an unexpected exception?"</code>
              </div>
            </div>
          </motion.div>

          {/* Small cards */}
          {[
            { icon: '⬡', title: 'Live Coding', desc: 'Docker-sandboxed tasks. Automated test scoring. Proves you can build, not just talk.', color: '#34D399', border: 'rgba(52,211,153,0.2)' },
            { icon: '◎', title: 'Peer Review', desc: 'Senior devs matched by your stack review architecture, security, and code quality.', color: '#F59E0B', border: 'rgba(245,158,11,0.2)' },
            { icon: '◉', title: 'Skill Gap Analysis', desc: 'File-level detection. Fix instructions. Week-by-week improvement roadmap included.', color: '#A78BFA', border: 'rgba(167,139,250,0.2)' },
            { icon: '⬢', title: 'Hiring Pipeline', desc: 'Companies access verified candidates. Every profile backed by evidence, not claims.', color: '#60A5FA', border: 'rgba(96,165,250,0.2)' },
          ].map((f, i) => (
            <motion.div key={i} initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: i * 0.08 }}
              whileHover={{ y: -6 }}
              style={{ background: '#0D0D0D', borderRadius: 22, border: `1px solid ${f.border}`, padding: 26, position: 'relative', overflow: 'hidden' }}>
              <motion.div style={{ position: 'absolute', top: -30, right: -30, width: 100, height: 100, background: `radial-gradient(circle,${f.color} 0%,transparent 70%)`, filter: 'blur(20px)', opacity: 0.06, pointerEvents: 'none' }}
                animate={{ opacity: [0.06, 0.14, 0.06] }} transition={{ duration: 3, repeat: Infinity }} />
              <div style={{ fontSize: 36, color: f.color, marginBottom: 14 }}>{f.icon}</div>
              <h3 style={{ fontSize: 19, fontWeight: 800, marginBottom: 8, letterSpacing: '-0.01em' }}>{f.title}</h3>
              <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.38)', lineHeight: 1.65 }}>{f.desc}</p>
            </motion.div>
          ))}

          {/* Wide bottom card */}
          <motion.div initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.3 }}
            whileHover={{ x: 5 }}
            style={{ gridColumn: 'span 2', background: '#0D0D0D', borderRadius: 22, border: '1px solid rgba(212,255,0,0.1)', padding: 26, display: 'flex', alignItems: 'center', gap: 20 }}>
            <motion.div animate={{ rotate: [0, 360] }} transition={{ duration: 4, repeat: Infinity, ease: 'linear' }}
              style={{ fontSize: 42, color: '#D4FF00', flexShrink: 0, lineHeight: 1 }}>↻</motion.div>
            <div>
              <h3 style={{ fontSize: 19, fontWeight: 800, marginBottom: 6 }}>Continuous Re-verification</h3>
              <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.38)', lineHeight: 1.6, margin: 0 }}>Your badge updates as you push new code. Scores reflect your current skill level, not a one-time snapshot.</p>
            </div>
            <div style={{ marginLeft: 'auto', flexShrink: 0, padding: '8px 18px', background: 'rgba(212,255,0,0.08)', borderRadius: 10, fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', fontWeight: 700, whiteSpace: 'nowrap' }}>
              Always Current
            </div>
          </motion.div>

          {/* Tamper-proof badge card */}
          <motion.div initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.35 }}
            whileHover={{ rotate: 1 }}
            style={{ background: '#0D0D0D', borderRadius: 22, border: '1px solid rgba(212,255,0,0.15)', padding: 26 }}>
            <div style={{ fontSize: 36, color: '#D4FF00', marginBottom: 14 }}>✦</div>
            <h3 style={{ fontSize: 19, fontWeight: 800, marginBottom: 8 }}>Tamper-Proof Badges</h3>
            <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.38)', lineHeight: 1.65 }}>HMAC-SHA256 signed. Publicly verifiable at a permanent URL — no login required.</p>
          </motion.div>
        </div>
      </section>

      {/* ── SOCIAL PROOF / TESTIMONIALS ──────────────────────────────────────── */}
      <section style={{ padding: '80px 0 100px', overflow: 'hidden' }}>
        <FadeUp>
          <div style={{ textAlign: 'center', marginBottom: 52, padding: '0 24px' }}>
            <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase' }}>// DEVELOPER STORIES</span>
            <h2 style={{ fontSize: 'clamp(32px,4vw,52px)', fontWeight: 900, letterSpacing: '-0.035em', marginTop: 14 }}>
              From verified developers.
            </h2>
          </div>
        </FadeUp>
        <div style={{ display: 'flex', gap: 16, padding: '8px 40px 24px', overflow: 'hidden', maskImage: 'linear-gradient(90deg,transparent,black 6%,black 94%,transparent)', WebkitMaskImage: 'linear-gradient(90deg,transparent,black 6%,black 94%,transparent)' }}>
          <motion.div animate={{ x: [0, -(TESTIMONIALS.length * 300)] }} transition={{ duration: 30, repeat: Infinity, ease: 'linear' }}
            style={{ display: 'flex', gap: 16, flexShrink: 0 }}>
            {[...TESTIMONIALS, ...TESTIMONIALS].map((t, i) => <TestimonialCard key={i} t={t} />)}
          </motion.div>
        </div>
      </section>

      {/* ── FOR COMPANIES ────────────────────────────────────────────────────── */}
      <section id="for-companies" style={{ padding: '80px 24px 120px' }}>
        <div style={{ maxWidth: 1200, margin: '0 auto', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 80, alignItems: 'center' }}>
          <FadeUp>
            <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase' }}>// FOR COMPANIES</span>
            <h2 style={{ fontSize: 'clamp(32px,4vw,54px)', fontWeight: 900, letterSpacing: '-0.035em', margin: '16px 0 22px', lineHeight: 1.08 }}>
              Hire on verified<br /><span style={{ color: '#D4FF00' }}>evidence.</span><br /><span style={{ color: 'rgba(255,255,255,0.22)' }}>Not claims.</span>
            </h2>
            <p style={{ fontSize: 15, color: 'rgba(255,255,255,0.42)', lineHeight: 1.75, marginBottom: 36 }}>Every candidate has a score built from their actual projects. No more 2-hour calls for developers who can't explain their own code.</p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {[
                { n: '80%', t: 'Less screening time per hire', icon: '↓' },
                { n: '₹3L+', t: 'Saved per bad hire prevented', icon: '◈' },
                { n: '3×', t: 'Better hire quality score', icon: '↑' },
              ].map((s, i) => (
                <motion.div key={i} initial={{ opacity: 0, x: -20 }} whileInView={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.1, duration: 0.5 }} whileHover={{ x: 6 }}
                  style={{ display: 'flex', alignItems: 'center', gap: 18, padding: '18px 22px', background: 'rgba(212,255,0,0.03)', border: '1px solid rgba(212,255,0,0.1)', borderRadius: 14, cursor: 'default', transition: 'border-color 0.2s' }}
                  onMouseEnter={e => (e.currentTarget.style.borderColor = 'rgba(212,255,0,0.25)')}
                  onMouseLeave={e => (e.currentTarget.style.borderColor = 'rgba(212,255,0,0.1)')}>
                  <span style={{ fontSize: 30, fontWeight: 900, color: '#D4FF00', minWidth: 80, letterSpacing: '-0.04em', fontFamily: 'JetBrains Mono,monospace' }}>{s.n}</span>
                  <span style={{ fontSize: 15, color: 'rgba(255,255,255,0.45)' }}>{s.t}</span>
                </motion.div>
              ))}
            </div>
          </FadeUp>

          {/* Mock recruiter dashboard */}
          <FadeUp delay={0.2}>
            <SpotlightCard>
              <div style={{ padding: 26 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 22 }}>
                  <div style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: 'rgba(255,255,255,0.2)' }}>// recruiter_dashboard</div>
                  <div style={{ display: 'flex', gap: 5 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#ff5f56' }} />
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#ffbd2e' }} />
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#27c93f' }} />
                  </div>
                </div>
                {[
                  { name: 'Priya Sharma', repo: 'ecommerce-api', score: 87, color: '#D4FF00', level: 'Senior' },
                  { name: 'Arjun Nair', repo: 'ml-pipeline', score: 76, color: '#60A5FA', level: 'Mid' },
                  { name: 'Meera Iyer', repo: 'react-dashboard', score: 91, color: '#34D399', level: 'Senior' },
                  { name: 'Rahul Gupta', repo: 'fintech-app', score: 68, color: '#F59E0B', level: 'Junior' },
                ].map((c, i) => (
                  <motion.div key={i} initial={{ opacity: 0, x: 20 }} whileInView={{ opacity: 1, x: 0 }}
                    transition={{ delay: i * 0.1, duration: 0.5 }} whileHover={{ x: 4 }}
                    style={{ display: 'flex', alignItems: 'center', gap: 13, padding: '12px 16px', marginBottom: 8, background: 'rgba(255,255,255,0.025)', border: '1px solid rgba(255,255,255,0.05)', borderRadius: 12, cursor: 'default' }}>
                    <div style={{ width: 36, height: 36, borderRadius: 10, background: `${c.color}18`, color: c.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 800, flexShrink: 0 }}>
                      {c.name.split(' ').map(n => n[0]).join('')}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 14, fontWeight: 700 }}>{c.name}</div>
                      <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.28)', fontFamily: 'JetBrains Mono,monospace', marginTop: 1 }}>{c.repo}</div>
                    </div>
                    <div style={{ padding: '2px 8px', background: `${c.color}12`, borderRadius: 6, fontSize: 10, color: c.color, fontFamily: 'JetBrains Mono,monospace', fontWeight: 700 }}>{c.level}</div>
                    <div style={{ textAlign: 'right', minWidth: 44 }}>
                      <div style={{ fontSize: 22, fontWeight: 900, color: c.color, lineHeight: 1, letterSpacing: '-0.03em' }}>{c.score}</div>
                    </div>
                  </motion.div>
                ))}
                <MagneticBtn onClick={handleLogin}
                  style={{ width: '100%', marginTop: 10, padding: '13px', background: '#D4FF00', color: '#000', border: 'none', borderRadius: 10, fontWeight: 800, fontSize: 14, cursor: 'pointer', fontFamily: 'Outfit,sans-serif', display: 'block' }}>
                  View Full Dashboard →
                </MagneticBtn>
              </div>
            </SpotlightCard>
          </FadeUp>
        </div>
      </section>

      {/* ── SKILL DIMENSIONS ─────────────────────────────────────────────────── */}
      <section style={{ padding: '60px 24px 120px', maxWidth: 1200, margin: '0 auto' }}>
        <FadeUp>
          <div style={{ textAlign: 'center', marginBottom: 60 }}>
            <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase' }}>// WHAT WE MEASURE</span>
            <h2 style={{ fontSize: 'clamp(36px,5vw,58px)', fontWeight: 900, letterSpacing: '-0.035em', marginTop: 14, lineHeight: 1.04 }}>
              Five dimensions.<br /><span style={{ color: 'rgba(255,255,255,0.2)' }}>One score.</span>
            </h2>
          </div>
        </FadeUp>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5,1fr)', gap: 12 }}>
          {[
            { label: 'Backend Logic', score: 88, color: '#D4FF00', desc: 'Architecture decisions, service layer patterns, dependency design' },
            { label: 'API Design', score: 76, color: '#60A5FA', desc: 'REST conventions, status codes, response structure consistency' },
            { label: 'Error Handling', score: 62, color: '#F59E0B', desc: 'Exception coverage, meaningful messages, edge case handling' },
            { label: 'Code Quality', score: 91, color: '#34D399', desc: 'Naming, readability, DRY principle, comment quality' },
            { label: 'Documentation', score: 45, color: '#F472B6', desc: 'README completeness, inline docs, API documentation coverage' },
          ].map((dim, i) => (
            <FadeUp key={i} delay={i * 0.1}>
              <SpotlightCard accentColor={`${dim.color}10`}>
                <div style={{ padding: 22, textAlign: 'center' }}>
                  <div style={{ marginBottom: 14, position: 'relative', height: 88, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <svg width="88" height="88" viewBox="0 0 88 88" style={{ transform: 'rotate(-90deg)' }}>
                      <circle cx="44" cy="44" r="36" fill="none" stroke="rgba(255,255,255,0.04)" strokeWidth="7" />
                      {/* Track glow */}
                      <circle cx="44" cy="44" r="36" fill="none" stroke={`${dim.color}15`} strokeWidth="7" />
                      <motion.circle cx="44" cy="44" r="36" fill="none" stroke={dim.color} strokeWidth="7" strokeLinecap="round"
                        strokeDasharray={`${2 * Math.PI * 36}`}
                        initial={{ strokeDashoffset: 2 * Math.PI * 36 }}
                        whileInView={{ strokeDashoffset: 2 * Math.PI * 36 * (1 - dim.score / 100) }}
                        transition={{ duration: 1.6, delay: i * 0.1, ease: 'easeOut' }}
                        style={{ filter: `drop-shadow(0 0 6px ${dim.color}60)` }} />
                    </svg>
                    <div style={{ position: 'absolute', fontSize: 22, fontWeight: 900, color: dim.color, fontFamily: 'JetBrains Mono,monospace', letterSpacing: '-0.03em' }}>{dim.score}</div>
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 800, marginBottom: 8, color: 'white', letterSpacing: '-0.01em' }}>{dim.label}</div>
                  <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.28)', lineHeight: 1.55 }}>{dim.desc}</div>
                </div>
              </SpotlightCard>
            </FadeUp>
          ))}
        </div>
        <FadeUp delay={0.3}>
          <div style={{ marginTop: 20, textAlign: 'center', fontSize: 12, color: 'rgba(255,255,255,0.2)', fontFamily: 'JetBrains Mono,monospace' }}>
            * Sample scores. Your scores reflect your actual codebase.
          </div>
        </FadeUp>
      </section>

      {/* ── PRICING ──────────────────────────────────────────────────────────── */}
      <section id="pricing" style={{ padding: '80px 24px 120px' }}>
        <div style={{ maxWidth: 1100, margin: '0 auto' }}>
          <FadeUp>
            <div style={{ textAlign: 'center', marginBottom: 72 }}>
              <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase' }}>// PRICING</span>
              <h2 style={{ fontSize: 'clamp(36px,5vw,62px)', fontWeight: 900, letterSpacing: '-0.035em', marginTop: 14, lineHeight: 1.04 }}>
                Simple, honest pricing.<br /><span style={{ color: 'rgba(255,255,255,0.22)' }}>No surprises.</span>
              </h2>
            </div>
          </FadeUp>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 16, alignItems: 'start' }}>
            {[
              { name: 'Developer', price: 'Free', period: 'forever', popular: false, features: ['3 verifications/month', 'Public badge page', 'Basic skill scores', 'Shareable badge URL'], cta: 'Start Free' },
              { name: 'Recruiter', price: '₹2,999', period: '/month', popular: true, features: ['Unlimited screening', 'Recruiter dashboard', 'PDF reports & export', 'Challenge creation', 'Candidate comparison'], cta: 'Start Hiring' },
              { name: 'Institution', price: '₹7,999', period: '/month', popular: false, features: ['Batch verification', 'White-label badge', 'Placement reports', 'Cohort analytics', 'Priority support'], cta: 'Contact Sales' },
            ].map((plan, i) => (
              <FadeUp key={i} delay={i * 0.1}>
                <motion.div whileHover={{ y: plan.popular ? -4 : -6 }} transition={{ duration: 0.3 }}
                  style={{ padding: '32px 28px', borderRadius: 20, position: 'relative', overflow: 'hidden',
                    background: plan.popular ? 'rgba(212,255,0,0.04)' : '#0D0D0D',
                    border: plan.popular ? '1px solid rgba(212,255,0,0.3)' : '1px solid rgba(255,255,255,0.07)',
                    boxShadow: plan.popular ? '0 0 80px rgba(212,255,0,0.08)' : 'none',
                    transform: plan.popular ? 'scale(1.03)' : 'none' }}>
                  {plan.popular && (
                    <>
                      <motion.div style={{ position: 'absolute', inset: -1, borderRadius: 21, background: 'linear-gradient(135deg,#D4FF00,#34D399,#60A5FA,#D4FF00)', backgroundSize: '300% 300%', zIndex: -1, opacity: 0.6 }}
                        animate={{ backgroundPosition: ['0% 50%', '100% 50%', '0% 50%'] }} transition={{ duration: 4, repeat: Infinity }} />
                      <div style={{ position: 'absolute', top: 18, right: 18, padding: '3px 10px', background: '#D4FF00', color: '#000', borderRadius: 6, fontSize: 10, fontWeight: 900, fontFamily: 'JetBrains Mono,monospace', letterSpacing: '0.08em' }}>POPULAR</div>
                    </>
                  )}
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'rgba(255,255,255,0.4)', marginBottom: 10 }}>{plan.name}</div>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 4, marginBottom: 28 }}>
                    <span style={{ fontSize: 46, fontWeight: 900, letterSpacing: '-0.04em', color: plan.popular ? '#D4FF00' : '#fff' }}>{plan.price}</span>
                    <span style={{ fontSize: 14, color: 'rgba(255,255,255,0.25)' }}>{plan.period}</span>
                  </div>
                  <div style={{ borderTop: '1px solid rgba(255,255,255,0.06)', paddingTop: 20, marginBottom: 24 }}>
                    {plan.features.map((f, j) => (
                      <div key={j} style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 13 }}>
                        <span style={{ color: plan.popular ? '#D4FF00' : '#34D399', fontSize: 15, lineHeight: 1, flexShrink: 0 }}>✓</span>
                        <span style={{ fontSize: 14, color: 'rgba(255,255,255,0.45)' }}>{f}</span>
                      </div>
                    ))}
                  </div>
                  <MagneticBtn onClick={handleLogin} className={plan.popular ? 'shimmer-btn' : ''}
                    style={{ width: '100%', padding: '14px', borderRadius: 12, fontWeight: 800, fontSize: 15, cursor: 'pointer', fontFamily: 'Outfit,sans-serif', position: 'relative', overflow: 'hidden',
                      background: plan.popular ? '#D4FF00' : 'transparent', color: plan.popular ? '#000' : '#fff',
                      border: plan.popular ? 'none' : '1px solid rgba(255,255,255,0.12)', display: 'block' }}>
                    {plan.cta}
                  </MagneticBtn>
                </motion.div>
              </FadeUp>
            ))}
          </div>
        </div>
      </section>

      {/* ── FAQ ──────────────────────────────────────────────────────────────── */}
      <section style={{ padding: '80px 24px 120px', maxWidth: 740, margin: '0 auto' }}>
        <FadeUp>
          <div style={{ textAlign: 'center', marginBottom: 60 }}>
            <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase' }}>// FAQ</span>
            <h2 style={{ fontSize: 'clamp(32px,4vw,54px)', fontWeight: 900, letterSpacing: '-0.035em', marginTop: 14 }}>
              Questions answered.
            </h2>
          </div>
        </FadeUp>
        {[
          { q: 'Is this just a ChatGPT wrapper?', a: 'No. The AI is one of seven components. The system includes GitHub OAuth integration, file filtering and ranking, code structure extraction, Docker-sandboxed code execution, reviewer matching, HMAC cryptographic badge signing, and pattern scanning. The AI adds question generation and answer evaluation on top of that engineering.' },
          { q: "Can't someone just study the repo and fake it?", a: "Yes — and that's fine. If a developer studies their own repository deeply enough to answer specific questions about implementation choices, they now understand the code. That's the goal. The badge represents understanding, which is what recruiters care about." },
          { q: 'Who pays? Students won\'t pay.', a: "Correct — students don't pay. Recruiters pay ₹2,999/month to screen candidates without 2-hour calls. Colleges pay ₹7,999/month to verify their graduating cohort. Students are the supply side. Classic two-sided market." },
          { q: 'How is this different from HackerRank?', a: "HackerRank tests generic algorithm problems you can prep for in 2 months. SkillProof tests you on your own code. 'Why did you use a HashMap in line 47 of your OrderService.java?' cannot be Googled and cannot be prepped for without understanding your own project." },
          { q: 'Is the badge actually secure?', a: "Yes. Every badge is signed with HMAC-SHA256 using a server-side secret. The badge URL encodes the signature. Any modification to score or user data invalidates the signature. A recruiter can verify any badge at the public URL — no login required." },
        ].map((item, i) => (
          <FadeUp key={i} delay={i * 0.04}>
            <FAQItem q={item.q} a={item.a} />
          </FadeUp>
        ))}
      </section>

      {/* ── FINAL CTA ────────────────────────────────────────────────────────── */}
      <section style={{ padding: '80px 24px 140px', textAlign: 'center', position: 'relative', overflow: 'hidden' }}>
        {/* Dramatic background glow */}
        <div style={{ position: 'absolute', bottom: -100, left: '50%', transform: 'translateX(-50%)', width: 900, height: 500, background: 'radial-gradient(ellipse at bottom,rgba(212,255,0,0.14) 0%,transparent 65%)', pointerEvents: 'none' }} />
        {/* Grid lines */}
        <div style={{ position: 'absolute', inset: 0, backgroundImage: 'linear-gradient(rgba(212,255,0,0.03) 1px,transparent 1px),linear-gradient(90deg,rgba(212,255,0,0.03) 1px,transparent 1px)', backgroundSize: '60px 60px', maskImage: 'radial-gradient(ellipse 80% 80% at 50% 100%,black 40%,transparent 100%)', WebkitMaskImage: 'radial-gradient(ellipse 80% 80% at 50% 100%,black 40%,transparent 100%)', pointerEvents: 'none' }} />

        <FadeUp>
          <div style={{ position: 'relative', zIndex: 10 }}>
            <span style={{ fontFamily: 'JetBrains Mono,monospace', fontSize: 11, color: '#D4FF00', letterSpacing: '0.22em', textTransform: 'uppercase', display: 'block', marginBottom: 28 }}>// GET VERIFIED</span>
            <h2 style={{ fontSize: 'clamp(42px,7vw,86px)', fontWeight: 900, letterSpacing: '-0.045em', lineHeight: 0.95, marginBottom: 26 }}>
              Stop applying with<br />unverified GitHub links.
            </h2>
            <p style={{ fontSize: 18, color: 'rgba(255,255,255,0.35)', marginBottom: 52, fontWeight: 400 }}>Your SkillProof badge is free. Takes 2 minutes. Lasts forever.</p>
            <MagneticBtn onClick={handleLogin} className="shimmer-btn"
              style={{ background: '#D4FF00', color: '#000', border: 'none', padding: '22px 56px', borderRadius: 18, fontWeight: 900, fontSize: 20, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 12, fontFamily: 'Outfit,sans-serif', position: 'relative', overflow: 'hidden', boxShadow: '0 0 80px rgba(212,255,0,0.25)' }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
              Verify My Portfolio Free
            </MagneticBtn>
            <div style={{ marginTop: 28, fontFamily: 'JetBrains Mono,monospace', fontSize: 12, color: 'rgba(255,255,255,0.2)' }}>
              No credit card · Read-only GitHub access · Badge in 2 minutes
            </div>
          </div>
        </FadeUp>
      </section>

      {/* ── FOOTER ───────────────────────────────────────────────────────────── */}
      <footer style={{ borderTop: '1px solid rgba(255,255,255,0.05)', padding: '64px 24px 36px' }}>
        <div style={{ maxWidth: 1200, margin: '0 auto' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 1fr', gap: 48, marginBottom: 60 }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
                <div style={{ width: 34, height: 34, borderRadius: 9, background: '#D4FF00', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'JetBrains Mono,monospace', fontWeight: 800, fontSize: 11, color: '#000' }}>SP</div>
                <span style={{ fontWeight: 900, fontSize: 17, letterSpacing: '-0.02em' }}>SkillProof</span>
              </div>
              <p style={{ fontSize: 14, color: 'rgba(255,255,255,0.3)', lineHeight: 1.75, maxWidth: 270 }}>
                AI-powered developer credibility platform. Prove you built what you claim. Verified in 2 minutes.
              </p>
              <div style={{ display: 'flex', gap: 14, marginTop: 20 }}>
                {[{ label: 'GitHub', href: 'https://github.com' }, { label: 'Twitter', href: '#' }, { label: 'LinkedIn', href: '#' }].map(s => (
                  <motion.a key={s.label} href={s.href} whileHover={{ y: -2, color: '#D4FF00' }}
                    style={{ fontSize: 13, color: 'rgba(255,255,255,0.25)', textDecoration: 'none', fontFamily: 'JetBrains Mono,monospace', transition: 'color 0.2s' }}>
                    {s.label}
                  </motion.a>
                ))}
              </div>
            </div>
            {[
              { title: 'Product', links: [['Verify Portfolio','/verify'],['Live Challenges','/challenges'],['Peer Review','/review'],['Skill Gap Report','/gaps'],['Pricing','#pricing']] },
              { title: 'Companies', links: [['Recruiter Dashboard','/recruiter'],['Institution Plan','#pricing'],['Batch Verification','#'],['ATS Integration','#'],['Contact Sales','#']] },
              { title: 'Resources', links: [['Documentation','#'],['API Reference','#'],['Sample Badge','#'],['Privacy Policy','#'],['Terms of Service','#']] },
            ].map(col => (
              <div key={col.title}>
                <div style={{ fontSize: 11, fontWeight: 800, color: 'rgba(255,255,255,0.35)', letterSpacing: '0.12em', textTransform: 'uppercase', marginBottom: 20, fontFamily: 'JetBrains Mono,monospace' }}>{col.title}</div>
                {col.links.map(([label, href]) => (
                  <motion.a key={label} href={href} whileHover={{ x: 4, color: '#D4FF00' }}
                    style={{ display: 'block', fontSize: 14, color: 'rgba(255,255,255,0.35)', textDecoration: 'none', marginBottom: 11, transition: 'color 0.2s' }}>
                    {label}
                  </motion.a>
                ))}
              </div>
            ))}
          </div>
          <div style={{ borderTop: '1px solid rgba(255,255,255,0.04)', paddingTop: 26, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
            <p style={{ fontSize: 12, color: 'rgba(255,255,255,0.18)' }}>© 2026 SkillProof. Built by developers, for developers.</p>
            <p style={{ fontSize: 12, color: 'rgba(255,255,255,0.18)', fontFamily: 'JetBrains Mono,monospace' }}>
              Scores are indicative only. Not a substitute for technical interviews.
            </p>
          </div>
        </div>
      </footer>

    </div>
  )
}