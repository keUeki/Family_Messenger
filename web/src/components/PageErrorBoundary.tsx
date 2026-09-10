import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from '@/components/Button'
import styles from './PageErrorBoundary.module.css'

interface Props {
  children: ReactNode
  resetKey?: string
}

interface State {
  error: Error | null
}

export class PageErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Page render failed', error, info.componentStack)
  }

  componentDidUpdate(prevProps: Props) {
    if (prevProps.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null })
    }
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div className={styles.wrap} role="alert">
        <p className={styles.eyebrow}>SOMETHING WENT WRONG</p>
        <h2>This page could not be opened</h2>
        <p>Rendering this page failed. You can try again, or go back to your chats and carry on.</p>
        <div className={styles.actions}>
          <Button onClick={() => this.setState({ error: null })}>Reload this page</Button>
          <Button variant="secondary" onClick={() => window.location.assign('/app/chat')}>
            Back to chat
          </Button>
        </div>
      </div>
    )
  }
}
