package com.ms.intro.client;

import com.ms.intro.dto.SongDataDeletedResponse;
import com.ms.intro.dto.SongDataDto;
import com.ms.intro.dto.SongDataUploadedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "${songs.url}"
        ,
//        url = "http://${songs.url:localhost:8081}"
        url = "http://${songs.url:localhost}:${songs.port:8081}"
)
public interface SongServiceClient {

    @PostMapping("/songs")
    ResponseEntity<SongDataUploadedResponse> saveSongMetadata(@RequestBody SongDataDto songMetadataDto);

    @DeleteMapping("/songs/by-resource-id")
    ResponseEntity<SongDataDeletedResponse> deleteSongsMetadata(@RequestParam List<Integer> ids);

}
