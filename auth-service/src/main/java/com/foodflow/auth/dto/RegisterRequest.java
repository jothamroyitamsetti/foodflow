package com.foodflow.auth.dto; import jakarta.validation.constraints.*;
public record RegisterRequest(@NotBlank @Size(min=2,max=80) String fullName,@NotBlank @Email @Size(max=160) String email,@NotBlank @Size(min=8,max=72) String password,@Pattern(regexp="CUSTOMER|RESTAURANT_OWNER|DELIVERY_PARTNER") String applicationRole,@Size(max=100) String restaurantName) {
 public RegisterRequest(String fullName,String email,String password){this(fullName,email,password,"CUSTOMER",null);}
}
