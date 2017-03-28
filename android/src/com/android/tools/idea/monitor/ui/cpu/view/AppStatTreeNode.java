package com.android.tools.idea.monitor.ui.cpu.view;

import javax.swing.tree.DefaultMutableTreeNode;

public class AppStatTreeNode extends DefaultMutableTreeNode {
  private long runtimeInclusive;
  private long runtimeExclusive;

  private double percentageInclusive;
  private double percentageExclusive;

  private String methodName;
  private String methodNamespace;

  public long getRuntimeInclusive() {
    return runtimeInclusive;
  }

  public void setRuntimeInclusive(long runtimeInclusive) {
    this.runtimeInclusive = runtimeInclusive;
  }

  public long getRuntimeExclusive() {
    return runtimeExclusive;
  }

  public void setRuntimeExclusive(long runtimeExclusive) {
    this.runtimeExclusive = runtimeExclusive;
  }

  public double getPercentageInclusive() {
    return percentageInclusive;
  }

  public void setPercentageInclusive(double percentageInclusive) {
    this.percentageInclusive = percentageInclusive;
  }

  public double getPercentageExclusive() {
    return percentageExclusive;
  }

  public void setPercentageExclusive(double percentageExclusive) {
    this.percentageExclusive = percentageExclusive;
  }

  public String getMethodName() {
    return methodName;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  public String getMethodNamespace() {
    return methodNamespace;
  }

  public void setMethodNamespace(String methodNamespace) {
    this.methodNamespace = methodNamespace;
  }
}
