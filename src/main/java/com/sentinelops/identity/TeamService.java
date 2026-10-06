package com.sentinelops.identity;

import com.sentinelops.common.ConflictException;
import com.sentinelops.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teams;

    @Transactional
    public IdentityDtos.TeamResponse create(IdentityDtos.CreateTeamRequest request) {
        String name = request.name().trim();
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);

        if (teams.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Team name already exists: " + name);
        }
        if (teams.existsBySlugIgnoreCase(slug)) {
            throw new ConflictException("Team slug already exists: " + slug);
        }

        Team team = Team.builder()
                .name(name)
                .slug(slug)
                .build();

        return IdentityDtos.TeamResponse.from(teams.saveAndFlush(team));
    }

    public List<IdentityDtos.TeamResponse> list() {
        return teams.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(IdentityDtos.TeamResponse::from)
                .toList();
    }

    public IdentityDtos.TeamResponse get(Long id) {
        return IdentityDtos.TeamResponse.from(find(id));
    }

    Team find(Long id) {
        return teams.findById(id)
                .orElseThrow(() -> new NotFoundException("Team not found: " + id));
    }
}
