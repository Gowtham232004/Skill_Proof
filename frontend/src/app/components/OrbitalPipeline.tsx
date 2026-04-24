'use client';
import { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const STEPS = [
  { n:'01', label:'GitHub OAuth',    icon:'⚡', color:'#D4FF00', tag:'Token Exchange',
    desc:'Secure OAuth 2.0 with read-only scope. Minimum permissions — public_repo only. No write access ever granted.',
    out:[['access_token','gho_8xKLm9...'],['scope','public_repo'],['rate_limit','5000/hr']],
    stat:'~320ms', viz:'oauth' },
  { n:'02', label:'File Filter',     icon:'◈', color:'#60A5FA', tag:'Rank by Relevance',
    desc:'AST-based scorer evaluates every file. 93 files ranked by signal — 15 selected. node_modules and lock files excluded.',
    out:[['middleware.ts','████████ 94'],['google-ai-utils.ts','██████░░ 87'],['auth.ts','██████░░ 82']],
    stat:'93 → 15 files', viz:'filter' },
  { n:'03', label:'Code Extractor',  icon:'⬡', color:'#34D399', tag:'AST + Patterns',
    desc:'Tree-sitter parses your top files. Extracts functions, classes, and types into a 30K-char semantic model.',
    out:[['functions','48 extracted'],['classes','12 found'],['summary','30,609 chars']],
    stat:'30,609 chars', viz:'extract' },
  { n:'04', label:'Groq AI',       icon:'◎', color:'#A78BFA', tag:'Grounded Questions',
    desc:'Groq Pro generates 5 questions grounded in your actual code — each references a specific file or function.',
    out:[['Q1','→ withAuth() in middleware.ts'],['Q2','→ OrderService.java:L47'],['Q3','→ api/route.ts handler']],
    stat:'5 questions', viz:'ai' },
  { n:'05', label:'Answer Eval',     icon:'◉', color:'#F59E0B', tag:'Rubric Scoring',
    desc:'Multi-criterion rubric scores depth, accuracy, and code-specific reasoning. Each of 5 answers scored 0–10.',
    out:[['Q1-Q3','8.2 · 7.6 · 9.0'],['Q4-Q5','7.1 · 8.4'],['composite','82 / 100']],
    stat:'avg 8.1 / 10', viz:'eval' },
  { n:'06', label:'Gap Scanner',     icon:'⬢', color:'#F472B6', tag:'24 Code Checks',
    desc:'24 automated checks scan for missing error handling, security gaps, undocumented exports, and anti-patterns.',
    out:[['auth patterns','✓ PASS'],['documentation','✗ 45/100'],['error handling','✓ PASS']],
    stat:'24 checks', viz:'scan' },
  { n:'07', label:'HMAC Badge',      icon:'✦', color:'#D4FF00', tag:'Cryptographic Sign',
    desc:'HMAC-SHA256 signs the score payload. Badge URL encodes the signature — any modification is immediately detectable.',
    out:[['badge_id','sp_1baa55015d...'],['algorithm','HMAC-SHA256'],['url','skillproof.dev/b/sp_...']],
    stat:'< 1ms sign', viz:'badge' },
];

const DURATION = 3000;

// ─── Step Visualizations ──────────────────────────────────────────────────────
function OAuthViz({ color }: { color: string }) {
  return (
    <svg viewBox="0 0 220 80" style={{ width:'100%', height:80, overflow:'visible', opacity:.75 }}>
      <rect x="5" y="22" width="70" height="34" rx="8" fill="none" stroke={color} strokeWidth="1.5"/>
      <text x="40" y="42" textAnchor="middle" fill={color} fontSize="10" fontFamily="JetBrains Mono,monospace" fontWeight="700">CLIENT</text>
      <rect x="145" y="22" width="70" height="34" rx="8" fill="none" stroke="rgba(255,255,255,.25)" strokeWidth="1.5"/>
      <text x="180" y="42" textAnchor="middle" fill="rgba(255,255,255,.35)" fontSize="10" fontFamily="JetBrains Mono,monospace" fontWeight="700">GITHUB</text>
      <line x1="75" y1="36" x2="145" y2="36" stroke="rgba(255,255,255,.1)" strokeWidth="1" strokeDasharray="4 4"/>
      <motion.g animate={{ x:[0,70,70,0,0] }} transition={{ duration:2, repeat:Infinity, ease:'easeInOut' }}>
        <rect x="82" y="28" width="50" height="16" rx="8" fill={color}/>
        <text x="107" y="39" textAnchor="middle" fill="#000" fontSize="9" fontFamily="JetBrains Mono,monospace" fontWeight="800">TOKEN</text>
      </motion.g>
    </svg>
  );
}

function FilterViz({ color }: { color: string }) {
  const bars: [string, number][] = [['middleware.ts',94],['ai-utils.ts',87],['auth.ts',82],['config.ts',31],['package.json',8]];
  return (
    <svg viewBox="0 0 220 80" style={{ width:'100%', height:80, opacity:.75 }}>
      {bars.map(([name,score],i)=>{
        const barWidth = score * 0.8;
        return (
        <g key={i}>
          <text x="8" y={14+i*14} fill={i<3?'rgba(255,255,255,.5)':'rgba(255,255,255,.15)'} fontSize="9" fontFamily="JetBrains Mono,monospace">{name}</text>
          <motion.rect x="100" y={7+i*14} height="7" rx="3" fill={i<3?color:'#333'}
            initial={{ width:0 }} animate={{ width: barWidth }} transition={{ duration:.8, delay:i*.1 }}/>
          <motion.text x={105+barWidth} y={14+i*14} fill={i<3?color:'#333'} fontSize="8" fontFamily="JetBrains Mono,monospace"
            initial={{ opacity:0 }} animate={{ opacity:1 }} transition={{ delay:i*.1+.5 }}>{score}</motion.text>
        </g>
      );
      })}
    </svg>
  );
}

function ExtractViz({ color }: { color: string }) {
  const lines=['function withAuth(req) {','  const token = headers.auth;','  return verify(token, key);','}','// 48 functions extracted'];
  return (
    <svg viewBox="0 0 220 80" style={{ width:'100%', height:80, opacity:.75 }}>
      <rect x="4" y="4" width="3" height="70" rx="2" fill={color}/>
      {lines.map((line,i)=>(
        <g key={i}>
          {i===1 && <rect x="4" y={7+i*14} width="212" height="13" rx="3" fill={`${color}22`}/>}
          <text x="12" y={14+i*14} fill={i===4?color:i===1?color:'rgba(255,255,255,.4)'} fontSize="9" fontFamily="JetBrains Mono,monospace">{line}</text>
        </g>
      ))}
    </svg>
  );
}

function AIViz({ color }: { color: string }) {
  const nodes: [number, number][] = [[30,40],[70,20],[70,60],[120,30],[120,55],[170,15],[170,45],[170,70],[200,35]];
  const edges: [number, number][] = [[0,1],[0,2],[1,3],[2,4],[3,5],[3,6],[4,6],[4,7],[6,8]];
  const cols=[color,'#A78BFA','#60A5FA'];
  return (
    <svg viewBox="0 0 220 80" style={{ width:'100%', height:80, opacity:.75, overflow:'visible' }}>
      {edges.map(([a,b],i)=>(
        <line key={i} x1={nodes[a][0]} y1={nodes[a][1]} x2={nodes[b][0]} y2={nodes[b][1]} stroke={color} strokeWidth="1" opacity={.1+i*.04}/>
      ))}
      {nodes.map(([x,y],i)=>(
        <motion.circle key={i} cx={x} cy={y} r={4} fill={cols[i%3]}
          animate={{ r:[4,8,4], opacity:[.3,1,.3] }}
          transition={{ duration:1+i*.2, delay:i*.15, repeat:Infinity }}/>
      ))}
      <text x="110" y="78" textAnchor="middle" fill={color} fontSize="9" fontFamily="JetBrains Mono,monospace">generating questions...</text>
    </svg>
  );
}

function EvalViz({ color }: { color: string }) {
  const qs: [string, number, string][] = [['Q1',82,'#D4FF00'],['Q2',76,'#60A5FA'],['Q3',90,'#34D399'],['Q4',71,'#F59E0B'],['Q5',84,'#A78BFA']];
  return (
    <svg viewBox="0 0 220 80" style={{ width:'100%', height:80, opacity:.75 }}>
      <text x="214" y="10" textAnchor="end" fill={color} fontSize="8" fontFamily="JetBrains Mono,monospace">rubric</text>
      {qs.map(([q,s,c],i)=>{
        const barWidth = s * 1.8;
        return (
        <g key={i}>
          <text x="8" y={14+i*14} fill="rgba(255,255,255,.45)" fontSize="9" fontFamily="JetBrains Mono,monospace">{q}</text>
          <motion.rect x="28" y={7+i*14} height="7" rx="3" fill={c}
            initial={{ width:0 }} animate={{ width: barWidth }} transition={{ duration:1, delay:i*.1 }}/>
          <motion.text x={33+barWidth} y={14+i*14} fill={c} fontSize="9" fontFamily="JetBrains Mono,monospace"
            initial={{ opacity:0 }} animate={{ opacity:1 }} transition={{ delay:i*.1+.6 }}>{s}</motion.text>
        </g>
      );
      })}
    </svg>
  );
}

function ScanViz({ color }: { color: string }) {
  const items=['✓ withAuth()','✓ apiHandler()','✗ processPayment()','✓ validateInput()','✓ routeMiddleware()'];
  return (
    <svg viewBox="0 0 220 80" style={{ width:'100%', height:80, opacity:.75, overflow:'hidden' }}>
      {items.map((l,i)=>(
        <text key={i} x="8" y={14+i*14} fill={l.startsWith('✗')?'#F472B6':'rgba(255,255,255,.5)'} fontSize="9" fontFamily="JetBrains Mono,monospace">{l}</text>
      ))}
      <motion.rect x="0" y="0" width="220" height="12" fill={color} fillOpacity=".12"
        animate={{ y:[0,70,0] }} transition={{ duration:2, repeat:Infinity, ease:'linear' }}/>
      <motion.rect x="0" y="0" width="220" height="2" fill={color} fillOpacity=".8"
        animate={{ y:[0,70,0] }} transition={{ duration:2, repeat:Infinity, ease:'linear' }}/>
    </svg>
  );
}

function BadgeViz({ color }: { color: string }) {
  return (
    <svg viewBox="0 0 220 80" style={{ width:'100%', height:80, overflow:'visible', opacity:.9 }}>
      <motion.g initial={{ scale:2.5, rotate:-12, opacity:0 }} animate={{ scale:1, rotate:0, opacity:1 }}
        transition={{ duration:.6, ease:[.22,1,.36,1] }} style={{ transformOrigin:'110px 40px' }}>
        <rect x="55" y="10" width="110" height="60" rx="12" fill="#0D0D0D" stroke={color} strokeWidth="2"/>
        <rect x="55" y="10" width="110" height="22" rx="12" fill={color}/>
        <rect x="55" y="22" width="110" height="10" fill={color}/>
        <text x="110" y="26" textAnchor="middle" fill="#000" fontSize="10" fontFamily="JetBrains Mono,monospace" fontWeight="900">VERIFIED</text>
        <text x="110" y="46" textAnchor="middle" fill={color} fontSize="20">✦</text>
        <text x="110" y="62" textAnchor="middle" fill="rgba(255,255,255,.4)" fontSize="8" fontFamily="JetBrains Mono,monospace">HMAC-SHA256</text>
      </motion.g>
    </svg>
  );
}

const VIZ_MAP: Record<string, React.FC<{ color: string }>> = { oauth:OAuthViz, filter:FilterViz, extract:ExtractViz, ai:AIViz, eval:EvalViz, scan:ScanViz, badge:BadgeViz };

// ─── Main Component ───────────────────────────────────────────────────────────
export function OrbitalPipeline() {
  const [active, setActive]       = useState(0);
  const [paused, setPaused]       = useState(false);
  const [progressKey, setProgressKey] = useState(0);
  const rafRef  = useRef<number | null>(null);
  const startRef= useRef<number>(performance.now());

  const step    = STEPS[active];
  const VizComp = VIZ_MAP[step.viz];
  const fillH   = active === 0 ? 0 : Math.round((active / (STEPS.length - 1)) * (STEPS.length - 1) * 50);

  const clearRaf = useCallback(() => { if (rafRef.current !== null) cancelAnimationFrame(rafRef.current); }, []);

  const tick = useCallback((now: number) => {
    if (now - (startRef.current ?? 0) >= DURATION) {
      setActive(prev => (prev + 1) % STEPS.length);
      setProgressKey(k => k + 1);
      startRef.current = performance.now();
    }
    rafRef.current = requestAnimationFrame(tick);
  }, []);

  useEffect(() => {
    if (paused) { clearRaf(); return; }
    startRef.current = performance.now();
    rafRef.current = requestAnimationFrame(tick);
    return clearRaf;
  }, [paused, tick, clearRaf]);

  useEffect(() => { setProgressKey(k => k + 1); }, [active]);

  const jump = (i: number) => { clearRaf(); setActive(i); setPaused(true); };

  return (
    <div style={{ fontFamily:'Outfit,sans-serif', color:'#fff', width:'100%' }}>

      <div style={{ display:'grid', gridTemplateColumns:'1fr 260px', gap:16, alignItems:'start' }}>

        {/* ── LEFT: Big step card ── */}
        <AnimatePresence mode="wait">
          <motion.div key={active}
            initial={{ opacity:0, y:18, scale:.97 }}
            animate={{ opacity:1, y:0, scale:1 }}
            exit={{ opacity:0, y:-12, scale:.97 }}
            transition={{ duration:.45, ease:[.22,1,.36,1] }}
            style={{ background:'#0C0C0C', border:`1px solid ${step.color}30`, borderRadius:20, padding:28, position:'relative', overflow:'hidden', minHeight:360 }}>

            {/* Ghost step number */}
            <div style={{ position:'absolute', top:-24, right:-6, fontSize:180, fontWeight:900, lineHeight:1, color:`${step.color}09`, letterSpacing:'-.05em', userSelect:'none', pointerEvents:'none', fontFamily:'Outfit,sans-serif' }}>{step.n}</div>

            {/* Glow */}
            <div style={{ position:'absolute', inset:0, background:`radial-gradient(ellipse at 15% 25%,${step.color}0a 0%,transparent 55%)`, borderRadius:20, pointerEvents:'none' }}/>

            <div style={{ position:'relative', zIndex:1 }}>
              {/* Tag */}
              <div style={{ display:'inline-flex', alignItems:'center', gap:7, padding:'4px 12px', borderRadius:100, background:`${step.color}15`, border:`1px solid ${step.color}35`, fontFamily:'JetBrains Mono,monospace', fontSize:11, fontWeight:700, color:step.color, letterSpacing:'.12em', marginBottom:18 }}>
                <span style={{ width:7, height:7, borderRadius:'50%', background:step.color, boxShadow:`0 0 8px ${step.color}` }}/>
                STEP {step.n} — {step.tag}
              </div>

              {/* Title */}
              <h2 style={{ fontSize:'clamp(22px,3.5vw,34px)', fontWeight:900, letterSpacing:'-.03em', margin:'0 0 12px', lineHeight:1.1 }}>
                <span style={{ color:step.color, marginRight:10 }}>{step.icon}</span>{step.label}
              </h2>

              {/* Desc */}
              <p style={{ fontSize:14, color:'rgba(255,255,255,.45)', lineHeight:1.65, margin:'0 0 20px', maxWidth:'90%' }}>{step.desc}</p>

              {/* Viz */}
              {VizComp && <div style={{ marginBottom:16 }}><VizComp color={step.color}/></div>}

              {/* Terminal */}
              <div style={{ background:'#060606', border:'1px solid rgba(255,255,255,.07)', borderRadius:12, padding:'14px 18px', fontFamily:'JetBrains Mono,monospace', fontSize:12 }}>
                <div style={{ display:'flex', gap:5, marginBottom:10, alignItems:'center' }}>
                  <span style={{ width:8, height:8, borderRadius:'50%', background:'#ff5f56' }}/>
                  <span style={{ width:8, height:8, borderRadius:'50%', background:'#ffbd2e' }}/>
                  <span style={{ width:8, height:8, borderRadius:'50%', background:'#27c93f' }}/>
                  <span style={{ marginLeft:8, fontSize:10, color:'rgba(255,255,255,.18)' }}>output.log</span>
                </div>
                {step.out.map(([key,val],i)=>(
                  <motion.div key={`${active}-${i}`}
                    initial={{ opacity:0, x:-6 }} animate={{ opacity:1, x:0 }}
                    transition={{ delay:i*.12, duration:.35 }}
                    style={{ display:'flex', gap:10, marginBottom:5, color:i===step.out.length-1?step.color:'rgba(255,255,255,.45)' }}>
                    <span style={{ color:'rgba(255,255,255,.18)' }}>&gt;</span>
                    <span style={{ color:'rgba(255,255,255,.3)', marginRight:4 }}>{key}:</span>
                    <span>{val}</span>
                  </motion.div>
                ))}
              </div>

              {/* Stat */}
              <div style={{ display:'inline-flex', alignItems:'center', padding:'7px 16px', background:`${step.color}12`, border:`1px solid ${step.color}30`, borderRadius:10, marginTop:18, fontFamily:'JetBrains Mono,monospace', fontSize:13, fontWeight:700, color:step.color }}>
                {step.stat}
              </div>
            </div>

            {/* Progress bar */}
            <div style={{ position:'absolute', bottom:0, left:0, right:0, height:3, background:'rgba(255,255,255,.05)' }}>
              {!paused && (
                <motion.div key={progressKey}
                  initial={{ width:'0%' }} animate={{ width:'100%' }}
                  transition={{ duration:DURATION/1000, ease:'linear' }}
                  style={{ height:'100%', background:step.color, boxShadow:`0 0 8px ${step.color}` }}/>
              )}
            </div>
          </motion.div>
        </AnimatePresence>

        {/* ── RIGHT: Timeline ── */}
        <div>
          <div style={{ position:'relative' }}>
            <div style={{ position:'absolute', left:14, top:14, width:1, height:(STEPS.length-1)*50, background:'rgba(255,255,255,.08)' }}/>
            <motion.div animate={{ height:fillH }} transition={{ duration:.6, ease:[.22,1,.36,1] }}
              style={{ position:'absolute', left:14, top:14, width:1, background:`linear-gradient(to bottom,#D4FF00,${step.color})`, boxShadow:`0 0 6px ${step.color}` }}/>

            {STEPS.map((s,i)=>{
              const state=i<active?'done':i===active?'active':'pending';
              return (
                <motion.div key={i} whileHover={{ x:4 }} onClick={()=>jump(i)}
                  style={{ display:'flex', alignItems:'flex-start', gap:12, padding:'10px 0 10px 32px', cursor:'pointer', opacity:state==='pending'?.3:1, position:'relative', transition:'opacity .3s' }}>
                  <div style={{ position:'absolute', left:6, top:10, width:16, height:16 }}>
                    <motion.div
                      animate={state==='active'?{scale:[1,1.3,1]}:{}}
                      transition={{ duration:1.4, repeat:Infinity }}
                      style={{ width:16, height:16, borderRadius:'50%', background:state==='pending'?'#1a1a1a':s.color, border:`2px solid ${state==='pending'?'rgba(255,255,255,.12)':s.color}`, boxShadow:state==='active'?`0 0 10px ${s.color}`:'none', display:'flex', alignItems:'center', justifyContent:'center', fontSize:8, fontWeight:900, color:'#000' }}>
                      {state==='done'?'✓':''}
                    </motion.div>
                    {state==='active'&&(
                      <motion.div animate={{ scale:[.5,2.6], opacity:[.9,0] }} transition={{ duration:1.4, repeat:Infinity }}
                        style={{ position:'absolute', inset:-2, borderRadius:'50%', border:`2px solid ${s.color}` }}/>
                    )}
                  </div>
                  <div>
                    <div style={{ display:'flex', alignItems:'center', gap:6 }}>
                      <span style={{ fontFamily:'JetBrains Mono,monospace', fontSize:10, fontWeight:700, color:state==='active'?s.color:'rgba(255,255,255,.25)' }}>{s.n}</span>
                      <span style={{ fontSize:13, fontWeight:700, lineHeight:1.3, color:state==='active'?'#fff':state==='done'?'rgba(255,255,255,.6)':'rgba(255,255,255,.35)' }}>{s.label}</span>
                    </div>
                    {state==='active'&&<motion.div initial={{opacity:0,y:3}} animate={{opacity:1,y:0}} style={{ fontSize:11, color:s.color, fontFamily:'JetBrains Mono,monospace', marginTop:2 }}>⟳ Running…</motion.div>}
                    {state==='done'&&<div style={{ fontSize:11, color:'rgba(255,255,255,.2)', fontFamily:'JetBrains Mono,monospace', marginTop:2 }}>{s.stat}</div>}
                  </div>
                </motion.div>
              );
            })}
          </div>

          <div style={{ paddingLeft:32, marginTop:16 }}>
            <motion.button whileHover={{ scale:1.04 }} whileTap={{ scale:.96 }}
              onClick={()=>setPaused(p=>!p)}
              style={{ background:'rgba(255,255,255,.06)', border:'1px solid rgba(255,255,255,.1)', borderRadius:8, padding:'6px 16px', color:'rgba(255,255,255,.45)', fontSize:12, fontFamily:'JetBrains Mono,monospace', cursor:'pointer' }}>
              {paused?'▶ Resume':'⏸ Pause'}
            </motion.button>
          </div>
        </div>
      </div>

      {/* ── Bottom dots ── */}
      <div style={{ display:'flex', gap:5, justifyContent:'center', marginTop:20 }}>
        {STEPS.map((s,i)=>(
          <motion.div key={i} onClick={()=>jump(i)}
            animate={{ width:i===active?28:8, background:i<=active?s.color:'rgba(255,255,255,.1)' }}
            transition={{ duration:.3 }}
            style={{ height:7, borderRadius:4, cursor:'pointer' }}/>
        ))}
      </div>
    </div>
  );
}

export default OrbitalPipeline;