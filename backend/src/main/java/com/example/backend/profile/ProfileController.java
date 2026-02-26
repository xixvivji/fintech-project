package com.example.backend.profile;

import com.example.backend.auth.JwtService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profileService;
    private final JwtService jwtService;
    public ProfileController(ProfileService profileService, JwtService jwtService) {
        this.profileService = profileService;
        this.jwtService = jwtService;
    }

    @GetMapping("/me")
    public ProfileDetailDto me(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return profileService.getMyProfile(userId);
    }

    @PutMapping("/me")
    public ProfileDetailDto update(@RequestHeader("Authorization") String authorizationHeader,
                                   @RequestBody ProfileUpdateRequestDto request) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return profileService.updateMyProfile(userId, request);
    }
}
