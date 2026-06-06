UPDATE conversation_messages
SET role = 'error'
WHERE role = 'assistant'
  AND (
    content = 'The model did not respond within 60 seconds. Please try again, or check whether the selected provider is running.'
    OR content LIKE 'LLM request failed:%'
    OR content LIKE 'OpenAI request failed:%'
    OR content LIKE 'OpenAI (SDK) request failed:%'
    OR content LIKE 'Azure OpenAI request failed:%'
    OR content LIKE 'Ollama request failed:%'
    OR content LIKE 'Mistral request failed:%'
    OR content LIKE 'Gemini request failed:%'
    OR content LIKE 'Anthropic request failed:%'
    OR content LIKE 'Groq request failed:%'
    OR content LIKE 'DeepSeek request failed:%'
  );
