package com.cms.iam.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;     // seconds until access token expires (900)

    // Note: refreshToken is NOT in this body.
    // It is returned as an HttpOnly cookie by AuthController.
}
