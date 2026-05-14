package com.ms.intro.service;

import com.ms.intro.dto.SongDataDto;
import com.ms.intro.exceptions.MetadataExtractionFailException;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
public class SongDataParserService {
    public SongDataDto getSongData(byte[] bytes) {
        SongDataDto songDataDto = new SongDataDto();
        Metadata songData = getMetaData(bytes);
        songDataDto.setName(StringUtils.defaultString(songData.get("dc:title")));
        songDataDto.setArtist(StringUtils.defaultString(songData.get("xmpDM:artist")));
        songDataDto.setAlbum(StringUtils.defaultString(songData.get("xmpDM:album")));
        songDataDto.setLength(StringUtils.defaultString(songData.get("xmpDM:duration")));
        songDataDto.setYear(StringUtils.defaultString(songData.get("xmpDM:releaseDate")));
        return songDataDto;
    }

    public Metadata getMetaData(byte[] bytes){
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext pContext = new ParseContext();
        Mp3Parser mp3Parser = new Mp3Parser();

        try(InputStream inputStream = new ByteArrayInputStream(bytes)) {
            mp3Parser.parse(inputStream, handler, metadata, pContext);
        } catch(Exception e) {
            throw new MetadataExtractionFailException(e);
        }
        return metadata;
    }
}
