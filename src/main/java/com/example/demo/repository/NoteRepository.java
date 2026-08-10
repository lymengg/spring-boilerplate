package com.example.demo.repository;

import com.example.demo.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findAllByOwnerUsernameOrderByUpdatedAtDesc(String ownerUsername);

    Optional<Note> findByIdAndOwnerUsername(Long id, String ownerUsername);

    boolean existsByIdAndOwnerUsername(Long id, String ownerUsername);
}
