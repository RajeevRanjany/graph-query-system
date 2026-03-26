package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "business_partners")
@Data
public class BusinessPartner {
    @Id
    @Column(name = "business_partner")
    private String businessPartner;
    private String customer;
    private String businessPartnerFullName;
    private String businessPartnerName;
    private String businessPartnerCategory;
    private String firstName;
    private String lastName;
    private String organizationBpName1;
    private String industry;
    private String creationDate;
}
