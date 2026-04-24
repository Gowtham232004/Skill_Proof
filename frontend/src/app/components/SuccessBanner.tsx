'use client'

import BaseBanner from '@/app/components/BaseBanner'

interface SuccessBannerProps {
  message: string
  compact?: boolean
}

export default function SuccessBanner({ message, compact = false }: SuccessBannerProps) {
  return (
    <BaseBanner
      message={message}
      compact={compact}
      borderColor="rgba(52,211,153,0.4)"
      backgroundColor="rgba(52,211,153,0.1)"
      textColor="#34D399"
    />
  )
}
