import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'SkillProof — Prove You Built It',
  description: 'AI-powered developer credibility platform. Connect GitHub, answer code-grounded questions, get a verified badge.',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
      </head>
      <body>{children}</body>
    </html>
  )
}