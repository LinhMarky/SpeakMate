package vn.tailinh.speakmate.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tailinh.speakmate.entity.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);
}
