import styles from './Spinner.module.css'

export function Spinner({ size = 18 }: { size?: number }) {
  return (
    <span
      className={styles.spinner}
      style={{ width: size, height: size, borderWidth: Math.max(2, size / 9) }}
      aria-label="加载中"
    />
  )
}
