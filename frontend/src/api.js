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

export async function deleteProject(projectId) {
  return requestNoContent(`/api/projects/${projectId}`, { method: "DELETE" });
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

export async function renameConversation(conversationId, name) {
  return request(`/api/conversations/${conversationId}`, {
    method: "PUT",
    headers: jsonHeaders,
    body: JSON.stringify({ name })
  });
}

export async function deleteConversation(conversationId) {
  return requestNoContent(`/api/conversations/${conversationId}`, {
    method: "DELETE"
  });
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

export async function getKnowledgeFolders() {
  return request("/api/settings/knowledge/local-folders");
}

export async function addKnowledgeFolder(path) {
  return request("/api/settings/knowledge/local-folders", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ path })
  });
}

export async function removeKnowledgeFolder(folderId) {
  return requestNoContent(`/api/settings/knowledge/local-folders/${folderId}`, {
    method: "DELETE"
  });
}

export async function scanKnowledgeFolder(folderId) {
  return request(`/api/settings/knowledge/local-folders/${folderId}/scan`, {
    method: "POST"
  });
}

export async function pauseKnowledgeFolder(folderId) {
  return request(`/api/settings/knowledge/local-folders/${folderId}/pause`, {
    method: "POST"
  });
}

export async function resumeKnowledgeFolder(folderId) {
  return request(`/api/settings/knowledge/local-folders/${folderId}/resume`, {
    method: "POST"
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

export async function getGeminiModels({ apiKey, useSavedKey }) {
  return request("/api/settings/gemini-models", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ apiKey, useSavedKey })
  });
}

export async function getGroqModels({ apiKey, useSavedKey }) {
  return request("/api/settings/groq-models", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ apiKey, useSavedKey })
  });
}

export async function getDeepSeekModels({ apiKey, useSavedKey }) {
  return request("/api/settings/deepseek-models", {
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
    throw new Error(await errorMessage(response));
  }

  return response.json();
}

async function requestNoContent(path, options) {
  const response = await fetch(path, options);

  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
}

async function errorMessage(response) {
  const fallback = `Request failed: ${response.status}`;
  const text = await response.text();
  if (!text) {
    return fallback;
  }

  try {
    const body = JSON.parse(text);
    return body.message || body.detail || body.error || fallback;
  } catch {
    return text;
  }
}
