/** Legend for line charts: a short line key per series (mirrors the mark). */
export default function Legend({ items }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm" aria-label="Chú giải">
      {items.map((it) => (
        <li key={it.key} className="flex items-center gap-1.5 secondary">
          <span className="line-key" style={{ background: it.color, width: 16 }} />
          {it.label}
        </li>
      ))}
    </ul>
  )
}
