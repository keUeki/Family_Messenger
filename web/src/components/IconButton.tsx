import type { ButtonHTMLAttributes, ReactNode } from 'react'
import styles from './IconButton.module.css'

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode
  active?: boolean
}

export function IconButton({ children, active, className, ...rest }: Props) {
  return (
    <button
      type="button"
      className={[styles.btn, active ? styles.active : '', className].filter(Boolean).join(' ')}
      {...rest}
    >
      {children}
    </button>
  )
}
