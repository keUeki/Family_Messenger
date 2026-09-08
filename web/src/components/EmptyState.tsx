import type { ReactNode } from 'react'
import styles from './EmptyState.module.css'
import { Button } from './Button'

interface Props {
  title: string
  description?: string
  actionLabel?: string
  onAction?: () => void
  icon?: ReactNode
}

export function EmptyState({ title, description, actionLabel, onAction, icon }: Props) {
  return (
    <div className={styles.wrap}>
      <div className={styles.orb}>{icon || <span className={styles.mark}>IC</span>}</div>
      <h3>{title}</h3>
      {description ? <p>{description}</p> : null}
      {actionLabel && onAction ? (
        <Button onClick={onAction} variant="secondary">
          {actionLabel}
        </Button>
      ) : null}
    </div>
  )
}
