package org.jetbrains.jps.android;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class ProGuardOptions {
  private final List<File> myCfgFiles;

  public ProGuardOptions(@Nullable List<File> cfgFiles) {
    myCfgFiles = cfgFiles;
  }

  @Nullable
  public List<File> getCfgFiles() {
    return myCfgFiles;
  }
}
