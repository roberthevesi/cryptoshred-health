package com.roberthevesi.cryptoshred_health.benchmarks;

import com.roberthevesi.cryptoshred_health.service.MerkleTreeService;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class MerkleTreeBenchmark {

    @Param({"100", "1000", "10000", "100000"})
    private int leafCount;

    private MerkleTreeService merkleTreeService;
    private List<String> precomputedLeaves;
    private String targetLeaf;
    private String newLeafHash;

    @Setup(Level.Iteration)
    public void setupIteration() {
        merkleTreeService = new MerkleTreeService(null);

        precomputedLeaves = new ArrayList<>(leafCount);
        for (int i = 0; i < leafCount; i++) {
            precomputedLeaves.add(sha256("DELETION_AUDIT_LOG_LEAF_INDEX_" + i));
        }

        for (String leaf : precomputedLeaves) {
            merkleTreeService.addLeaf(leaf);
        }

        targetLeaf = precomputedLeaves.get(leafCount / 2);
        newLeafHash = sha256("NEW_VERIFIABLE_AUDIT_LEAF_" + System.nanoTime());
    }

    @Benchmark
    public void computeRoot(Blackhole bh) {
        String root = merkleTreeService.getMerkleRoot();
        bh.consume(root);
    }

    @Benchmark
    public void generateAndVerifyInclusionProof(Blackhole bh) {
        String root = merkleTreeService.getMerkleRoot();
        List<String> proofPath = merkleTreeService.getInclusionProof(targetLeaf);
        boolean isValid = merkleTreeService.verifyInclusion(targetLeaf, proofPath, root);
        bh.consume(proofPath);
        bh.consume(isValid);
    }

    @Benchmark
    public void addLeaf(Blackhole bh) {
        merkleTreeService.addLeaf(newLeafHash);
        bh.consume(merkleTreeService);
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
