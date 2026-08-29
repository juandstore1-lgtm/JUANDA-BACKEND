package com.jdqstore.backend.dto;
import lombok.Data;
@Data
public class GalleryDTO {
    private Long id;
    private String url;
    private String title;
    private String description;
    private Long storeId;
    private Integer order;
}
