package vn.tailinh.speakmate.service.ai;

import java.util.List;
import vn.tailinh.speakmate.dto.response.conversation.MessageResponse;
import vn.tailinh.speakmate.util.constant.EnglishLevel;

public record AiConversation(
    String topicName,
    String scenario,
    EnglishLevel profileLevel,
    EnglishLevel practiceLevel,
    List<MessageResponse> messages) {
}
