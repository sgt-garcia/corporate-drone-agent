// Coffee-cup brand mark on a European-blue tile.
export function Logomark({ size = 30, radius = 8, className }) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label="Corporate Drone's Agent logomark"
      style={{ borderRadius: radius, display: "block", flexShrink: 0 }}
    >
      <rect width="48" height="48" rx="11" fill="#2150C9" />
      <path
        d="M20.5 11.5c-1.2 1.1-1.2 2.4 0 3.5s1.2 2.4 0 3.5"
        stroke="#FFFFFF"
        strokeWidth="2"
        strokeLinecap="round"
        opacity="0.85"
      />
      <path
        d="M27.5 11.5c-1.2 1.1-1.2 2.4 0 3.5s1.2 2.4 0 3.5"
        stroke="#FFFFFF"
        strokeWidth="2"
        strokeLinecap="round"
        opacity="0.85"
      />
      <path
        d="M15 22.5h15v6.5a5 5 0 0 1-5 5h-5a5 5 0 0 1-5-5v-6.5Z"
        stroke="#FFFFFF"
        strokeWidth="2.4"
        strokeLinejoin="round"
      />
      <path
        d="M30 24.5h2.5a3 3 0 0 1 0 6H30"
        stroke="#FFFFFF"
        strokeWidth="2.4"
        strokeLinecap="round"
      />
      <path
        d="M13.5 37.5h18"
        stroke="#FFFFFF"
        strokeWidth="2.4"
        strokeLinecap="round"
      />
    </svg>
  );
}
