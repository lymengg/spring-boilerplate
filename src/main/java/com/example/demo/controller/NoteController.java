package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.NoteCreateRequest;
import com.example.demo.dto.NoteResponse;
import com.example.demo.dto.NoteUpdateRequest;
import com.example.demo.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public ResponseEntity<ApiResponse<NoteResponse>> createNote(
            @Valid @RequestBody NoteCreateRequest request,
            Authentication authentication) {
        NoteResponse note = noteService.createNote(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Note created successfully", note));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NoteResponse>>> getMyNotes(Authentication authentication) {
        List<NoteResponse> notes = noteService.getMyNotes(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Notes retrieved successfully", notes));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<NoteResponse>>> getAllNotes() {
        List<NoteResponse> notes = noteService.getAllNotes();
        return ResponseEntity.ok(ApiResponse.success("All notes retrieved successfully", notes));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> getNoteById(
            @PathVariable Long id,
            Authentication authentication) {
        NoteResponse note = noteService.getNoteById(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Note retrieved successfully", note));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> updateNote(
            @PathVariable Long id,
            @Valid @RequestBody NoteUpdateRequest request,
            Authentication authentication) {
        NoteResponse note = noteService.updateNote(authentication.getName(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Note updated successfully", note));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @PathVariable Long id,
            Authentication authentication) {
        noteService.deleteNote(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Note deleted successfully", null));
    }
}
