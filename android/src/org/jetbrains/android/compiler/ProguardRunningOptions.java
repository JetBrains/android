package org.jetbrains.android.compiler;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class ProguardRunningOptions {
  private final List<String> myProguardCfgFiles;

  public ProguardRunningOptions(@NotNull List<String> proguardCfgFiles) {
    myProguardCfgFiles = proguardCfgFiles;
  }

  @NotNull
  public List<String> getProguardCfgFiles() {
    return myProguardCfgFiles;
  }
}
