package com.ms.intro.controller;

import com.ms.intro.dao.SongRepo;
import com.ms.intro.domain.SongData;
import com.ms.intro.dto.IdDto;
import com.ms.intro.dto.SongDto;
import com.ms.intro.exceptions.DuplicatedResourceIdException;
import com.ms.intro.exceptions.InvalidIdsForDeletionException;
import com.ms.intro.exceptions.NotFoundSongDataException;
import com.ms.intro.mapper.SongDtoToDomainMapper;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping(path = "/songs")
@AllArgsConstructor
public class SongController {

    private final SongRepo songRepo;

    @GetMapping(path = "/{id}", produces = "application/json")
    @ResponseBody
    public SongDto getSongMetadataByResourceId(@PathVariable("id") int id) {
        SongData song = songRepo.findByResourceId(id)
                .orElseThrow(() -> new NotFoundSongDataException("File info on id '" + id + "' does not exist!"));
        return SongDtoToDomainMapper.INSTANCE.songToCarDto(song);
    }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public IdDto saveSongMetadata(@Validated @RequestBody SongDto song) {
        songRepo.findByResourceId(song.getResourceId().intValue())
                .ifPresent(el -> {
                    throw new DuplicatedResourceIdException("Duplicated \"resourceId\": " + song.getResourceId().intValue());
                });
        SongData savedMeta = SongDtoToDomainMapper.INSTANCE.songToCarDto(song);
        savedMeta = songRepo.save(savedMeta);
        return new IdDto(savedMeta.getId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public String deleteSongMetadata(@RequestParam("ids") @Size(min = 1, max = 200) String ids) {
        return Stream.of(ids.split(","))
                .map(String::strip)
                .filter(this::validateIds)
                .mapToInt(Integer::parseInt)
                .filter(i -> songRepo.findById(i).isPresent())
                .map(i -> {
                    songRepo.deleteById(Integer.valueOf(i));
                    return i;
                })
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(",", "{ \"ids\":[ ", " ] }"));
    }

    @DeleteMapping("/by-resource-id")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    @Transactional
    public String deleteSongMetadataByResourceIds(@RequestParam("ids") @Size(min = 1, max = 200) String ids) {
        return Stream.of(ids.split(","))
                .map(String::strip)
                .filter(this::validateIds)
                .mapToInt(Integer::parseInt)
                .filter(i -> songRepo.findByResourceId(i).isPresent())
                .map(i -> {
                    songRepo.deleteByResourceId(Integer.valueOf(i));
                    return i;
                })
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(",", "{ \"ids\":[ ", " ] }"));
    }

    private boolean validateIds(String id) {
        if(StringUtils.isNumeric(id)) {
            return true;
        }
        throw new InvalidIdsForDeletionException(String.format("Invalid Ids parameter: %s!", id));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({InvalidIdsForDeletionException.class, DuplicatedResourceIdException.class})
    public String return400(Exception ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundSongDataException.class)
    public String return404(Exception ex) {
        return ex.getMessage();
    }

}
