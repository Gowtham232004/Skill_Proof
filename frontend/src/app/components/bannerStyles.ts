import type { CSSProperties } from 'react'

export const bannerCodeStyle: CSSProperties = {
  marginBottom: 6,
  letterSpacing: '0.08em',
  opacity: 0.85,
}

interface BannerContainerOptions {
  compact: boolean
  borderColor: string
  backgroundColor: string
  textColor: string
}

export function buildBannerContainerStyle({
  compact,
  borderColor,
  backgroundColor,
  textColor,
}: BannerContainerOptions): CSSProperties {
  return {
    marginTop: compact ? 0 : 10,
    padding: compact ? '8px 10px' : '10px 12px',
    borderRadius: 10,
    border: `1px solid ${borderColor}`,
    background: backgroundColor,
    color: textColor,
    fontSize: compact ? 11 : 12,
    fontFamily: 'JetBrains Mono, monospace',
  }
}

interface BannerActionOptions {
  borderColor: string
  textColor: string
}

export function buildBannerActionStyle({
  borderColor,
  textColor,
}: BannerActionOptions): CSSProperties {
  return {
    marginTop: 8,
    padding: '6px 10px',
    borderRadius: 7,
    border: `1px solid ${borderColor}`,
    background: 'transparent',
    color: textColor,
    fontSize: 11,
    fontWeight: 700,
    cursor: 'pointer',
    fontFamily: 'JetBrains Mono, monospace',
  }
}
