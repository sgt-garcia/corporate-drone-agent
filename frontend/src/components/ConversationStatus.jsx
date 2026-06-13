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

// Per-state fill/stroke color for the shape itself. Distinct from STATUS_META's
// text colors, which run a step darker for legibility at small label sizes.
const STATUS_COLOR = {
  success: "var(--success-500)",
  running: "var(--blue-600)",
  review: "var(--warning-500)",
  error: "var(--danger-500)",
  idle: "var(--gray-300)"
};

export function StatusShape({ status }) {
  const key = statusKey(status);
  const { label } = STATUS_META[key];
  const color = STATUS_COLOR[key];
  // Crisp 2.6px glyph strokes scaled into the 11px filled marks.
  const glyphStroke = {
    stroke: "var(--white)",
    strokeWidth: 2.6,
    strokeLinecap: "round",
    strokeLinejoin: "round",
    fill: "none"
  };

  let shape;
  switch (key) {
    case "running":
      shape = (
        <span
          className="cda-pulse"
          style={{ width: 9, height: 9, borderRadius: "50%", background: color, display: "block" }}
        />
      );
      break;
    case "review":
      shape = (
        <span
          style={{
            width: 9,
            height: 9,
            borderRadius: "50%",
            border: "2.25px solid " + color,
            boxSizing: "border-box",
            display: "block"
          }}
        />
      );
      break;
    case "error":
      // Sharp filled diamond (angular — unmistakable vs. a round dot) + an ✕.
      shape = (
        <span
          style={{
            width: 11,
            height: 11,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            transform: "rotate(45deg)",
            borderRadius: 1.5,
            background: color
          }}
        >
          <svg
            width="11"
            height="11"
            viewBox="0 0 11 11"
            style={{ transform: "rotate(-45deg)" }}
            aria-hidden="true"
          >
            <path d="M3.4 3.4 7.6 7.6 M7.6 3.4 3.4 7.6" {...glyphStroke} />
          </svg>
        </span>
      );
      break;
    case "idle":
      shape = (
        <span
          style={{
            width: 7,
            height: 7,
            borderRadius: "50%",
            border: "1.5px solid " + color,
            boxSizing: "border-box",
            display: "block"
          }}
        />
      );
      break;
    default:
      // success — filled round dot + a check.
      shape = (
        <span
          style={{
            width: 11,
            height: 11,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            borderRadius: "50%",
            background: color
          }}
        >
          <svg width="11" height="11" viewBox="0 0 11 11" aria-hidden="true">
            <path d="M3 5.7 4.7 7.4 8 3.8" {...glyphStroke} />
          </svg>
        </span>
      );
  }

  return (
    <span className="status-shape" role="img" aria-label={label} title={label}>
      {shape}
    </span>
  );
}
