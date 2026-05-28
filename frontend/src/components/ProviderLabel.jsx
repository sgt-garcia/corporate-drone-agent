import { EuFlagIcon } from "./EuFlagIcon.jsx";

export function ProviderLabel({ name }) {
  if (name !== "Mistral AI") {
    return name;
  }

  return (
    <span className="provider-label">
      <span>{name}</span>
      <EuFlagIcon />
    </span>
  );
}
