import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { ToastHost } from '@/components/Toast'
import './styles/tokens.css'

if (import.meta.env.VITE_A11Y_TEST === '1') {
  void import('./a11yTest')
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
      <ToastHost />
    </BrowserRouter>
  </StrictMode>,
)
