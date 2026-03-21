'use client'
import { useState, useEffect } from 'react'

interface User {
  userId: number
  githubUsername: string
  avatarUrl: string
  displayName: string
  role: string
  plan: string
}

export function useAuth() {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const stored = localStorage.getItem('sp_user')
    const token = localStorage.getItem('sp_token')
    if (stored && token) {
      try { setUser(JSON.parse(stored)) }
      catch { localStorage.clear() }
    }
    setLoading(false)
  }, [])

  const login = (token: string, userData: User) => {
    localStorage.setItem('sp_token', token)
    localStorage.setItem('sp_user', JSON.stringify(userData))
    setUser(userData)
  }

  const logout = () => {
    localStorage.removeItem('sp_token')
    localStorage.removeItem('sp_user')
    setUser(null)
  }

  return { user, loading, login, logout, isLoggedIn: !!user }
}