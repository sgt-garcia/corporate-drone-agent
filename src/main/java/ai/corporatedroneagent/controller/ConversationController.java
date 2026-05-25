package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.ConversationDto;
import ai.corporatedroneagent.dto.ConversationRequest;
import ai.corporatedroneagent.dto.MessageDto;
import ai.corporatedroneagent.dto.SendMessageRequest;
import ai.corporatedroneagent.service.ConversationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/{conversationId}")
    public ConversationDto getConversation(@PathVariable UUID conversationId) {
        return conversationService.get(conversationId);
    }

    @PutMapping("/{conversationId}")
    public ConversationDto updateConversation(
            @PathVariable UUID conversationId,
            @RequestBody ConversationRequest request
    ) {
        return conversationService.update(conversationId, request);
    }

    @PostMapping("/{conversationId}/messages")
    public MessageDto sendMessage(
            @PathVariable UUID conversationId,
            @RequestBody SendMessageRequest request
    ) {
        return conversationService.sendUserMessage(conversationId, request.content());
    }
}
