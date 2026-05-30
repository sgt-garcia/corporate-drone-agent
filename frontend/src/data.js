export const initialProjects = [
  {
    id: "new-project",
    name: "New Project",
    conversations: [
      { id: "new-conversation", name: "New Conversation" }
    ]
  }
];

export const pages = {
  work: {
    title: "Work"
  },
  settings: {
    title: "Settings",
    menu: [
      "General",
      "Mistral AI",
      "Anthropic",
      "Groq",
      "Azure OpenAI",
      "Google Gemini",
      "Ollama",
      "OpenAI",
      "OpenAI (Official SDK)"
    ]
  }
};

export function findWorkItem(projects, activeId) {
  if (projects.length === 0) {
    return {
      item: null,
      name: "Work",
      type: "empty"
    };
  }

  for (const project of projects) {
    if (project.id === activeId) {
      return {
        item: project,
        name: project.name,
        type: "project"
      };
    }

    const conversation = project.conversations.find((item) => item.id === activeId);
    if (conversation) {
      return {
        item: conversation,
        name: conversation.name,
        project,
        type: "conversation"
      };
    }
  }

  return {
    item: projects[0],
    name: projects[0]?.name ?? "Work",
    type: "project"
  };
}

export function createInitialMessages(projects) {
  return projects.reduce((messages, project) => {
    project.conversations.forEach((conversation) => {
      messages[conversation.id] = createConversationMessages(conversation, project.name);
    });

    return messages;
  }, {});
}

export function createConversationMessages(conversation, projectName) {
  return [
    {
      id: `assistant-seed-${conversation.id}`,
      content: `Ready for **${conversation.name}** in ${projectName}.`,
      createdAt: new Date().toISOString(),
      role: "assistant"
    }
  ];
}
