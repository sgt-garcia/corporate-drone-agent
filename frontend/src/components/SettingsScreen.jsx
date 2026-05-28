export function SettingsScreen({
  children,
  onReload,
  onSave,
  subtitle,
  title,
  titleLabel = title
}) {
  return (
    <section className="settings-screen" aria-labelledby="settings-screen-title">
      <div className="settings-header">
        <div>
          <p>{subtitle}</p>
          <h1 id="settings-screen-title">{title}</h1>
        </div>
        <div className="settings-actions" aria-label={`${titleLabel} actions`}>
          <button type="button" onClick={onReload}>
            Reload
          </button>
          <button className="primary-action" type="button" onClick={onSave}>
            Save
          </button>
        </div>
      </div>
      <div className="settings-panel">{children}</div>
    </section>
  );
}
