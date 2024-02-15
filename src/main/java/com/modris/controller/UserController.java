package com.modris.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

	@GetMapping("/user")
	public Map<String, String> getUser(@AuthenticationPrincipal OidcUser oidcUser) {
		Map<String, String> response = new HashMap<>();
        response.put("username", oidcUser.getPreferredUsername());
        return response;
		
	}
}
