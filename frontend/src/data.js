export const initialProjects = [
  {
    id: "northwind",
    name: "Northwind Migration",
    conversations: [
      { id: "northwind-kickoff", name: "Kickoff notes" },
      { id: "northwind-inventory", name: "Data inventory" },
      { id: "northwind-risks", name: "Risk review" }
    ]
  },
  {
    id: "atlas",
    name: "Atlas Reporting",
    conversations: [
      { id: "atlas-dashboard", name: "Dashboard scope" },
      { id: "atlas-finance", name: "Finance metrics" },
      { id: "atlas-release", name: "Release checklist" }
    ]
  },
  {
    id: "helix",
    name: "Helix Operations",
    conversations: [
      { id: "helix-status", name: "Weekly status" },
      { id: "helix-vendor", name: "Vendor follow-up" },
      { id: "helix-incident", name: "Incident summary" }
    ]
  }
];

export const pages = {
  work: {
    title: "Work"
  },
  settings: {
    title: "Settings",
    menu: ["General", "OpenAI", "OpenAI (Official)", "Azure OpenAI", "Ollama"]
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
