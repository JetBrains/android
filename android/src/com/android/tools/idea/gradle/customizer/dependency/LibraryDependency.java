/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.dependency;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * An IDEA module's dependency on a library (e.g. a jar file.)
 */
public class LibraryDependency extends Dependency {
  @NotNull private final Map<PathType, Collection<String>> myPathsByType = Maps.newEnumMap(PathType.class);

  private String myName;

  /**
   * Creates a new {@link LibraryDependency}.
   *
   * @param binaryPath the path, in the file system, of the binary file that represents the library to depend on.
   * @param scope      the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  @VisibleForTesting
  public LibraryDependency(@NotNull File binaryPath, @NotNull DependencyScope scope) {
    this(FileUtil.getNameWithoutExtension(binaryPath), scope);
    addPath(PathType.BINARY, binaryPath);
  }

  /**
   * Creates a new {@link LibraryDependency}.
   *
   * @param name  the name of the library to depend on.
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  LibraryDependency(@NotNull String name, @NotNull DependencyScope scope) {
    super(scope);
    setName(name);
  }

  void addPath(@NotNull PathType type, @NotNull File path) {
    Collection<String> paths = myPathsByType.get(type);
    if (paths == null) {
      paths = Sets.newHashSet();
      myPathsByType.put(type, paths);
    }
    paths.add(path.getPath());
  }

  @NotNull
  public Collection<String> getPaths(@NotNull PathType type) {
    Collection<String> paths = myPathsByType.get(type);
    return paths == null ? Collections.<String>emptyList() : paths;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" +
           "name='" + myName + '\'' +
           ", scope=" + getScope() +
           ", pathsByType=" + myPathsByType +
           "]";
  }

  public enum PathType {
    BINARY, SOURCE, DOC
  }
}
