package com.linkedin.userservice.service;

import com.linkedin.userservice.dto.ConnectionStatusResponse;
import com.linkedin.userservice.dto.UserResponse;
import com.linkedin.userservice.entity.Connection;
import com.linkedin.userservice.entity.ConnectionStatus;
import com.linkedin.userservice.entity.User;
import com.linkedin.userservice.exception.ApiException;
import com.linkedin.userservice.repository.ConnectionRepository;
import com.linkedin.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock ConnectionRepository connectionRepository;
    @Mock UserRepository userRepository;
    @Mock S3Service s3Service;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Captor ArgumentCaptor<Collection<String>> idsCaptor;
    @Captor ArgumentCaptor<Pageable> pageCaptor;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(connectionRepository, userRepository, s3Service, kafkaTemplate);
    }

    private static Connection conn(String id, String requester, String receiver, ConnectionStatus status) {
        Connection c = new Connection();
        c.setId(id);
        c.setRequesterId(requester);
        c.setReceiverId(receiver);
        c.setStatus(status);
        return c;
    }

    private static User user(String id) {
        User u = new User();
        u.setId(id);
        u.setEmail(id + "@x.io");
        u.setFirstName(id);
        u.setLastName("L");
        return u;
    }

    private static ApiException statusOf(Runnable r, HttpStatus expected) {
        ApiException e = assertThrows(ApiException.class, r::run);
        assertEquals(expected, e.getStatus());
        return e;
    }

    // ------------------------------------------------------------ connect
    @Test
    void connectingWithYourselfIs400() {
        statusOf(() -> service.sendConnectionRequest("a", "a"), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(connectionRepository);
    }

    @Test
    void connectingToUnknownUserIs404() {
        when(userRepository.existsById("ghost")).thenReturn(false);
        statusOf(() -> service.sendConnectionRequest("ghost", "a"), HttpStatus.NOT_FOUND);
    }

    @Test
    void duplicateRequestInEitherDirectionIs409() {
        when(userRepository.existsById("b")).thenReturn(true);
        when(connectionRepository.findBetween("a", "b")).thenReturn(List.of(conn("1", "b", "a", ConnectionStatus.PENDING)));
        statusOf(() -> service.sendConnectionRequest("b", "a"), HttpStatus.CONFLICT);
        verify(connectionRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulRequestIsStoredPendingAndPublished() {
        when(userRepository.existsById("b")).thenReturn(true);
        when(connectionRepository.findBetween("a", "b")).thenReturn(List.of());
        assertEquals("Connection request sent", service.sendConnectionRequest("b", "a"));

        ArgumentCaptor<Connection> saved = ArgumentCaptor.forClass(Connection.class);
        verify(connectionRepository).save(saved.capture());
        assertEquals("a", saved.getValue().getRequesterId());
        assertEquals("b", saved.getValue().getReceiverId());
        assertEquals(ConnectionStatus.PENDING, saved.getValue().getStatus());

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("connection.requested"), eq("a"), event.capture());
        assertEquals("b", ((Map<String, Object>) event.getValue()).get("receiverId"));
    }

    // ------------------------------------------------------- accept / reject
    @Test
    void onlyTheReceiverMayAcceptOrReject() {
        when(connectionRepository.findById("c1")).thenReturn(Optional.of(conn("c1", "a", "b", ConnectionStatus.PENDING)));
        statusOf(() -> service.acceptConnectionRequest("c1", "a"), HttpStatus.FORBIDDEN);   // requester
        statusOf(() -> service.rejectConnectionRequest("c1", "x"), HttpStatus.FORBIDDEN);   // third party
        verify(connectionRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void acceptingUnknownConnectionIs404AndNonPendingIs409() {
        when(connectionRepository.findById("nope")).thenReturn(Optional.empty());
        statusOf(() -> service.acceptConnectionRequest("nope", "b"), HttpStatus.NOT_FOUND);
        when(connectionRepository.findById("c2")).thenReturn(Optional.of(conn("c2", "a", "b", ConnectionStatus.CONNECTED)));
        statusOf(() -> service.acceptConnectionRequest("c2", "b"), HttpStatus.CONFLICT);
    }

    @Test
    void acceptSetsConnectedAndPublishesEvent() {
        Connection c = conn("c1", "a", "b", ConnectionStatus.PENDING);
        when(connectionRepository.findById("c1")).thenReturn(Optional.of(c));
        assertEquals("Connection accepted", service.acceptConnectionRequest("c1", "b"));
        assertEquals(ConnectionStatus.CONNECTED, c.getStatus());
        verify(connectionRepository).save(c);
        verify(kafkaTemplate).send(eq("connection.accepted"), eq("a"), any());
    }

    @Test
    void rejectSetsRejectedWithoutAcceptedEvent() {
        Connection c = conn("c1", "a", "b", ConnectionStatus.PENDING);
        when(connectionRepository.findById("c1")).thenReturn(Optional.of(c));
        service.rejectConnectionRequest("c1", "b");
        assertEquals(ConnectionStatus.REJECTED, c.getStatus());
        verify(kafkaTemplate, never()).send(eq("connection.accepted"), any(), any());
    }

    // ----------------------------------------------------------- connections
    @Test
    void getConnectionsReturnsTheOtherSideForBothDirections() {
        when(connectionRepository.findAcceptedFor("me")).thenReturn(List.of(
                conn("1", "me", "b", ConnectionStatus.CONNECTED),     // I sent it
                conn("2", "c", "me", ConnectionStatus.CONNECTED),     // I received it
                conn("3", "me", "b", ConnectionStatus.CONNECTED)));   // duplicate row -> de-duplicated
        when(userRepository.findAllById(any())).thenReturn(List.of(user("b"), user("c")));

        List<UserResponse> out = service.getConnections("me");

        verify(userRepository).findAllById(idsCaptor.capture());
        assertEquals(List.of("b", "c"), new ArrayList<>(idsCaptor.getValue()));
        assertEquals(2, out.size());
    }

    @Test
    void getConnectionsWithNoneSkipsTheUserLookup() {
        when(connectionRepository.findAcceptedFor("me")).thenReturn(List.of());
        assertTrue(service.getConnections("me").isEmpty());
        verify(userRepository, never()).findAllById(any());
    }

    // ----------------------------------------------------------- suggestions
    @Test
    void suggestionsExcludeSelfAndEveryoneWithAnyRelationship() {
        when(connectionRepository.findAllInvolving("me")).thenReturn(List.of(
                conn("1", "me", "b", ConnectionStatus.PENDING),
                conn("2", "c", "me", ConnectionStatus.CONNECTED),
                conn("3", "d", "me", ConnectionStatus.REJECTED)));
        when(userRepository.findByIdNotIn(any(), any())).thenReturn(List.of(user("z")));

        List<UserResponse> out = service.getSuggestions("me", 12);

        verify(userRepository).findByIdNotIn(idsCaptor.capture(), pageCaptor.capture());
        assertEquals(Set.of("me", "b", "c", "d"), new HashSet<>(idsCaptor.getValue()));
        assertEquals(12, pageCaptor.getValue().getPageSize());
        assertEquals("z", out.get(0).getId());
    }

    @Test
    void suggestionsNeverPassAnEmptyExcludeList() {
        when(connectionRepository.findAllInvolving("me")).thenReturn(List.of());
        service.getSuggestions("me", 5);
        verify(userRepository).findByIdNotIn(idsCaptor.capture(), any());
        assertEquals(Set.of("me"), new HashSet<>(idsCaptor.getValue()));     // an empty IN () is invalid SQL
    }

    @Test
    void suggestionLimitIsClampedBetween1And50() {
        when(connectionRepository.findAllInvolving("me")).thenReturn(List.of());
        service.getSuggestions("me", 100_000);
        service.getSuggestions("me", -3);
        verify(userRepository, times(2)).findByIdNotIn(any(), pageCaptor.capture());
        assertEquals(50, pageCaptor.getAllValues().get(0).getPageSize());
        assertEquals(1, pageCaptor.getAllValues().get(1).getPageSize());
    }

    // ------------------------------------------------------ connection status
    @Test
    void connectionStatusCoversAllStates() {
        assertEquals("SELF", service.getConnectionStatus("a", "a").getStatus());

        when(connectionRepository.findBetween("a", "b")).thenReturn(List.of());
        assertEquals("NONE", service.getConnectionStatus("a", "b").getStatus());

        when(connectionRepository.findBetween("a", "b")).thenReturn(List.of(conn("1", "a", "b", ConnectionStatus.PENDING)));
        assertEquals("PENDING_SENT", service.getConnectionStatus("a", "b").getStatus());
        assertEquals("PENDING_RECEIVED", service.getConnectionStatus("b", "a").getStatus());

        when(connectionRepository.findBetween("a", "b")).thenReturn(List.of(conn("2", "a", "b", ConnectionStatus.CONNECTED)));
        ConnectionStatusResponse r = service.getConnectionStatus("a", "b");
        assertEquals("CONNECTED", r.getStatus());
        assertEquals("2", r.getConnectionId());
    }

    @Test
    void connectionStatusPrefersAnActiveRelationshipOverAnOldRejection() {
        when(connectionRepository.findBetween("a", "b")).thenReturn(List.of(
                conn("old", "a", "b", ConnectionStatus.REJECTED),
                conn("new", "b", "a", ConnectionStatus.PENDING)));
        ConnectionStatusResponse r = service.getConnectionStatus("a", "b");
        assertEquals("PENDING_RECEIVED", r.getStatus());
        assertEquals("new", r.getConnectionId());
    }

    // --------------------------------------------------------------- profile
    @Test
    void updateProfileTrimsDeduplicatesSkillsAndPublishesUserUpdated() {
        User u = user("me");
        u.setSkills(new ArrayList<>(List.of("old")));
        when(userRepository.findById("me")).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse req = new UserResponse();
        req.setHeadline("H");
        req.setAbout("A");
        req.setLocation("L");
        req.setSkills(Arrays.asList(" Java ", "Kafka", "Java", "", null));
        UserResponse out = service.updateProfile("me", req);

        assertEquals(List.of("Java", "Kafka"), out.getSkills());
        verify(kafkaTemplate).send(eq("user.updated"), eq("me"), any());
    }

    @Test
    void unknownUserProfileIs404() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        statusOf(() -> service.getUserProfile("ghost"), HttpStatus.NOT_FOUND);
    }
}
