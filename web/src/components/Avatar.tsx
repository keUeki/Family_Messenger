import { useEffect, useState } from 'react'
import styles from './Avatar.module.css'
import { initials } from '@/utils'

interface Props {
  src?: string
  name?: string
  size?: number
}

export function Avatar({ src, name, size = 40 }: Props) {
  const [failed, setFailed] = useState(false)

  useEffect(() => setFailed(false), [src])

  if (src && !failed) {
    return (
      <img
        className={styles.avatar}
        src={src}
        alt={name ? `${name}的头像` : '用户头像'}
        style={{ width: size, height: size }}
        onError={() => setFailed(true)}
      />
    )
  }
  return (
    <div className={styles.fallback} style={{ width: size, height: size, fontSize: size * 0.4 }}>
      {initials(name)}
    </div>
  )
}
