package ai.corporatedroneagent.service;

interface ChatProvider {

    String providerId();

    String reply(ChatRequest request);
}
