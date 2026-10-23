package com.linkedin.searchservice.repository;

import com.linkedin.searchservice.model.UserDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, String> {

    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"firstName^3\", \"lastName^3\", "
            + "\"headline\", \"location\"], \"fuzziness\": \"AUTO\"}}")
    List<UserDocument> searchUsers(String query, Pageable pageable);

    /** Exact skill, case-insensitive. */
    @Query("{\"term\": {\"skills\": {\"value\": \"?0\", \"case_insensitive\": true}}}")
    List<UserDocument> findBySkill(String skill, Pageable pageable);
}
