/** Logo: a tiny floor plan (one room, four desks, one free). Same drawing as the favicon. */
export default function BrandMark({ size = 32, className = '', inverted = false }) {
  const bg = inverted ? 'var(--sign-ink)' : 'var(--sign)'
  const fg = inverted ? 'var(--sign)' : 'var(--sign-ink)'
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" className={className} aria-hidden focusable="false">
      <rect width="32" height="32" rx="7" fill={bg} />
      <path d="M6 6h7M19 6h7v20H6V6" fill="none" stroke={fg} strokeWidth="1.6" strokeOpacity=".55" />
      <rect x="9" y="10" width="6" height="5" rx="1.5" fill={fg} />
      <rect x="17" y="10" width="6" height="5" rx="1.5" fill="var(--good)" />
      <rect x="9" y="18" width="6" height="5" rx="1.5" fill={fg} />
      <rect x="17" y="18" width="6" height="5" rx="1.5" fill={fg} />
    </svg>
  )
}
