package com.example.demo.service;

import com.example.demo.dto.AuditLogResponse;
import com.example.demo.dto.PageResponse;
import com.example.demo.entity.AuditLog;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.AuditLogMapper;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    private User actor;
    private User noTenantUser;
    private AuditLog log;
    private AuditLogResponse stubResponse;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().id(1L).name("Tenant 1").build();
        actor = User.builder().id(1L).email("admin").tenant(tenant).build();
        noTenantUser = User.builder().id(2L).tenant(null).build();
        log = AuditLog.builder().id(5L).tenantId(1L).build();
        stubResponse = AuditLogResponse.builder().id(5L).build();

        when(userService.getByEmail("admin")).thenReturn(actor);
        when(userService.getByEmail("noTenantUser")).thenReturn(noTenantUser);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogMapper.toResponse(any(AuditLog.class))).thenReturn(stubResponse);
    }

    @Test
    @DisplayName("record persists a complete audit entry with actor, tenant and resource details")
    void recordPersistsCompleteAuditEntry() {
        auditLogService.record("USER_CREATED", "USER", "10", "User created", "admin");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(1L);
        assertThat(saved.getActorEmail()).isEqualTo("admin");
        assertThat(saved.getTenantId()).isEqualTo(1L);
        assertThat(saved.getAction()).isEqualTo("USER_CREATED");
        assertThat(saved.getResourceType()).isEqualTo("USER");
        assertThat(saved.getResourceId()).isEqualTo("10");
        assertThat(saved.getDetails()).isEqualTo("User created");
    }

    @Test
    @DisplayName("record for an actor without a tenant leaves tenantId null")
    void recordForActorWithoutTenantHasNullTenantId() {
        auditLogService.record("USER_CREATED", "USER", "10", "User created", "noTenantUser");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isNull();
    }

    @Test
    @DisplayName("Super admin listing audit logs uses findAll and maps into a PageResponse")
    void getAuditLogsAsSuperAdminUsesFindAll() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(authorizationService.isSuperAdmin(actor)).thenReturn(true);
        when(auditLogRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        PageResponse<AuditLogResponse> result = auditLogService.getAuditLogs(pageable, "admin");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isSameAs(stubResponse);
        verify(auditLogRepository).findAll(pageable);
        verify(auditLogRepository, never()).findAllByTenantId(any(), any());
    }

    @Test
    @DisplayName("Tenant user listing audit logs uses findAllByTenantId")
    void getAuditLogsAsTenantUserUsesFindAllByTenantId() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(authorizationService.isSuperAdmin(actor)).thenReturn(false);
        when(auditLogRepository.findAllByTenantId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        PageResponse<AuditLogResponse> result = auditLogService.getAuditLogs(pageable, "admin");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isSameAs(stubResponse);
        verify(auditLogRepository).findAllByTenantId(1L, pageable);
        verify(auditLogRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("User with no tenant gets an empty audit page and the repository is never queried")
    void getAuditLogsForUserWithoutTenantReturnsEmpty() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(authorizationService.isSuperAdmin(noTenantUser)).thenReturn(false);

        PageResponse<AuditLogResponse> result = auditLogService.getAuditLogs(pageable, "noTenantUser");

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getContent()).isEmpty();
        verify(auditLogRepository, never()).findAll(any(Pageable.class));
        verify(auditLogRepository, never()).findAllByTenantId(any(), any());
    }

    @Test
    @DisplayName("Super admin reading an audit log by id uses findById and maps the result")
    void getAuditLogByIdAsSuperAdminUsesFindById() {
        when(authorizationService.isSuperAdmin(actor)).thenReturn(true);
        when(auditLogRepository.findById(5L)).thenReturn(Optional.of(log));

        AuditLogResponse result = auditLogService.getAuditLogById(5L, "admin");

        assertThat(result).isSameAs(stubResponse);
        verify(auditLogMapper).toResponse(log);
    }

    @Test
    @DisplayName("Super admin reading a nonexistent audit log throws IllegalArgumentException")
    void getAuditLogByIdAsSuperAdminNotFoundThrows() {
        when(authorizationService.isSuperAdmin(actor)).thenReturn(true);
        when(auditLogRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditLogService.getAuditLogById(5L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Audit log not found");
    }

    @Test
    @DisplayName("Tenant user reading an audit log by id uses findByIdAndTenantId")
    void getAuditLogByIdAsTenantUserUsesFindByIdAndTenantId() {
        when(authorizationService.isSuperAdmin(actor)).thenReturn(false);
        when(auditLogRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(log));

        AuditLogResponse result = auditLogService.getAuditLogById(5L, "admin");

        assertThat(result).isSameAs(stubResponse);
        verify(auditLogRepository).findByIdAndTenantId(5L, 1L);
        verify(auditLogRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Tenant user reading a nonexistent audit log throws IllegalArgumentException")
    void getAuditLogByIdAsTenantUserNotFoundThrows() {
        when(authorizationService.isSuperAdmin(actor)).thenReturn(false);
        when(auditLogRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditLogService.getAuditLogById(5L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Audit log not found");
    }

    @Test
    @DisplayName("User with no tenant reading an audit log is denied with AccessDeniedException")
    void getAuditLogByIdForUserWithoutTenantDenied() {
        when(authorizationService.isSuperAdmin(noTenantUser)).thenReturn(false);

        assertThatThrownBy(() -> auditLogService.getAuditLogById(5L, "noTenantUser"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot access this audit log");
        verify(auditLogRepository, never()).findById(any());
        verify(auditLogRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    @DisplayName("Cross-tenant audit log read is treated as not found with no tenant leak")
    void getAuditLogByIdCrossTenantThrowsNotFound() {
        when(authorizationService.isSuperAdmin(actor)).thenReturn(false);
        when(auditLogRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditLogService.getAuditLogById(5L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Audit log not found");
        verify(auditLogRepository, never()).findById(any());
    }
}
