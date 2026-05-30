const FLAGS = {
  china: "\u{1F1E8}\u{1F1F3}",
  eu: "\u{1F1EA}\u{1F1FA}",
  france: "\u{1F1EB}\u{1F1F7}",
  usa: "\u{1F1FA}\u{1F1F8}"
};

const PROVIDER_FLAGS = {
  Anthropic: [FLAGS.usa],
  "Azure OpenAI": [FLAGS.usa],
  DeepSeek: [FLAGS.china],
  Gemini: [FLAGS.usa],
  Groq: [FLAGS.usa],
  Mistral: [FLAGS.eu, FLAGS.france],
  Ollama: [FLAGS.usa],
  OpenAI: [FLAGS.usa],
  "OpenAI (SDK)": [FLAGS.usa]
};

export function ProviderLabel({ name }) {
  const flags = PROVIDER_FLAGS[name] ?? [];
  if (flags.length === 0) {
    return name;
  }

  return (
    <span className="provider-label">
      <span className="provider-label-text">{name}</span>
      {flags.map((flag) => (
        <span aria-hidden="true" className="provider-flag" key={flag}>
          {flag}
        </span>
      ))}
    </span>
  );
}

export function providerOptionLabel(name) {
  const flags = PROVIDER_FLAGS[name] ?? [];
  if (flags.length === 0) {
    return name;
  }
  return `${flags.join(" ")} ${name}`;
}
