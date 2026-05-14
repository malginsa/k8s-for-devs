package com.ms.intro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SongDataDto {

    int id;

    String name;

    String artist;

    String album;

    String length;

    @NotNull Integer resourceId;

    String year;
}
