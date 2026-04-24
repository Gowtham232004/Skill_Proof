'use client'

import BaseBanner from '@/app/components/BaseBanner'

interface InfoBannerProps {
  message: string
  compact?: boolean
}

export default function InfoBanner({ message, compact = false }: InfoBannerProps) {
  return (
    <BaseBanner
      message={message}
      compact={compact}
      borderColor="rgba(96,165,250,0.4)"
      backgroundColor="rgba(96,165,250,0.1)"
      textColor="#60A5FA"
    />
  )
}
