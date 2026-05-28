export function EuFlagIcon({ className = "" }) {
  const classNames = ["eu-flag-icon", className].filter(Boolean).join(" ");

  return (
    <svg
      aria-hidden="true"
      className={classNames}
      focusable="false"
      viewBox="0 0 28 18"
      xmlns="http://www.w3.org/2000/svg"
    >
      <rect width="28" height="18" rx="3" fill="#1f5fbf" />
      {EU_STAR_POINTS.map(([cx, cy]) => (
        <circle cx={cx} cy={cy} fill="#ffcc00" key={`${cx}-${cy}`} r="1.05" />
      ))}
    </svg>
  );
}

const EU_STAR_POINTS = [
  [14, 3.8],
  [16.6, 4.5],
  [18.5, 6.4],
  [19.2, 9],
  [18.5, 11.6],
  [16.6, 13.5],
  [14, 14.2],
  [11.4, 13.5],
  [9.5, 11.6],
  [8.8, 9],
  [9.5, 6.4],
  [11.4, 4.5]
];
