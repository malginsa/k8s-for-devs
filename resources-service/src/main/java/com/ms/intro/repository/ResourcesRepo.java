package com.ms.intro.repository;

import com.ms.intro.domain.ResourceDomain;
import org.springframework.data.repository.CrudRepository;

public interface ResourcesRepo extends CrudRepository<ResourceDomain, Integer> {
}
