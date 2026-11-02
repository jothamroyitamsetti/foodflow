package com.foodflow.auth.dto; import java.util.UUID;
public record AuthResponse(String token,String tokenType,UUID userId,String fullName,String email,String role){}
