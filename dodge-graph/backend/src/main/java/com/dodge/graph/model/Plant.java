package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "plants")
@Data
public class Plant {
    @Id
    @Column(name = "plant")
    private String plant;
    private String plantName;
    private String salesOrganization;
    private String distributionChannel;
    private String division;
    private String language;
}
