package com.android.navigation;

import com.android.annotations.Property;

public class TestClassB {
  public final String name;
  private int value;

  public TestClassB(@Property("name") String name) {
    this.name = name;
  }

  public int getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return "TestClassA{" +
           "name='" + name + '\'' +
           ", value=" + value +
           '}';
  }
}
