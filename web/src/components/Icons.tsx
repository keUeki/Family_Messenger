import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement> & { size?: number }

function base({ size = 20, ...rest }: IconProps) {
  return {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.75,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    ...rest,
  }
}

export function IconChat(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M4.5 7.5A3 3 0 0 1 7.5 4.5h9a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-4.2L8 19.5v-3H7.5a3 3 0 0 1-3-3v-6Z" />
    </svg>
  )
}

export function IconPeople(props: IconProps) {
  return (
    <svg {...base(props)}>
      <circle cx="9" cy="8" r="2.5" />
      <circle cx="16" cy="9" r="2" />
      <path d="M4.5 17.5c.8-2.4 2.7-3.5 4.5-3.5s3.7 1.1 4.5 3.5" />
      <path d="M13.2 14.2c1 .4 1.9 1.2 2.3 3.3" />
    </svg>
  )
}

export function IconGroup(props: IconProps) {
  return (
    <svg {...base(props)}>
      <circle cx="8.5" cy="9" r="2.25" />
      <circle cx="15.5" cy="9" r="2.25" />
      <circle cx="12" cy="14.5" r="2.25" />
      <path d="M4.2 17.8c.7-1.9 2.2-2.8 4.3-2.8" />
      <path d="M15.5 15c2.1 0 3.6.9 4.3 2.8" />
      <path d="M9.2 18.5c.7-1.2 1.7-1.8 2.8-1.8s2.1.6 2.8 1.8" />
    </svg>
  )
}

export function IconBell(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M6.5 16.5h11l-1.2-1.5V11a4.3 4.3 0 0 0-8.6 0v4l-1.2 1.5Z" />
      <path d="M10 18.2a2 2 0 0 0 4 0" />
    </svg>
  )
}

export function IconUser(props: IconProps) {
  return (
    <svg {...base(props)}>
      <circle cx="12" cy="8.5" r="3" />
      <path d="M5.5 18.5c1.2-2.8 3.4-4 6.5-4s5.3 1.2 6.5 4" />
    </svg>
  )
}

export function IconSmile(props: IconProps) {
  return (
    <svg {...base(props)}>
      <circle cx="12" cy="12" r="8" />
      <path d="M9 14.2c.8 1 1.8 1.5 3 1.5s2.2-.5 3-1.5" />
      <circle cx="9.2" cy="10" r="1" fill="currentColor" stroke="none" />
      <circle cx="14.8" cy="10" r="1" fill="currentColor" stroke="none" />
    </svg>
  )
}

export function IconImage(props: IconProps) {
  return (
    <svg {...base(props)}>
      <rect x="4.5" y="5.5" width="15" height="13" rx="2.5" />
      <circle cx="9.5" cy="10" r="1.4" />
      <path d="M5.5 16.5 10 12.5l3 2.5 2.2-2.2 3.3 3.7" />
    </svg>
  )
}

export function IconPacket(props: IconProps) {
  return (
    <svg {...base(props)}>
      <rect x="6" y="5" width="12" height="14" rx="2.5" />
      <path d="M6 10.5h12" />
      <circle cx="12" cy="10.5" r="1.8" />
    </svg>
  )
}

export function IconSend(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M4.5 19.5 20 12 4.5 4.5 7.5 12l-3 7.5Z" />
      <path d="M7.5 12h5.5" />
    </svg>
  )
}

export function IconSearch(props: IconProps) {
  return (
    <svg {...base(props)}>
      <circle cx="11" cy="11" r="5.5" />
      <path d="M15.5 15.5 19 19" />
    </svg>
  )
}

export function IconRefresh(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M19 12a7 7 0 1 1-1.8-4.6" />
      <path d="M19 5v4.5h-4.5" />
    </svg>
  )
}
