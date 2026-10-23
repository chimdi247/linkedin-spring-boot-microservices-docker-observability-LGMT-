package com.linkedin.searchservice.service;

import com.linkedin.searchservice.model.PostDocument;
import com.linkedin.searchservice.model.UserDocument;
import com.linkedin.searchservice.repository.PostSearchRepository;
import com.linkedin.searchservice.repository.UserSearchRepository;
import com.linkedin.searchservice.telemetry.Telemetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchEventConsumer {

    private final UserSearchRepository userSearchRepository;
    private final PostSearchRepository postSearchRepository;

    @KafkaListener(topics = "user.created")
    public void consumeUserCreated(@Payload Map<String, Object> payload) {
        indexUser(payload, "user.created");
    }

    /** Upserts: if the created event was missed (or the user predates the index) the profile still gets indexed. */
    @KafkaListener(topics = "user.updated")
    public void consumeUserUpdated(@Payload Map<String, Object> payload) {
        indexUser(payload, "user.updated");
    }

    @SuppressWarnings("unchecked")
    private void indexUser(Map<String, Object> payload, String source) {
        String userId = (String) payload.get("userId");
        try {
            if (userId == null) {
                log.warn("Ignoring {} event without userId", source);
                return;
            }
            UserDocument doc = userSearchRepository.findById(userId).orElseGet(UserDocument::new);
            doc.setId(userId);
            doc.setFirstName((String) payload.get("firstName"));
            doc.setLastName((String) payload.get("lastName"));
            doc.setHeadline((String) payload.get("headline"));
            doc.setLocation((String) payload.get("location"));
            if (payload.get("email") != null) {
                doc.setEmail((String) payload.get("email"));
            }
            if (payload.get("profilePhotoUrl") != null) {
                doc.setProfilePhotoUrl((String) payload.get("profilePhotoUrl"));
            }
            if (payload.get("skills") != null) {
                doc.setSkills((List<String>) payload.get("skills"));
            }
            userSearchRepository.save(doc);
            Telemetry.indexed("users", true);
            log.info("User indexed ({}): {}", source, userId);
        } catch (Exception e) {
            Telemetry.indexed("users", false);
            log.error("Error indexing user {}: {}", userId, e.toString(), e);
            throw new IllegalStateException("user indexing failed", e); // let the Kafka error handler retry
        }
    }

    @KafkaListener(topics = "post.created")
    public void consumePostCreated(@Payload Map<String, Object> payload) {
        String postId = (String) payload.get("postId");
        try {
            PostDocument document = new PostDocument();
            document.setId(postId);
            document.setContent((String) payload.get("content"));
            document.setAuthorId((String) payload.get("authorId"));
            document.setImageUrl((String) payload.get("imageUrl"));
            document.setCreatedAt((String) payload.get("createdAt"));
            postSearchRepository.save(document);
            Telemetry.indexed("posts", true);
            log.info("Post indexed: {}", postId);
        } catch (Exception e) {
            Telemetry.indexed("posts", false);
            log.error("Error indexing post {}: {}", postId, e.toString(), e);
            throw new IllegalStateException("post indexing failed", e);
        }
    }
}
