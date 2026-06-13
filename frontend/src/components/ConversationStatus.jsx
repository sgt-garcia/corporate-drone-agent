// Conversation run-status indicator. Each state is encoded by SHAPE + GLYPH +
// COLOR (not color alone) with an aria-label, so it survives color-blindness and
// reads to assistive tech:
//   success → green filled circle + check   error  → red diamond + ✕
//   running → pulsing blue dot               review → amber hollow ring
//   idle    → faint gray ring
export const STATUS_META = {
  running: { word: "Running", label: "Running" },
  review: { word: "Review", label: "Needs review" },
  success: { word: "Done", label: "Done" },
  error: { word: "Failed", label: "Failed" },
  idle: { word: "Idle", label: "Idle" }
};

export function statusKey(status) {
  return STATUS_META[status] ? status : "idle";
}

export function StatusShape({ status }) {
  const key = statusKey(status);
  const { label } = STATUS_META[key];
  return (
    <span className="status-shape" role="img" aria-label={label} title={label}>
      <svg
        width="14"
        height="14"
        viewBox="0 0 16 16"
        className={key === "running" ? "cda-pulse" : undefined}
        aria-hidden="true"
      >
        {key === "success" && (
          <>
            <circle cx="8" cy="8" r="6.5" fill="var(--success-600)" />
            <path
              d="M5.1 8.2l1.9 1.9 3.9-4.1"
              fill="none"
              stroke="#fff"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </>
        )}
        {key === "error" && (
          <>
            <path d="M8 1.4 14.6 8 8 14.6 1.4 8Z" fill="var(--danger-600)" />
            <path
              d="M6 6l4 4M10 6l-4 4"
              stroke="#fff"
              strokeWidth="1.5"
              strokeLinecap="round"
            />
          </>
        )}
        {key === "running" && <circle cx="8" cy="8" r="4.5" fill="var(--blue-500)" />}
        {key === "review" && (
          <circle cx="8" cy="8" r="4.5" fill="none" stroke="var(--warning-500)" strokeWidth="2" />
        )}
        {key === "idle" && (
          <circle cx="8" cy="8" r="3.5" fill="none" stroke="var(--gray-300)" strokeWidth="1.75" />
        )}
      </svg>
    </span>
  );
}
