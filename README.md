# corporate-drone-agent
An AI agent for corporate drones like me

Corporate Drone’s Agent is an open-source application for office workers who want a personal AI assistant they can run themselves. The idea is simple: download it, configure an LLM, start the application, open [http://localhost:8080](http://localhost:8080), and use a personal agent through a React interface served by a Spring Boot backend.

The backend will use Spring Boot and Spring AI, while the frontend will be built with React and packaged inside the Spring Boot project using frontend-maven-plugin. The application will be local-first: it can index local folders and continuously synchronize external sources such as Jira projects, Confluence spaces, GitHub repositories, and SharePoint sites into a local searchable knowledge base, so answers can be computed quickly from local data. Write-back to external sources would be planned for a future version.

The main UI is organized around projects. A project represents a workspace and contains conversations. Each conversation is a chat with its own context, history, and potentially scheduled jobs. Project-level settings may exist for things like an optional working folder or project-specific behavior, while global settings handle LLM configuration, sources, connectors, and indexing options.

Scheduled jobs run inside the context of a conversation. For example, after discussing a topic, the user can ask the agent to perform a task every day, and the result appears back in that same conversation. Conversations with scheduled jobs can be marked with a small clock icon and a badge showing how many jobs are attached.
