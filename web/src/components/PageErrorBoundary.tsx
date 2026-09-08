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
        <h2>这一页暂时打不开</h2>
        <p>刚才的页面渲染失败了。可以重试，或先回到聊天继续使用。</p>
        <div className={styles.actions}>
          <Button onClick={() => this.setState({ error: null })}>重新加载此页</Button>
          <Button variant="secondary" onClick={() => window.location.assign('/app/chat')}>
            回到聊天
          </Button>
        </div>
      </div>
    )
  }
}
