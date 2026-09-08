import type { ButtonHTMLAttributes, ReactNode } from 'react'
import styles from './Button.module.css'
import { Spinner } from './Spinner'

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  children: ReactNode
  block?: boolean
  loading?: boolean
}

export function Button({
  variant = 'primary',
  children,
  block,
  loading,
  className,
  disabled,
  ...rest
}: Props) {
  return (
    <button
      className={[styles.btn, styles[variant], block ? styles.block : '', className]
        .filter(Boolean)
        .join(' ')}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? <Spinner size={16} /> : null}
      {children}
    </button>
  )
}
