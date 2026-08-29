package com.jdqstore.backend.dto;
import lombok.Data;
@Data
public class ContactDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String subject;
    private String message;
    private String status;
}
