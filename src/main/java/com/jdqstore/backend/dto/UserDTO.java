package com.jdqstore.backend.dto;
import lombok.Data;
import java.util.List;

@Data
public class UserDTO {
    private Long id;
    private String name;
    private String email;
    private String password;
    private String roleName;
    private List<Long> storeIds;
    private Boolean isActive;
}
