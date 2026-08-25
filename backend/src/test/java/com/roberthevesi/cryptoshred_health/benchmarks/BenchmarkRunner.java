package com.roberthevesi.cryptoshred_health.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        Options opt;
        if (args.length > 0) {
            opt = new CommandLineOptions(args);
        } else {
            opt = new OptionsBuilder()
                    .include("com.roberthevesi.cryptoshred_health.benchmarks.*")
                    .forks(1)
                    .build();
        }
        new Runner(opt).run();
    }
}
