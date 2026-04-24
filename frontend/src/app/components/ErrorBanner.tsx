'use client'

import BaseBanner from '@/app/components/BaseBanner'

interface ErrorBannerProps {
  message: string
  code?: string
  compact?: boolean
  actionLabel?: string
  onAction?: () => void
}

export default function ErrorBanner({
  message,
  code,
  compact = false,
  actionLabel,
  onAction,
}: ErrorBannerProps) {
  return (
    <BaseBanner
      message={message}
      code={code}
      compact={compact}
      actionLabel={actionLabel}
      onAction={onAction}
      borderColor="rgba(244,114,182,0.4)"
      backgroundColor="rgba(244,114,182,0.08)"
      textColor="#F472B6"
      actionBorderColor="rgba(244,114,182,0.45)"
    />
  )
}
