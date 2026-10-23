package com.linkedin.searchservice.service;

import com.linkedin.searchservice.model.PostDocument;
import com.linkedin.searchservice.model.UserDocument;
import com.linkedin.searchservice.repository.PostSearchRepository;
import com.linkedin.searchservice.repository.UserSearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SearchServiceTest {

    private final UserSearchRepository users = mock(UserSearchRepository.class);
    private final PostSearchRepository posts = mock(PostSearchRepository.class);
    private final SearchService service = new SearchService(users, posts);

    @Test
    void blankQueriesReturnEmptyWithoutHittingElasticsearch() {
        assertTrue(service.searchUsers("  ").isEmpty());
        assertTrue(service.searchUsers(null).isEmpty());
        assertTrue(service.searchPosts("").isEmpty());
        assertTrue(service.searchBySkill(" ").isEmpty());
        verifyNoInteractions(users, posts);
    }

    @Test
    void queryIsTrimmedAndQuotesAndBackslashesAreEscapedForTheJsonTemplate() {
        when(users.searchUsers(any(), any())).thenReturn(List.of(new UserDocument()));
        service.searchUsers("  a\"b\\c  ");
        ArgumentCaptor<String> q = ArgumentCaptor.forClass(String.class);
        verify(users).searchUsers(q.capture(), any());
        assertEquals("a\\\"b\\\\c", q.getValue());
    }

    @Test
    void injectionAttemptStaysInsideTheStringLiteral() {
        service.searchPosts("x\"}},\"match_all\":{}");
        ArgumentCaptor<String> q = ArgumentCaptor.forClass(String.class);
        verify(posts).searchPosts(q.capture(), any());
        // every double quote in the payload is preceded by a backslash
        assertFalse(q.getValue().matches("(?s).*(?<!\\\\)\".*"));
    }

    @Test
    void newlinesAreFlattened() {
        service.searchPosts("a\nb\rc");
        ArgumentCaptor<String> q = ArgumentCaptor.forClass(String.class);
        verify(posts).searchPosts(q.capture(), any());
        assertEquals("a b c", q.getValue());
    }

    @Test
    void resultsArePagedToTwentyHits() {
        service.searchBySkill("java");
        ArgumentCaptor<org.springframework.data.domain.Pageable> p = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(users).findBySkill(eq("java"), p.capture());
        assertEquals(20, p.getValue().getPageSize());
    }
}
