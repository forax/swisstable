package com.github.forax.swisstable;

import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class SwissTableSetTest {
  @Test
  public void add() {
    var count = 20;
    var set = new SwissTableSet<Integer>();
    IntStream.range(0, count)
        .forEach(i -> assertTrue(set.add(i)));
    assertAll(
        () -> assertEquals(count, set.size()),
        () -> IntStream.range(0, count).forEach(i -> assertTrue(set.contains(i)))
    );
  }

  @Test
  public void addSame() {
    var count = 20;
    var set = new SwissTableSet<Integer>();
    IntStream.range(0, count).forEach(set::add);
    IntStream.range(0, count).forEach(i -> assertFalse(set.add(i)));
    assertAll(
        () -> assertEquals(count, set.size()),
        () -> IntStream.range(0, count).forEach(i -> assertTrue(set.contains(i)))
    );
  }

  @Test
  public void addWithCollision() {
    var set = new SwissTableSet<Integer>();
    set.add(42);
    set.add(42 + 256);
    assertAll(
        () -> assertTrue(set.contains(42)),
        () -> assertTrue(set.contains(42 + 256))
    );
  }

  @Test
  public void addBadHashCode() {
    record BadHashInteger(int value) {
      @Override
      public int hashCode() {
        return 42;
      }
    }

    var count = 20;
    var set = new SwissTableSet<BadHashInteger>();
    IntStream.range(0, count).forEach(i -> set.add(new BadHashInteger(i)));
    assertAll(
        () -> assertEquals(count, set.size()),
        () -> IntStream.range(0, count).forEach(i -> assertTrue(set.contains(new BadHashInteger(i))))
    );
  }

  @Test
  public void addAndToString() {
    var count = 20;
    var set = new SwissTableSet<Integer>();
    IntStream.range(0, count).forEach(set::add);
    assertAll(
        () -> assertEquals(count, set.size()),
        () -> assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19]", set.toString())
    );
  }

  @Test
  public void notContained() {
    var count = 100_000;
    var set = new SwissTableSet<Integer>();
    IntStream.range(0, count).forEach(i -> assertFalse(set.contains(i)));
  }

  @Test
  public void addAndRemove() {
    var count = 20;
    var set = new SwissTableSet<Integer>();
    IntStream.range(0, count)
        .forEach(i -> assertTrue(set.add(i)));
    IntStream.range(0, count)
        .forEach(i -> assertTrue(set.remove(i)));
    assertAll(
        () -> assertEquals(0, set.size()),
        () -> IntStream.range(0, count).forEach(i -> assertFalse(set.contains(i)))
    );
  }

  @Test
  public void notRemoved() {
    var count = 100_000;
    var set = new SwissTableSet<Integer>();
    IntStream.range(0, count).forEach(i -> assertFalse(set.remove(i)));
  }

  @Test
  public void addAndRemoveAndToString() {
    var set = new SwissTableSet<Integer>();
    IntStream.range(0, 10).forEach(set::add);
    set.remove(8);
    set.add(18);
    assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 18, 9]", set.toString());
  }

  @Test
  public void iterator() {
    var count = 20;
    var set = new SwissTableSet<Integer>();
    IntStream.range(0, count)
        .forEach(i -> assertTrue(set.add(i)));
    var result = new HashSet<Integer>();
    set.forEach(result::add);
    assertAll(
        () -> assertEquals(set.size(), result.size()),
        () -> IntStream.range(0, count).forEach(i -> assertTrue(result.contains(i)))
    );
  }

  @Test
  public void iteratorNoElement() {
    var set = new SwissTableSet<Integer>();
    var iterator = set.iterator();
    assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  public void iteratorFailFastAdd() {
    var set = new SwissTableSet<Integer>();
    set.add(1);
    var iterator = set.iterator();
    set.add(3);
    assertThrows(ConcurrentModificationException.class, iterator::next);
  }

  @Test
  public void iteratorFailFastRemove() {
    var set = new SwissTableSet<Integer>();
    set.add(1);
    var iterator = set.iterator();
    set.remove(1);
    assertThrows(ConcurrentModificationException.class, iterator::next);
  }
}