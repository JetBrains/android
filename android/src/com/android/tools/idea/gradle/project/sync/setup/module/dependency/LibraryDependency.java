/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.dependency;

import com.android.tools.idea.io.FilePaths;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.util.ArrayUtilRt.EMPTY_FILE_ARRAY;

/**
 * An IDEA module's dependency on a library (e.g. a jar file.)
 */
public class LibraryDependency extends Dependency {
  @NotNull private final Collection<File> myBinaryPaths = new HashSet<>();
  @NotNull private final File myArtifactPath;

  private String myName;

  /**
   * Creates a new {@link LibraryDependency}.
   *
   * @param artifactPath the path, in the file system, of the binary file that represents the library to depend on.
   * @param scope        the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  @VisibleForTesting
  public LibraryDependency(@NotNull File artifactPath, @NotNull DependencyScope scope) {
    this(artifactPath, getNameWithoutExtension(artifactPath), scope);
    addBinaryPath(artifactPath);
  }

  /**
   * Creates a new {@link LibraryDependency}.
   *
   * @param artifactPath the path, in the file system, of the binary file that represents the library to depend on.
   * @param name  the name of the library to depend on.
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  LibraryDependency(@NotNull File artifactPath, @NotNull String name, @NotNull DependencyScope scope) {
    super(scope);
    myArtifactPath = artifactPath;
    setName(name);
  }

  void addBinaryPath(@NotNull File path) {
    myBinaryPaths.add(path);
  }

  void addBinaryPath(@NotNull String path) {
    addBinaryPath(FilePaths.toSystemDependentPath(path));
  }

  @NotNull
  public File[] getBinaryPaths() {
    return myBinaryPaths.isEmpty() ? EMPTY_FILE_ARRAY : myBinaryPaths.toArray(new File[myBinaryPaths.size()]);
  }

  @NotNull
  public File getArtifactPath() {
    return myArtifactPath;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  void setName(@NotNull String name) {
    // let's use the same format for libraries imported from Gradle, to be compatible with API like ExternalSystemApiUtil.isExternalSystemLibrary()
    // and be able to reuse common cleanup service, see LibraryDataService.postProcess()
    String prefix = GradleConstants.SYSTEM_ID.getReadableName() + ": ";
    myName = name.isEmpty() || StringUtil.startsWith(name, prefix) ? name : prefix + name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LibraryDependency)) {
      return false;
    }
    LibraryDependency that = (LibraryDependency)o;
    return Objects.equals(myBinaryPaths, that.myBinaryPaths) &&
           Objects.equals(myArtifactPath, that.myArtifactPath) &&
           Objects.equals(myName, that.myName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myBinaryPaths, myArtifactPath, myName);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" +
           "name='" + myName + '\'' +
           ", scope=" + getScope() +
           ", pathsByType=" + myBinaryPaths +
           "]";
  }
}
