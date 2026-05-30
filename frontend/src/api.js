const jsonHeaders = {
  "Content-Type": "application/json"
};

export async function getProjects() {
  return request("/api/projects");
}

export async function createProject(project) {
  return request("/api/projects", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify(project)
  });
}

export async function saveProject(project) {
  return request(`/api/projects/${project.id}`, {
    method: "PUT",
    headers: jsonHeaders,
    body: JSON.stringify(project)
  });
}

export async function getProjectConversations(projectId) {
  return request(`/api/projects/${projectId}/conversations`);
}

export async function createConversation(projectId, conversation) {
  return request(`/api/projects/${projectId}/conversations`, {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify(conversation)
  });
}

export async function getConversation(conversationId) {
  return request(`/api/conversations/${conversationId}`);
}

export async function sendConversationMessage(conversationId, content) {
  return request(`/api/conversations/${conversationId}/messages`, {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ content })
  });
}

export async function getSettings() {
  return request("/api/settings");
}

export async function saveSettings(settings) {
  return request("/api/settings", {
    method: "PUT",
    headers: jsonHeaders,
    body: JSON.stringify(settings)
  });
}

export async function getOpenAiModels({ apiKey, provider, useSavedKey }) {
  return request("/api/settings/openai-models", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ apiKey, provider, useSavedKey })
  });
}

export async function getMistralModels({ apiKey, useSavedKey }) {
  return request("/api/settings/mistral-models", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ apiKey, useSavedKey })
  });
}

export async function getAnthropicModels({ apiKey, useSavedKey }) {
  return request("/api/settings/anthropic-models", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ apiKey, useSavedKey })
  });
}

export async function getGoogleGeminiModels({ apiKey, useSavedKey }) {
  return request("/api/settings/google-gemini-models", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ apiKey, useSavedKey })
  });
}

export async function getOllamaModels({ baseUrl }) {
  return request("/api/settings/ollama-models", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ baseUrl })
  });
}

export async function getAzureOpenAiDeployments({ apiKey, endpoint, useSavedKey }) {
  return request("/api/settings/azure-openai-deployments", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ apiKey, endpoint, useSavedKey })
  });
}

async function request(path, options) {
  const response = await fetch(path, options);

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed: ${response.status}`);
  }

  return response.json();
}
