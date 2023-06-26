package com.github.forax.swisstable;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = { "--add-modules", "jdk.incubator.vector" })
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class Benchmarks {
  static {
    //System.setProperty("jdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK", "0");
  }

  //private SwissTableSet<Integer> tableSet = new SwissTableSet<>();
  //private HashSet<Integer> hashSet = new HashSet<>();

  private SwissTableSet<Integer> populatedTableSet = new SwissTableSet<>();
  private HashSet<Integer> populatedHashSet = new HashSet<>();
  {
    for(var i = 0; i < 10; i++) {
      populatedTableSet.add(i * 2);
      populatedHashSet.add(i * 2);
    }
  }

  @Benchmark
  public int swiss_table_add() {
    var set = populatedTableSet;
    for(var i = 0; i < 20; i++) {
      set.add(i);
    }
    return set.size();
  }

  @Benchmark
  public int set_add() {
    var set = populatedHashSet;
    for(var i = 0; i < 20; i++) {
      set.add(i);
    }
    return set.size();
  }

  //@Benchmark
  public int swiss_table_contains() {
    var set = populatedTableSet;
    var sum = 0;
    for(var i = 0; i < 20; i++) {
      if (set.contains(i)) {
        sum += i;
      }
    }
    return sum;
  }

  //@Benchmark
  public int set_contains() {
    var set = populatedHashSet;
    var sum = 0;
    for(var i = 0; i < 20; i++) {
      if (set.contains(i)) {
        sum += i;
      }
    }
    return sum;
  }
}
