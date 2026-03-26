package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "products")
@Data
public class Product {
    @Id
    @Column(name = "product")
    private String product;
    private String productType;
    private String productOldId;
    private String productGroup;
    private String baseUnit;
    private String division;
    private String grossWeight;
    private String weightUnit;
    private String creationDate;
}
