package com.example.backend;

import lombok.Data;

@Data
public class TokenResponseDto {
    private String access_token;
    private String token_type;
    private int expires_in;
    private String access_token_token_expired;
}