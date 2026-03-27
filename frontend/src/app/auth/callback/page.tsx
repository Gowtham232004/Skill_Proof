'use client'
import { useEffect } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Suspense } from 'react'

function CallbackInner() {
  const router = useRouter()
  const params = useSearchParams()

  useEffect(() => {
    if (!params) {
      router.push('/')
      return
    }

    // Method 1: Copilot version — data passed as URL param
    const dataParam = params.get('data')
    if (dataParam) {
      try {
        const data = JSON.parse(decodeURIComponent(dataParam))
        if (data.accessToken) {
          localStorage.setItem('sp_token', data.accessToken)
          localStorage.setItem('sp_user', JSON.stringify({
            userId: data.userId,
            githubUsername: data.githubUsername,
            avatarUrl: data.avatarUrl,
            displayName: data.displayName,
            role: data.role,
            plan: data.plan,
          }))
          router.push('/verify')
          return
        }
      } catch (e) {
        console.error('Failed to parse data param', e)
      }
    }

    // Method 2: Original version — code in URL, call backend
    const code = params.get('code')
    if (code) {
      fetch(`http://localhost:8080/api/auth/github/callback?code=${code}`)
        .then(res => res.json())
        .then(data => {
          if (data.accessToken) {
            localStorage.setItem('sp_token', data.accessToken)
            localStorage.setItem('sp_user', JSON.stringify({
              userId: data.userId,
              githubUsername: data.githubUsername,
              avatarUrl: data.avatarUrl,
              displayName: data.displayName,
              role: data.role,
              plan: data.plan,
            }))
          }
          router.push('/verify')
        })
        .catch(() => router.push('/'))
      return
    }

    // No params — redirect home
    router.push('/')
  }, [params, router])

  return (
    <div style={{ minHeight: '100vh', background: '#000', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'Outfit, sans-serif' }}>
      <div style={{ textAlign: 'center' }}>
        <div style={{ width: 48, height: 48, border: '2px solid rgba(212,255,0,0.3)', borderTopColor: '#D4FF00', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 20px' }} />
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
        <p style={{ color: 'rgba(255,255,255,0.4)', fontSize: 14, fontFamily: 'JetBrains Mono, monospace' }}>Completing authentication...</p>
      </div>
    </div>
  )
}

export default function AuthCallbackPage() {
  return (
    <Suspense fallback={
      <div style={{ minHeight: '100vh', background: '#000', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ width: 48, height: 48, border: '2px solid rgba(212,255,0,0.3)', borderTopColor: '#D4FF00', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      </div>
    }>
      <CallbackInner />
    </Suspense>
  )
}