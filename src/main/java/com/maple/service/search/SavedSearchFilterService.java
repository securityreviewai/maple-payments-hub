package com.maple.service.search;

import com.maple.dto.RoleWorkspaceDefaultDto;
import com.maple.dto.SavedSearchFilterDto;
import com.maple.dto.WorkspaceDefaultViewDto;
import com.maple.model.RoleDefaultWorkspaceView;
import com.maple.model.SavedSearchFilter;
import com.maple.model.SavedSearchVisibility;
import com.maple.repository.RoleDefaultWorkspaceViewRepository;
import com.maple.repository.SavedSearchFilterRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for managing user-saved payment search filters and shareable "views" per role, plus
 * default workspace views per role.
 */
@Service
public class SavedSearchFilterService {

    private static final Logger logger = LoggerFactory.getLogger(SavedSearchFilterService.class);

    /** Allowed keys for query params; others are discarded. */
    private static final Set<String> ALLOWED_QUERY_KEYS =
            Set.of(
                    "paymentReference",
                    "debtorAccount",
                    "creditorAccount",
                    "status",
                    "initiatedBy",
                    "startDate",
                    "endDate",
                    "minAmountCents",
                    "maxAmountCents",
                    "currency",
                    "page",
                    "size");

    private static final int MAX_FILTERS_PER_USER = 20;

    /**
     * Roles that may appear in {@link SavedSearchFilter#getSharedRole()} and in role default
     * configuration (must align with SecurityConfig authorities).
     */
    private static final Set<String> WORKSPACE_ROLES =
            Set.of(
                    "ROLE_TREASURY_OPS",
                    "ROLE_TREASURY_MANAGER",
                    "ROLE_CLEARING",
                    "ROLE_AUDITOR",
                    "ROLE_INTEGRATION");

    /**
     * When resolving {@link #resolveWorkspaceDefault}, the first matching role in this order wins.
     */
    private static final List<String> DEFAULT_ROLE_PRIORITY =
            List.of(
                    "ROLE_TREASURY_MANAGER",
                    "ROLE_TREASURY_OPS",
                    "ROLE_CLEARING",
                    "ROLE_AUDITOR",
                    "ROLE_INTEGRATION");

    private final SavedSearchFilterRepository repository;
    private final RoleDefaultWorkspaceViewRepository roleDefaultRepository;

    public SavedSearchFilterService(
            SavedSearchFilterRepository repository, RoleDefaultWorkspaceViewRepository roleDefaultRepository) {
        this.repository = repository;
        this.roleDefaultRepository = roleDefaultRepository;
    }

    public List<SavedSearchFilterDto> listVisibleForUser(UUID userId, Collection<String> roleAuthorities) {
        Set<String> roles = normalizeRoleSet(roleAuthorities);
        List<SavedSearchFilter> mine = repository.findByUserIdOrderByUpdatedAtDesc(userId);
        Set<UUID> mineIds = mine.stream().map(SavedSearchFilter::getId).collect(Collectors.toSet());
        List<SavedSearchFilter> shared =
                roles.isEmpty()
                        ? List.of()
                        : repository.findByVisibilityAndSharedRoleIn(SavedSearchVisibility.SHARED, roles);
        List<SavedSearchFilter> merged = new ArrayList<>(mine);
        for (SavedSearchFilter s : shared) {
            if (!mineIds.contains(s.getId())) {
                merged.add(s);
            }
        }
        merged.sort(Comparator.comparing(SavedSearchFilter::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        return merged.stream().map(e -> toDto(e, userId)).collect(Collectors.toList());
    }

    public Optional<SavedSearchFilterDto> getByIdVisibleToUser(
            UUID userId, UUID id, Collection<String> roleAuthorities) {
        Set<String> roles = normalizeRoleSet(roleAuthorities);
        return repository
                .findById(id)
                .filter(
                        s ->
                                userId.equals(s.getUserId())
                                        || (s.getVisibility() == SavedSearchVisibility.SHARED
                                                && s.getSharedRole() != null
                                                && roles.contains(s.getSharedRole())))
                .map(s -> toDto(s, userId));
    }

    @Transactional
    public SavedSearchFilterDto create(
            UUID userId, String name, Map<String, Object> rawParams, SavedSearchVisibility visibility, String sharedRole) {
        if (repository.countByUserId(userId) >= MAX_FILTERS_PER_USER) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Maximum number of saved filters (" + MAX_FILTERS_PER_USER + ") reached");
        }
        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Filter name cannot be empty");
        }
        if (repository.existsByUserIdAndName(userId, trimmedName)) {
            throw new IllegalArgumentException("A filter with this name already exists");
        }
        Map<String, Object> sanitized = sanitizeQueryParams(rawParams);
        SavedSearchFilter entity = new SavedSearchFilter(userId, trimmedName, sanitized);
        applyShareSettings(entity, visibility, sharedRole);
        entity = repository.save(entity);
        logger.info("Saved search filter created: id={} name={} user={}", entity.getId(), entity.getName(), userId);
        return toDto(entity, userId);
    }

    @Transactional
    public SavedSearchFilterDto update(
            UUID userId,
            UUID id,
            String name,
            Map<String, Object> rawParams,
            SavedSearchVisibility visibility,
            String sharedRole) {
        SavedSearchFilter entity =
                repository
                        .findByUserIdAndId(userId, id)
                        .orElseThrow(() -> new IllegalArgumentException("Saved filter not found"));
        if (name != null && !name.trim().isEmpty()) {
            String trimmed = name.trim();
            if (!trimmed.equals(entity.getName()) && repository.existsByUserIdAndName(userId, trimmed)) {
                throw new IllegalArgumentException("A filter with this name already exists");
            }
            entity.setName(trimmed);
        }
        if (rawParams != null) {
            entity.setQueryParams(sanitizeQueryParams(rawParams));
        }
        if (visibility != null || sharedRole != null) {
            applyShareSettings(
                    entity,
                    visibility != null ? visibility : entity.getVisibility(),
                    sharedRole != null ? sharedRole : entity.getSharedRole());
        }
        entity = repository.save(entity);
        logger.info("Saved search filter updated: id={} user={}", id, userId);
        return toDto(entity, userId);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        SavedSearchFilter entity =
                repository
                        .findByUserIdAndId(userId, id)
                        .orElseThrow(() -> new IllegalArgumentException("Saved filter not found"));
        repository.delete(entity);
        logger.info("Saved search filter deleted: id={} user={}", id, userId);
    }

    public WorkspaceDefaultViewDto resolveWorkspaceDefault(UUID userId, Collection<String> roleAuthorities) {
        Set<String> roles = normalizeRoleSet(roleAuthorities);
        for (String role : DEFAULT_ROLE_PRIORITY) {
            if (!roles.contains(role)) {
                continue;
            }
            Optional<RoleDefaultWorkspaceView> mapping = roleDefaultRepository.findById(role);
            if (mapping.isEmpty() || mapping.get().getSavedSearchFilterId() == null) {
                continue;
            }
            UUID filterId = mapping.get().getSavedSearchFilterId();
            Optional<SavedSearchFilterDto> dto = getByIdVisibleToUser(userId, filterId, roleAuthorities);
            if (dto.isPresent()) {
                return WorkspaceDefaultViewDto.builder().appliedRole(role).filter(dto.get()).build();
            }
        }
        return WorkspaceDefaultViewDto.builder().appliedRole(null).filter(null).build();
    }

    public List<RoleWorkspaceDefaultDto> listRoleDefaults() {
        return roleDefaultRepository.findAll().stream()
                .sorted(Comparator.comparing(RoleDefaultWorkspaceView::getRoleName))
                .map(
                        m ->
                                RoleWorkspaceDefaultDto.builder()
                                        .role(m.getRoleName())
                                        .savedSearchFilterId(m.getSavedSearchFilterId())
                                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Sets which saved filter is the default workspace view for users with {@code roleName}. The filter
     * must be a SHARED view with {@code shared_role} equal to {@code roleName}.
     */
    @Transactional
    public RoleWorkspaceDefaultDto setRoleDefaultWorkspace(String roleName, UUID savedSearchFilterId) {
        if (!WORKSPACE_ROLES.contains(roleName)) {
            throw new IllegalArgumentException("Unsupported role for workspace default");
        }
        if (savedSearchFilterId == null) {
            RoleDefaultWorkspaceView row =
                    roleDefaultRepository
                            .findById(roleName)
                            .orElseGet(() -> new RoleDefaultWorkspaceView(roleName, null));
            row.setSavedSearchFilterId(null);
            roleDefaultRepository.save(row);
            return RoleWorkspaceDefaultDto.builder().role(roleName).savedSearchFilterId(null).build();
        }
        SavedSearchFilter filter =
                repository
                        .findById(savedSearchFilterId)
                        .orElseThrow(() -> new IllegalArgumentException("Saved filter not found"));
        if (filter.getVisibility() != SavedSearchVisibility.SHARED
                || filter.getSharedRole() == null
                || !filter.getSharedRole().equals(roleName)) {
            throw new IllegalArgumentException(
                    "Default workspace view must be a SHARED filter targeted at the same role");
        }
        RoleDefaultWorkspaceView row =
                roleDefaultRepository
                        .findById(roleName)
                        .orElseGet(() -> new RoleDefaultWorkspaceView(roleName, savedSearchFilterId));
        row.setSavedSearchFilterId(savedSearchFilterId);
        roleDefaultRepository.save(row);
        logger.info("Role default workspace view set: role={} filterId={}", roleName, savedSearchFilterId);
        return RoleWorkspaceDefaultDto.builder().role(roleName).savedSearchFilterId(savedSearchFilterId).build();
    }

    private void applyShareSettings(SavedSearchFilter entity, SavedSearchVisibility visibility, String sharedRoleRaw) {
        SavedSearchVisibility v = visibility != null ? visibility : SavedSearchVisibility.PRIVATE;
        entity.setVisibility(v);
        if (v == SavedSearchVisibility.PRIVATE) {
            entity.setSharedRole(null);
            return;
        }
        String sr = sharedRoleRaw != null ? sharedRoleRaw.trim() : "";
        if (sr.isEmpty()) {
            throw new IllegalArgumentException("sharedRole is required when visibility is SHARED");
        }
        if (!WORKSPACE_ROLES.contains(sr)) {
            throw new IllegalArgumentException("Invalid sharedRole for a workspace view");
        }
        entity.setSharedRole(sr);
    }

    private Set<String> normalizeRoleSet(Collection<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return Set.of();
        }
        return authorities.stream()
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Map<String, Object> sanitizeQueryParams(Map<String, Object> raw) {
        if (raw == null) return new HashMap<>();
        Map<String, Object> out = new HashMap<>();
        for (String key : ALLOWED_QUERY_KEYS) {
            if (raw.containsKey(key) && raw.get(key) != null && !"".equals(raw.get(key))) {
                Object v = raw.get(key);
                if (v instanceof String && ((String) v).length() > 500) continue;
                out.put(key, v);
            }
        }
        return out;
    }

    private SavedSearchFilterDto toDto(SavedSearchFilter e, UUID currentUserId) {
        boolean canEdit = currentUserId != null && currentUserId.equals(e.getUserId());
        return SavedSearchFilterDto.builder()
                .id(e.getId())
                .name(e.getName())
                .queryParams(e.getQueryParams() != null ? new HashMap<>(e.getQueryParams()) : new HashMap<>())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .visibility(e.getVisibility() != null ? e.getVisibility().name() : SavedSearchVisibility.PRIVATE.name())
                .sharedRole(e.getSharedRole())
                .ownerUserId(e.getUserId())
                .canEdit(canEdit)
                .build();
    }

    /** Parses visibility for API requests; invalid values yield BAD_REQUEST. */
    public static SavedSearchVisibility parseVisibility(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SavedSearchVisibility.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid visibility value");
        }
    }

    public static Set<String> workspaceRoles() {
        return WORKSPACE_ROLES;
    }
}
