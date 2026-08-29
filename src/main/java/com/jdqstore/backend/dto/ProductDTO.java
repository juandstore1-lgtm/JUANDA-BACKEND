package com.jdqstore.backend.dto;
import lombok.Data;
@Data
public class ProductDTO {
    private Long id;
    private String name;
    private java.math.BigDecimal price;
    private java.math.BigDecimal oldPrice;
    private Integer discountPercentage;
    private String description;
    private String category;
    private java.util.List<String> sizes;
    private java.util.List<String> colors;
    private java.util.List<String> images;
    private java.util.List<Long> storeIds;
    private String status;
    private java.util.List<String> tags;
    private Integer order;
}
