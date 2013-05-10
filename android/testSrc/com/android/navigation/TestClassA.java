package com.android.navigation;

import com.android.annotations.Property;

public class TestClassA {
  public final String name;
  private int value;
  public TestClassB child;

  public TestClassA(@Property("name") String name) {
    this.name = name;
  }

  public int getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

  public TestClassB getChild() {
    return child;
  }

  public void setChild(TestClassB child) {
    this.child = child;
  }

  @Override
  public String toString() {
    return "TestClassA{" +
           "name='" + name + '\'' +
           ", value=" + value +
           ", child=" + child +
           '}';
  }
}
