package com.roberthevesi.cryptoshred_health.repository;

import com.roberthevesi.cryptoshred_health.model.MerkleNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MerkleNodeRepository extends JpaRepository<MerkleNode, UUID> {
    List<MerkleNode> findAllByOrderByLeafIndexAsc();
    boolean existsByLeafHash(String leafHash);
}
