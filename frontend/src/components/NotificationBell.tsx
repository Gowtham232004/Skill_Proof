'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import {
  getNotificationCount,
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
} from '@/lib/api'

function timeAgo(value?: string) {
  if (!value) {
    return 'now'
  }

  const now = Date.now()
  const created = new Date(value).getTime()
  const deltaSec = Math.max(0, Math.floor((now - created) / 1000))
  if (deltaSec < 60) return `${deltaSec}s ago`
  if (deltaSec < 3600) return `${Math.floor(deltaSec / 60)}m ago`
  if (deltaSec < 86400) return `${Math.floor(deltaSec / 3600)}h ago`
  return `${Math.floor(deltaSec / 86400)}d ago`
}

function toInternalPath(url: string) {
  if (!url) return '/'
  if (url.startsWith('/')) return url

  try {
    const parsed = new URL(url)
    return `${parsed.pathname}${parsed.search}${parsed.hash}`
  } catch {
    return '/'
  }
}

export default function NotificationBell() {
  const router = useRouter()
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [items, setItems] = useState<NotificationItem[]>([])

  const pollUnread = async () => {
    try {
      const response = await getNotificationCount()
      setUnreadCount(Number(response.data?.unreadCount || 0))
    } catch {
      setUnreadCount(0)
    }
  }

  const loadNotifications = async () => {
    setLoading(true)
    try {
      const response = await getNotifications()
      const rows = Array.isArray(response.data) ? response.data : []
      setItems(rows)
    } catch {
      setItems([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void pollUnread()
    const timer = window.setInterval(() => {
      void pollUnread()
    }, 10000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (open) {
      void loadNotifications()
    }
  }, [open])

  const unreadLabel = useMemo(() => (unreadCount > 99 ? '99+' : `${unreadCount}`), [unreadCount])

  const onOpenNotification = async (notification: NotificationItem) => {
    try {
      if (!notification.isRead) {
        await markNotificationRead(notification.id)
      }
    } catch {
      // no-op: navigation should still proceed
    }

    setOpen(false)
    void pollUnread()
    router.push(toInternalPath(notification.actionUrl || '/'))
  }

  const onMarkAll = async () => {
    try {
      await markAllNotificationsRead()
      setItems((prev) => prev.map((item) => ({ ...item, isRead: true })))
      setUnreadCount(0)
    } catch {
      // no-op
    }
  }

  return (
    <div style={{ position: 'relative' }}>
      <button
        onClick={() => setOpen((prev) => !prev)}
        style={{
          position: 'relative',
          width: 36,
          height: 36,
          borderRadius: 10,
          border: '1px solid rgba(255,255,255,0.18)',
          background: 'rgba(255,255,255,0.04)',
          color: 'rgba(255,255,255,0.9)',
          cursor: 'pointer',
          fontSize: 16,
        }}
        aria-label="Notifications"
      >
        🔔
        {unreadCount > 0 && (
          <span
            style={{
              position: 'absolute',
              top: -6,
              right: -6,
              minWidth: 18,
              height: 18,
              borderRadius: 999,
              background: '#D4FF00',
              color: '#000',
              fontSize: 10,
              fontWeight: 800,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '0 4px',
              fontFamily: 'JetBrains Mono, monospace',
            }}
          >
            {unreadLabel}
          </span>
        )}
      </button>

      {open && (
        <div
          style={{
            position: 'absolute',
            right: 0,
            marginTop: 8,
            width: 360,
            maxHeight: 420,
            overflowY: 'auto',
            borderRadius: 12,
            border: '1px solid rgba(255,255,255,0.14)',
            background: 'rgba(8,8,8,0.98)',
            backdropFilter: 'blur(18px)',
            zIndex: 220,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 12px', borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
            <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.8)', fontFamily: 'JetBrains Mono, monospace' }}>
              Notifications
            </div>
            <button
              onClick={() => { void onMarkAll() }}
              style={{ border: 'none', background: 'transparent', color: '#D4FF00', fontSize: 11, cursor: 'pointer', fontFamily: 'JetBrains Mono, monospace' }}
            >
              Mark all read
            </button>
          </div>

          {loading ? (
            <div style={{ padding: 12, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>
              Loading notifications...
            </div>
          ) : items.length === 0 ? (
            <div style={{ padding: 12, color: 'rgba(255,255,255,0.45)', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>
              No notifications yet.
            </div>
          ) : (
            <div>
              {items.map((item) => (
                <button
                  key={item.id}
                  onClick={() => { void onOpenNotification(item) }}
                  style={{
                    width: '100%',
                    textAlign: 'left',
                    border: 'none',
                    borderBottom: '1px solid rgba(255,255,255,0.06)',
                    background: item.isRead ? 'transparent' : 'rgba(212,255,0,0.07)',
                    color: 'inherit',
                    cursor: 'pointer',
                    padding: '10px 12px',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                    <div style={{ color: '#fff', fontSize: 13, fontWeight: 700 }}>{item.title}</div>
                    <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}>
                      {timeAgo(item.createdAt)}
                    </div>
                  </div>
                  <div style={{ color: 'rgba(255,255,255,0.65)', fontSize: 12, marginTop: 4 }}>
                    {item.message}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
