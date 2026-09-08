import styles from './Skeleton.module.css'

interface Props {
  width?: string | number
  height?: string | number
  radius?: number
  className?: string
}

export function Skeleton({ width = '100%', height = 16, radius = 8, className }: Props) {
  return (
    <div
      className={[styles.skeleton, className].filter(Boolean).join(' ')}
      style={{ width, height, borderRadius: radius }}
    />
  )
}

export function SkeletonList({ rows = 4 }: { rows?: number }) {
  return (
    <div className={styles.list}>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className={styles.row}>
          <Skeleton width={44} height={44} radius={22} />
          <div className={styles.meta}>
            <Skeleton width="46%" height={14} />
            <Skeleton width="72%" height={12} />
          </div>
        </div>
      ))}
    </div>
  )
}
