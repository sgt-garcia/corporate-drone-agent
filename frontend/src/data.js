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
