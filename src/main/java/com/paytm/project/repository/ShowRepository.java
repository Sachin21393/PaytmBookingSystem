package com.paytm.project.repository;

import com.paytm.project.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    Optional<Show> findByName(String name);

    boolean existsByName(String name);
}
