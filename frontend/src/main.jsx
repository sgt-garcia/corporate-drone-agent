import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
// Self-hosted IBM Plex (weights the UI uses) so the app renders the design's
// typeface offline and never calls out to Google Fonts. These bundle every
// subset (latin, latin-ext, cyrillic, greek, vietnamese) each behind its own
// unicode-range, so any script in user content renders in IBM Plex while the
// browser still downloads only the subsets a page actually uses.
import "@fontsource/ibm-plex-sans/400.css";
import "@fontsource/ibm-plex-sans/500.css";
import "@fontsource/ibm-plex-sans/600.css";
import "@fontsource/ibm-plex-mono/400.css";
import App from "./App.jsx";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <App />
  </StrictMode>
);
