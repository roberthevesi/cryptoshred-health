package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.model.MerkleNode;
import com.roberthevesi.cryptoshred_health.repository.MerkleNodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MerkleTreeService — Maintains a binary Merkle Tree over deletion audit hashes
 * backed by persistent PostgreSQL storage to guarantee non-repudiation and inclusion proofs
 * survive server restarts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerkleTreeService {

    private final MerkleNodeRepository merkleNodeRepository;
    private final List<String> leaves = new ArrayList<>();

    @PostConstruct
    public synchronized void init() {
        try {
            List<MerkleNode> persistedNodes = merkleNodeRepository.findAllByOrderByLeafIndexAsc();
            leaves.clear();
            for (MerkleNode node : persistedNodes) {
                leaves.add(node.getLeafHash());
            }
            log.info("Initialized MerkleTree from database. Loaded {} persisted leaves. Current root: {}",
                    leaves.size(), getMerkleRoot());
        } catch (Exception e) {
            log.warn("Failed to load Merkle leaves from database during startup: {}", e.getMessage());
        }
    }

    @Transactional
    public synchronized void addLeaf(String leafHash) {
        int nextIndex = leaves.size();
        try {
            merkleNodeRepository.save(new MerkleNode(nextIndex, leafHash));
            leaves.add(leafHash); // Only add to memory after successful DB persist
        } catch (Exception e) {
            log.warn("Failed to persist Merkle leaf to database: {}", e.getMessage());
        }
        log.info("MerkleTree added leaf at index {}. Total leaves: {}, New Root: {}",
                nextIndex, leaves.size(), getMerkleRoot());
    }

    public synchronized String getMerkleRoot() {
        if (leaves.isEmpty()) {
            return sha256("EMPTY_TREE");
        }
        return computeRoot(leaves);
    }

    public synchronized List<String> getInclusionProof(String leafHash) {
        int index = leaves.indexOf(leafHash);
        if (index == -1) {
            return Collections.emptyList();
        }

        List<String> proofPath = new ArrayList<>();
        List<String> currentLevel = new ArrayList<>(leaves);

        while (currentLevel.size() > 1) {
            if (currentLevel.size() % 2 != 0) {
                currentLevel.add(currentLevel.get(currentLevel.size() - 1));
            }

            List<String> nextLevel = new ArrayList<>();
            int pairIndex = (index % 2 == 0) ? index + 1 : index - 1;

            if (pairIndex < currentLevel.size()) {
                proofPath.add(currentLevel.get(pairIndex));
            }

            for (int i = 0; i < currentLevel.size(); i += 2) {
                nextLevel.add(sha256(currentLevel.get(i) + currentLevel.get(i + 1)));
            }

            index /= 2;
            currentLevel = nextLevel;
        }

        return proofPath;
    }

    public boolean verifyInclusion(String leafHash, List<String> proofPath, String expectedRoot) {
        String currentHash = leafHash;
        for (String sibling : proofPath) {
            if (currentHash.compareTo(sibling) <= 0) {
                currentHash = sha256(currentHash + sibling);
            } else {
                currentHash = sha256(sibling + currentHash);
            }
        }
        return currentHash.equalsIgnoreCase(expectedRoot);
    }

    private String computeRoot(List<String> nodes) {
        if (nodes.size() == 1) {
            return nodes.get(0);
        }

        List<String> currentLevel = new ArrayList<>(nodes);
        if (currentLevel.size() % 2 != 0) {
            currentLevel.add(currentLevel.get(currentLevel.size() - 1));
        }

        List<String> nextLevel = new ArrayList<>();
        for (int i = 0; i < currentLevel.size(); i += 2) {
            nextLevel.add(sha256(currentLevel.get(i) + currentLevel.get(i + 1)));
        }

        return computeRoot(nextLevel);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}
