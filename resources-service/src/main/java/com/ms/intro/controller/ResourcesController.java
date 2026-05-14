package com.ms.intro.controller;

import com.ms.intro.domain.ResourceDomain;
import com.ms.intro.dto.IdDto;
import com.ms.intro.exceptions.CannotUploadFileException;
import com.ms.intro.exceptions.FileNotExistException;
import com.ms.intro.exceptions.InvalidIdsForDeletionException;
import com.ms.intro.exceptions.NotMp3Exception;
import com.ms.intro.repository.ResourcesRepo;
import com.ms.intro.service.ResourcesService;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@RestController
@RequestMapping(path = "/resources")
@AllArgsConstructor
public class ResourcesController  {

    private final ResourcesRepo repo;
    private final ResourcesService service;
    private Environment env;

    @PostMapping(path = "/file")
    @ResponseStatus(value = HttpStatus.OK)
    public IdDto createResource(@RequestParam("file") MultipartFile file) {
        if(file == null || !StringUtils.endsWith(file.getOriginalFilename(), ".mp3")){
            throw new NotMp3Exception();
        }
        ResourceDomain savedResource = null;
        try {
            savedResource = service.saveResource(file.getBytes());
        } catch(IOException e) {
            throw new CannotUploadFileException(e);
        }
        return new IdDto(savedResource.getId());
    }
    @PostMapping( consumes = "audio/mpeg")
    @ResponseStatus(value = HttpStatus.OK)
    public IdDto createResource(@RequestBody byte[] bytes) {
        var savedResource = service.saveResource(bytes);
        return new IdDto(savedResource.getId());
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Resource> getResource(@PathVariable("id") int id) {
        ByteArrayResource bytes = service.getResource(Integer.valueOf(id));
        return ResponseEntity.ok()
                .contentLength(bytes.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public int[] deleteResource(@RequestParam("ids") @Size(min = 1, max = 200) String id) {
        Stream<ResourceDomain> resources = Stream.of(id.split(","))
                .map(String::strip)
                .filter(this::validateIds)
                .mapToInt(Integer::parseInt)
                .mapToObj(repo::findById)
                .filter(Optional::isPresent)
                .map(Optional::get);
        List<Integer> removedIds = service.deleteResources(resources);
        return removedIds.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean validateIds(String id) {
        if(StringUtils.isNumeric(id)) {
            return true;
        }
        throw new InvalidIdsForDeletionException("Invalid Ids parameter!");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({NotMp3Exception.class, CannotUploadFileException.class, InvalidIdsForDeletionException.class})
    public String return400(Exception ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({FileNotExistException.class})
    public String return404(Exception ex) {
        return ex.getMessage();
    }

}
