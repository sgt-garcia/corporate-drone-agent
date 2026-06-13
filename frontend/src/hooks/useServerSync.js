import { useEffect, useRef } from "react";

function deepEqual(a, b) {
  if (a === b) {
    return true;
  }
  if (typeof a !== "object" || typeof b !== "object" || a === null || b === null) {
    return false;
  }
  if (Array.isArray(a) || Array.isArray(b)) {
    if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) {
      return false;
    }
    return a.every((item, index) => deepEqual(item, b[index]));
  }
  const keysA = Object.keys(a);
  const keysB = Object.keys(b);
  if (keysA.length !== keysB.length) {
    return false;
  }
  return keysA.every((key) => deepEqual(a[key], b[key]));
}

// Mirrors a server-provided value into local editable state, but only fires
// `onChange` when the value's *content* actually changes. Background SSE
// refreshes (scheduled scans publishing settings-updated/projects-updated)
// hand us a new object identity with identical content; syncing on identity
// alone would wipe whatever the user was typing mid-edit.
export function useServerSync(serverValue, onChange) {
  const last = useRef(serverValue);
  const handler = useRef(onChange);
  handler.current = onChange;

  useEffect(() => {
    if (deepEqual(last.current, serverValue)) {
      return;
    }
    last.current = serverValue;
    handler.current(serverValue);
  }, [serverValue]);
}
