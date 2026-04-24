'use client'

import {
  bannerCodeStyle,
  buildBannerActionStyle,
  buildBannerContainerStyle,
} from '@/app/components/bannerStyles'

export interface BaseBannerProps {
  message: string
  compact?: boolean
  code?: string
  actionLabel?: string
  onAction?: () => void
  borderColor: string
  backgroundColor: string
  textColor: string
  actionBorderColor?: string
}

export default function BaseBanner({
  message,
  compact = false,
  code,
  actionLabel,
  onAction,
  borderColor,
  backgroundColor,
  textColor,
  actionBorderColor,
}: BaseBannerProps) {
  const containerStyle = buildBannerContainerStyle({
    compact,
    borderColor,
    backgroundColor,
    textColor,
  })

  const actionStyle = buildBannerActionStyle({
    borderColor: actionBorderColor || borderColor,
    textColor,
  })

  return (
    <div style={containerStyle}>
      {code && (
        <div style={bannerCodeStyle}>
          {code}
        </div>
      )}
      <div>{message}</div>
      {actionLabel && onAction && (
        <button
          onClick={onAction}
          style={actionStyle}
        >
          {actionLabel}
        </button>
      )}
    </div>
  )
}
