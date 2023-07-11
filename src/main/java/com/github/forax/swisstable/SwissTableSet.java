package com.github.forax.swisstable;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// java --add-modules=jdk.incubator.vector SwissTableSet.java
public class SwissTableSet<E> extends AbstractSet<E> {
  private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;
  private static final int LENGTH = SPECIES.length();

  static {
    if (LENGTH > Integer.SIZE) {
      throw new AssertionError("too big to fit into an int");
    }
  }

  private static final sun.misc.Unsafe UNSAFE;
  private static final long BYTE_ARRAY_OFFSET, BYTE_ARRAY_INDEX_SCALE;
  private static final long OBJECT_ARRAY_OFFSET, OBJECT_ARRAY_INDEX_SCALE;
  static {
    try {
      var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      UNSAFE = (sun.misc.Unsafe) field.get(null);
    } catch (IllegalAccessException | NoSuchFieldException e) {
      throw new AssertionError(e);
    }
    BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    BYTE_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(byte[].class);
    OBJECT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(Object[].class);
    OBJECT_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(Object[].class);
  }

  private static final byte EMPTY = (byte) 0b1000_0000;
  private static final byte DELETED = (byte) 0b1111_1110;
  private static final byte NOT_FULL_MASK = (byte) 0b1000_0000;

  private static final int SEVEN = 7;
  private static final int SEVEN_BITS_MASK = 0b0111_1111;

  private static final int INITIAL_CAPACITY = 2;

  private byte[] controls;
  private E[] slots;
  private int size;
  private int modCount;
  private int capacityLeft;

  @SuppressWarnings("unchecked")
  public SwissTableSet() {
    var controls = new byte[LENGTH * INITIAL_CAPACITY];
    Arrays.fill(controls, EMPTY);
    var slots = (E[]) new Object[LENGTH * INITIAL_CAPACITY];
    this.controls = controls;
    this.slots = slots;
    this.capacityLeft = controls.length >>> 1;
  }

  public int size() {
    return size;
  }

  public boolean add(E element) {
    var hash = element.hashCode();
    var h1 = hash >>> SEVEN;
    var h2 = (byte) (hash & SEVEN_BITS_MASK);
    for(var group = (h1 * LENGTH) & (controls.length - 1);; group = (group + LENGTH) & (controls.length - 1)) {
      var v = ByteVector.fromArray(SPECIES, controls, group);
      var mask = (int) v.eq(h2).toLong();
      for (; mask != 0; mask = mask & (mask - 1)) {
        var trailingZeroes = Integer.numberOfTrailingZeros(mask);
        //if (element.equals(slots[group + trailingZeroes])) {
        if (element.equals(UNSAFE.getObject(slots, OBJECT_ARRAY_OFFSET + OBJECT_ARRAY_INDEX_SCALE * (group + trailingZeroes)))) {
          return false;
        }
      }
      var first = v.eq(EMPTY).firstTrue();
      if (first != LENGTH) {
        if (capacityLeft == 0) {
          return rehash(element);
        }
        //controls[group + first] = h2;
        UNSAFE.putByte(controls, BYTE_ARRAY_OFFSET + BYTE_ARRAY_INDEX_SCALE * (group + first), h2);
        //slots[group + first] = element;
        UNSAFE.putObject(slots, OBJECT_ARRAY_OFFSET + OBJECT_ARRAY_INDEX_SCALE * (group + first), element);
        capacityLeft--;
        size++;
        modCount++;
        return true;
      }
    }
  }

  public boolean contains(Object o) {
    var hash = o.hashCode();
    var h1 = hash >>> SEVEN;
    var h2 = (byte) (hash & SEVEN_BITS_MASK);

    for(var group = (h1 * LENGTH) & (controls.length - 1);; group = (group + LENGTH) & (controls.length - 1)) {
      var v = ByteVector.fromArray(SPECIES, controls, group);
      var mask = (int) v.eq(h2).toLong();
      for (; mask != 0; mask = mask & (mask - 1)) {
        var trailingZeroes = Integer.numberOfTrailingZeros(mask);
        //if (o.equals(slots[group + trailingZeroes])) {
        if (o.equals(UNSAFE.getObject(slots, OBJECT_ARRAY_OFFSET + OBJECT_ARRAY_INDEX_SCALE * (group + trailingZeroes)))) {
          return true;
        }
      }
      var anyTrue = v.eq(EMPTY).anyTrue();
      if (anyTrue) {
        return false;
      }
    }
  }

  public boolean remove(Object o) {
    var hash = o.hashCode();
    var h1 = hash >>> SEVEN;
    var h2 = (byte) (hash & SEVEN_BITS_MASK);
    for(var group = (h1 * LENGTH) & (controls.length - 1);; group = (group + LENGTH) & (controls.length - 1)) {
      var v = ByteVector.fromArray(SPECIES, controls, group);
      var mask = (int) v.eq(h2).toLong();
      for (; mask != 0; mask = mask & (mask - 1)) {
        var trailingZeroes = Integer.numberOfTrailingZeros(mask);
        if (o.equals(slots[group + trailingZeroes])) {
          var anyTrue = v.eq(EMPTY).anyTrue();
          //controls[group + trailingZeroes] = anyTrue? EMPTY: DELETED;
          UNSAFE.putByte(controls, BYTE_ARRAY_OFFSET + BYTE_ARRAY_INDEX_SCALE * (group + trailingZeroes), anyTrue? EMPTY: DELETED);
          //slots[group + trailingZeroes] = null;
          UNSAFE.putObject(slots, OBJECT_ARRAY_OFFSET + OBJECT_ARRAY_INDEX_SCALE * (group + trailingZeroes), null);
          capacityLeft += anyTrue? 1: 0;
          size--;
          modCount++;
          return true;
        }
      }
      var anyTrue = v.eq(EMPTY).anyTrue();
      if (anyTrue) {
        return false;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private boolean rehash(E newElement) {
    var newLength = controls.length << 1;
    var newControls = new byte[newLength];
    Arrays.fill(newControls, EMPTY);
    var newSlots = (E[]) new Object[newLength];

    //System.out.println("newLength " + newLength +  " newCapacityMinusOne " + newCapacityMinusOne);

    for(var group = 0; group < controls.length; group += LENGTH) {
      var v = ByteVector.fromArray(SPECIES, controls, group);
      var controlMask = (int) v.and(NOT_FULL_MASK).eq((byte) 0).toLong();
      for (; controlMask != 0; controlMask = controlMask & (controlMask - 1)) {
        var trailingZeroes = Integer.numberOfTrailingZeros(controlMask);
        //var element = slots[group + trailingZeroes];
        var element = (E) UNSAFE.getObject(slots, OBJECT_ARRAY_OFFSET + OBJECT_ARRAY_INDEX_SCALE * (group + trailingZeroes));
        insert(newLength, newControls, newSlots, element);
      }
    }

    insert(newLength, newControls, newSlots, newElement);
    modCount++;
    size++;

    capacityLeft = controls.length >>> 1 - size;
    controls = newControls;
    slots = newSlots;
    return true;
  }

  private static void insert(int length, byte[] controls, Object[] slots, Object element) {
    var hash = element.hashCode();
    var h1 = hash >>> SEVEN;
    var h2 = (byte) (hash & SEVEN_BITS_MASK);

    //System.out.println("h1 " + h1 + " h2 " + h2);

    for(var g = (h1 * LENGTH) & (length - 1);; g = (g + LENGTH) & (length - 1)) {
      //System.out.println("from array " + controls.length + " " + g);

      var newV = ByteVector.fromArray(SPECIES, controls, g);
      var index = newV.eq(EMPTY).firstTrue();
      if (index != LENGTH) {
        //controls[g + index] = h2;
        UNSAFE.putByte(controls, BYTE_ARRAY_OFFSET + BYTE_ARRAY_INDEX_SCALE * (g + index), h2);
        //slots[g + index] = element;
        UNSAFE.putObject(slots, OBJECT_ARRAY_OFFSET + OBJECT_ARRAY_INDEX_SCALE * (g + index), element);
        return;
      }
    }
  }

  @Override
  public String toString() {
    var joiner = new StringJoiner(", ", "[", "]");
    for(var group = 0; group < controls.length; group += LENGTH) {
      var v = ByteVector.fromArray(SPECIES, controls, group);
      var controlMask = (int) v.and(NOT_FULL_MASK).eq((byte) 0).toLong();
      for (; controlMask != 0; controlMask = controlMask & (controlMask - 1)) {
        var trailingZeroes = Integer.numberOfTrailingZeros(controlMask);
        //joiner.add(slots[group + trailingZeroes].toString());
        joiner.add(UNSAFE.getObject(slots, OBJECT_ARRAY_OFFSET + OBJECT_ARRAY_INDEX_SCALE * (group + trailingZeroes)).toString());
      }
    }
    return joiner.toString();
  }

  @Override
  public Iterator<E> iterator() {
    var modCount = this.modCount;
    return new Iterator<>() {
      private int group;
      private int controlMask = findNextControlMask(0);

      private int findNextControlMask(int group) {
        var controls = SwissTableSet.this.controls;
        for(; group < controls.length; group += LENGTH) {
          var v = ByteVector.fromArray(SPECIES, controls, group);
          var controlMask = (int) v.and(NOT_FULL_MASK).eq((byte) 0).toLong();
          if (controlMask != 0) {
            this.group = group;
            return controlMask;
          }
        }
        this.group = group;
        return 0;
      }

      @Override
      public boolean hasNext() {
        return controlMask != 0;
      }

      @Override
      public E next() {
        if (modCount != SwissTableSet.this.modCount) {
          throw new ConcurrentModificationException();
        }
        var controlMask = this.controlMask;
        if (controlMask == 0) {
          throw new NoSuchElementException();
        }
        var trailingZeroes = Integer.numberOfTrailingZeros(controlMask);
        //var element = slots[group + trailingZeroes];
        @SuppressWarnings("unchecked")
        var element = (E) UNSAFE.getObject(slots, OBJECT_ARRAY_OFFSET + OBJECT_ARRAY_INDEX_SCALE * (group + trailingZeroes));
        controlMask = controlMask & (controlMask - 1);
        if (controlMask == 0) {
          controlMask = findNextControlMask(group + LENGTH);
        }
        this.controlMask = controlMask;
        return element;
      }
    };
  }

  String debug() {
    return """
        controls: %s
        slots   : %s
        """.formatted(
          IntStream.range(0, controls.length).mapToObj(i -> String.format("%8s", Integer.toBinaryString(controls[i] & 0xFF)).replace(' ', '0')).collect(Collectors.joining(",")),
          Arrays.stream(slots).map(String::valueOf).collect(Collectors.joining(","))
        );
  }
}
