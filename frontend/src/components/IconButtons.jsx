export function OverflowButton({ label }) {
  return (
    <button className="overflow-button" type="button" aria-label={label}>
      {"\u2026"}
    </button>
  );
}

export function AddButton({ label, onClick }) {
  return (
    <button className="icon-button" type="button" aria-label={label} onClick={onClick}>
      +
    </button>
  );
}
