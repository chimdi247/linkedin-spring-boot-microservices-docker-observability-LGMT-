package com.linkedin.searchservice.service;

import com.linkedin.searchservice.model.PostDocument;
import com.linkedin.searchservice.model.UserDocument;
import com.linkedin.searchservice.repository.PostSearchRepository;
import com.linkedin.searchservice.repository.UserSearchRepository;
import com.linkedin.searchservice.telemetry.Telemetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    private final UserSearchRepository userSearchRepository;
    private final PostSearchRepository postSearchRepository;

    public List<UserDocument> searchUsers(String query) {
        Telemetry.search("people");
        if (query == null || query.isBlank()) {
            return List.of();
        }
        log.debug("Searching users");
        return userSearchRepository.searchUsers(escape(query), PAGE);
    }

    public List<UserDocument> searchBySkill(String skill) {
        Telemetry.search("skills");
        if (skill == null || skill.isBlank()) {
            return List.of();
        }
        return userSearchRepository.findBySkill(escape(skill.trim()), PAGE);
    }

    public List<PostDocument> searchPosts(String query) {
        Telemetry.search("posts");
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return postSearchRepository.searchPosts(escape(query), PAGE);
    }

    /** The value is placed inside a JSON string of the @Query template: keep it from breaking out. */
    private static String escape(String s) {
        return s.trim().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
