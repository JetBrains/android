/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.model.java;

import org.gradle.tooling.model.GradleModuleVersion;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.io.FileUtil.sanitizeFileName;

/**
 * Dependency to a Jar library.
 */
public class JarLibraryDependency implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull @NonNls private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";

  @NotNull private final String myName;

  @Nullable private final File myBinaryPath;
  @Nullable private final File mySourcePath;
  @Nullable private final File myJavadocPath;
  @Nullable private final String myScope;

  @Nullable private final GradleModuleVersion myModuleVersion;
  private final boolean myResolved;

  @NotNull
  public static String getDependencyName(@NotNull File binaryPath, boolean resolved) {
    if (resolved) {
      // Gradle API doesn't provide library name at the moment.
      return binaryPath.isFile() ? getNameWithoutExtension(binaryPath) : sanitizeFileName(binaryPath.getPath());
    }
    else {
      // Gradle uses names like 'unresolved dependency - commons-collections commons-collections 3.2' for unresolved dependencies.
      // We report the unresolved dependency as 'commons-collections:commons-collections:3.2'
      return binaryPath.getName().substring(UNRESOLVED_DEPENDENCY_PREFIX.length()).replace(' ', ':');
    }
  }

  public static boolean isResolved(@NotNull String libraryName) {
    return !libraryName.startsWith(UNRESOLVED_DEPENDENCY_PREFIX);
  }

  public JarLibraryDependency(@NotNull String name,
                              @Nullable File binaryPath,
                              @Nullable File sourcePath,
                              @Nullable File javadocPath,
                              @Nullable String scope,
                              @Nullable GradleModuleVersion moduleVersion,
                              boolean resolved) {
    myName = name;
    myBinaryPath = binaryPath;
    mySourcePath = sourcePath;
    myJavadocPath = javadocPath;
    myScope = scope;
    myModuleVersion = moduleVersion;
    myResolved = resolved;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public File getBinaryPath() {
    return myBinaryPath;
  }

  @Nullable
  public File getSourcePath() {
    return mySourcePath;
  }

  @Nullable
  public File getJavadocPath() {
    return myJavadocPath;
  }

  @Nullable
  public String getScope() {
    return myScope;
  }

  @Nullable
  public GradleModuleVersion getModuleVersion() {
    return myModuleVersion;
  }

  public boolean isResolved() {
    return myResolved;
  }
}
