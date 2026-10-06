package com.sentinelops.identity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdentityDtos.TeamResponse create(
            @Valid @RequestBody IdentityDtos.CreateTeamRequest request) {
        return teamService.create(request);
    }

    @GetMapping
    public List<IdentityDtos.TeamResponse> list() {
        return teamService.list();
    }

    @GetMapping("/{id}")
    public IdentityDtos.TeamResponse get(@PathVariable Long id) {
        return teamService.get(id);
    }
}
