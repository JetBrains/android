package org.jetbrains.jps.android.model.impl;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public enum AndroidImportableProperty {
  MANIFEST_FILE_PATH("Manifest file path"),
  RESOURCES_DIR_PATH("Resources directory path"),
  ASSETS_DIR_PATH("Assets directory path"),
  NATIVE_LIBS_DIR_PATH("Native libs directory path");

  private final String myDisplayName;

  AndroidImportableProperty(@NotNull String displayName) {
    myDisplayName = displayName;
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }
}
