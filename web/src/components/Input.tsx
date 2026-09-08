import type { InputHTMLAttributes, TextareaHTMLAttributes } from 'react'
import styles from './Input.module.css'

export function Input(props: InputHTMLAttributes<HTMLInputElement> & { label?: string }) {
  const { label, className, ...rest } = props
  return (
    <label className={styles.field}>
      {label ? <span className={styles.label}>{label}</span> : null}
      <input className={[styles.input, className].filter(Boolean).join(' ')} {...rest} />
    </label>
  )
}

export function TextArea(props: TextareaHTMLAttributes<HTMLTextAreaElement> & { label?: string }) {
  const { label, className, ...rest } = props
  return (
    <label className={styles.field}>
      {label ? <span className={styles.label}>{label}</span> : null}
      <textarea className={[styles.textarea, className].filter(Boolean).join(' ')} {...rest} />
    </label>
  )
}
