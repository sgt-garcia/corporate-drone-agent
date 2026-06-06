package ai.corporatedroneagent.service;

interface ChatProvider {

    String providerId();

    ChatReply reply(ChatRequest request);
}
