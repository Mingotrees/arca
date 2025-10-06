package com.popman.arca.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.popman.arca.entity.File;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {
}
