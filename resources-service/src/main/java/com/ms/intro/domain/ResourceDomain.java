package com.ms.intro.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.VarbinaryJdbcType;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "resources")
public class ResourceDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Lob
    @JdbcType(VarbinaryJdbcType.class)
    @Column(name = "blob")
    private byte[] blob;

    public ResourceDomain(byte[] blob) {
        this.blob = blob;
    }
}
