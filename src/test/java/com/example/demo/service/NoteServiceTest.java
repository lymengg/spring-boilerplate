package com.example.demo.service;

import com.example.demo.dto.NoteCreateRequest;
import com.example.demo.dto.NoteResponse;
import com.example.demo.dto.NoteUpdateRequest;
import com.example.demo.entity.Note;
import com.example.demo.entity.User;
import com.example.demo.repository.NoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private NoteService noteService;

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .build();
    }

    private Note sampleNote(User owner) {
        return Note.builder()
                .id(1L)
                .title("My title")
                .content("My content")
                .owner(owner)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should create note and return response")
    void shouldCreateNote() {
        User user = sampleUser();
        Note note = sampleNote(user);
        NoteCreateRequest request = NoteCreateRequest.builder()
                .title("My title")
                .content("My content")
                .build();

        when(userService.getByUsername("testuser")).thenReturn(user);
        when(noteRepository.save(any(Note.class))).thenReturn(note);

        NoteResponse response = noteService.createNote("testuser", request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("My title");
        assertThat(response.getContent()).isEqualTo("My content");
        assertThat(response.getOwnerUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should get note by id when owned")
    void shouldGetNoteByIdWhenOwned() {
        User user = sampleUser();
        Note note = sampleNote(user);

        when(noteRepository.findByIdAndOwnerUsername(1L, "testuser")).thenReturn(Optional.of(note));

        NoteResponse response = noteService.getNoteById("testuser", 1L);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw when note does not exist or is not owned")
    void shouldThrowWhenNoteNotFoundOrNotOwned() {
        when(noteRepository.findByIdAndOwnerUsername(1L, "testuser")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getNoteById("testuser", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Note not found");
    }

    @Test
    @DisplayName("Should get all notes owned by user")
    void shouldGetMyNotes() {
        User user = sampleUser();
        Note note = sampleNote(user);

        when(noteRepository.findAllByOwnerUsernameOrderByUpdatedAtDesc("testuser")).thenReturn(List.of(note));

        List<NoteResponse> notes = noteService.getMyNotes("testuser");

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getOwnerUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should update note when owned")
    void shouldUpdateNoteWhenOwned() {
        User user = sampleUser();
        Note note = sampleNote(user);
        NoteUpdateRequest request = NoteUpdateRequest.builder()
                .title("Updated")
                .content("Updated content")
                .build();

        when(noteRepository.findByIdAndOwnerUsername(1L, "testuser")).thenReturn(Optional.of(note));

        NoteResponse response = noteService.updateNote("testuser", 1L, request);

        assertThat(response.getTitle()).isEqualTo("Updated");
        assertThat(response.getContent()).isEqualTo("Updated content");
    }

    @Test
    @DisplayName("Should delete note when owned")
    void shouldDeleteNoteWhenOwned() {
        User user = sampleUser();
        Note note = sampleNote(user);

        when(noteRepository.findByIdAndOwnerUsername(1L, "testuser")).thenReturn(Optional.of(note));

        noteService.deleteNote("testuser", 1L);

        verify(noteRepository).delete(note);
    }

    @Test
    @DisplayName("Should return all notes for admin")
    void shouldReturnAllNotes() {
        User user = sampleUser();
        Note note = sampleNote(user);

        when(noteRepository.findAll()).thenReturn(List.of(note));

        List<NoteResponse> notes = noteService.getAllNotes();

        assertThat(notes).hasSize(1);
    }
}
