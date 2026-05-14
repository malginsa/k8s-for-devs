package com.ms.intro.domain;

import com.ms.intro.dto.SongDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Objects;

@Entity
@Table(name = "songs")
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class SongData {

    @Id
    @GeneratedValue
    int id;

    @Column(nullable = true)
    String name;

    @Column(nullable = true)
    String artist;

    @Column(nullable = true)
    String album;

    @Column(nullable = true)
    String length;

    @Column(unique = true, nullable = false)
    Integer resourceId;

    @Column(nullable = true)
    String year;
}
