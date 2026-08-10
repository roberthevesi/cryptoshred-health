package com.roberthevesi.cryptoshred_health.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MerkleTreeService — Maintains a binary Merkle Tree over deletion audit hashes
 * to provide non-repudiation and inclusion proofs for crypto-shredding events.
 */
@Service
@Slf4j
public class MerkleTreeService {

    private final List<String> leaves = new ArrayList<>();

    public synchronized void addLeaf(String leafHash) {
        leaves.add(leafHash);
        log.info("MerkleTree added leaf. Total leaves: {}, New Root: {}", leaves.size(), getMerkleRoot());
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
            // Lexicographical ordering or positional pairing for deterministic root
            if (currentHash.compareTo(sibling) <= 0) {
                currentHash = sha256(currentHash + sibling);
            } else {
                currentHash = sha256(sibling + currentHash);
            }
        }
        // Also check straightforward positional hash if ordering mismatch
        if (!currentHash.equalsIgnoreCase(expectedRoot)) {
            currentHash = leafHash;
            for (String sibling : proofPath) {
                currentHash = sha256(currentHash + sibling);
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

    public static String sha256(String input) {
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
