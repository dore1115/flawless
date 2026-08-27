package com.dsa.flawless.repository;

import com.dsa.flawless.domain.Position;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByActiveTrue();
}
