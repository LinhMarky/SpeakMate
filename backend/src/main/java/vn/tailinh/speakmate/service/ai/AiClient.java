package vn.tailinh.speakmate.service.ai;

// Tách lời gọi AI bên ngoài để test có thể thay thế mà không sửa luồng nghiệp vụ.
public interface AiClient {

    String reply(AiConversation conversation);

    AiReview review(AiConversation conversation);
}
