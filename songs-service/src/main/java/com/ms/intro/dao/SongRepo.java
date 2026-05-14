package com.ms.intro.dao;

import com.ms.intro.domain.SongData;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface SongRepo extends CrudRepository<SongData, Integer> {
    Optional<SongData> findByResourceId(int resourceId);
    void deleteByResourceId(int resourceId);
}