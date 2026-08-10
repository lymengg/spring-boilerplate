package com.example.demo.service;

import com.example.demo.dto.NoteCreateRequest;
import com.example.demo.dto.NoteResponse;
import com.example.demo.dto.NoteUpdateRequest;
import com.example.demo.entity.Note;
import com.example.demo.entity.User;
import com.example.demo.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final UserService userService;

    @Transactional
    public NoteResponse createNote(String username, NoteCreateRequest request) {
        User owner = userService.getByUsername(username);

        Note note = Note.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .owner(owner)
                .build();

        note = noteRepository.save(note);
        return mapToResponse(note);
    }

    @Transactional(readOnly = true)
    public NoteResponse getNoteById(String username, Long id) {
        Note note = noteRepository.findByIdAndOwnerUsername(id, username)
                .orElseThrow(() -> new IllegalArgumentException("Note not found"));
        return mapToResponse(note);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> getMyNotes(String username) {
        return noteRepository.findAllByOwnerUsernameOrderByUpdatedAtDesc(username).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<NoteResponse> getAllNotes() {
        return noteRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public NoteResponse updateNote(String username, Long id, NoteUpdateRequest request) {
        Note note = noteRepository.findByIdAndOwnerUsername(id, username)
                .orElseThrow(() -> new IllegalArgumentException("Note not found"));

        note.setTitle(request.getTitle());
        note.setContent(request.getContent());

        return mapToResponse(note);
    }

    @Transactional
    public void deleteNote(String username, Long id) {
        Note note = noteRepository.findByIdAndOwnerUsername(id, username)
                .orElseThrow(() -> new IllegalArgumentException("Note not found"));
        noteRepository.delete(note);
    }

    private NoteResponse mapToResponse(Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .title(note.getTitle())
                .content(note.getContent())
                .ownerUsername(note.getOwner().getUsername())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
