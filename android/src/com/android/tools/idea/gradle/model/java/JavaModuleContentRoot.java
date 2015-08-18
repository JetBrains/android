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

import com.google.common.collect.Sets;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaContentRoot;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Content root of a Java module.
 */
public class JavaModuleContentRoot implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final File myRootDirPath;
  @NotNull private final Collection<File> mySourceDirPaths;
  @NotNull private final Collection<File> myGenSourceDirPaths;
  @NotNull private final Collection<File> myResourceDirPaths;
  @NotNull private final Collection<File> myTestDirPaths;
  @NotNull private final Collection<File> myGenTestDirPaths;
  @NotNull private final Collection<File> myTestResourceDirPaths;
  @NotNull private final Collection<File> myExcludeDirPaths;

  @NotNull
  public static JavaModuleContentRoot copy(@NotNull IdeaContentRoot original) {
    File rootDirPath = original.getRootDirectory();
    Collection<File> sourceDirPaths = copy(original.getSourceDirectories());
    Collection<File> genSourceDirPaths = copy(original.getGeneratedSourceDirectories());
    Collection<File> testDirPaths = copy(original.getTestDirectories());
    Collection<File> genTestDirPaths = copy(original.getGeneratedTestDirectories());

    Collection<File> resourceDirPaths = Collections.emptySet();
    Collection<File> testResourceDirPaths = Collections.emptySet();
    if (original instanceof ExtIdeaContentRoot) {
      ExtIdeaContentRoot extContentRoot = (ExtIdeaContentRoot)original;
      resourceDirPaths = copy(extContentRoot.getResourceDirectories());
      testResourceDirPaths = copy(extContentRoot.getTestResourceDirectories());
    }
    Collection<File> excludeDirPaths = Collections.emptySet();
    Set<File> exclude = original.getExcludeDirectories();
    if (exclude != null) {
      excludeDirPaths = Sets.newHashSet();
      for (File path : exclude) {
        if (path != null) {
          excludeDirPaths.add(path);
        }
      }
    }
    return new JavaModuleContentRoot(rootDirPath, sourceDirPaths, genSourceDirPaths, resourceDirPaths, testDirPaths, genTestDirPaths,
                                     testResourceDirPaths, excludeDirPaths);
  }

  @NotNull
  private static Collection<File> copy(@Nullable DomainObjectSet<? extends IdeaSourceDirectory> directories) {
    if (directories == null) {
      return Collections.emptySet();
    }
    Set<File> paths = Sets.newHashSet();
    for (IdeaSourceDirectory directory : directories) {
      paths.add(directory.getDirectory());
    }
    return paths;
  }

  public JavaModuleContentRoot(@NotNull File rootDirPath,
                               @NotNull Collection<File> sourceDirPaths,
                               @NotNull Collection<File> genSourceDirPaths,
                               @NotNull Collection<File> resourceDirPaths,
                               @NotNull Collection<File> testDirPaths,
                               @NotNull Collection<File> genTestDirPaths,
                               @NotNull Collection<File> testResourceDirPaths,
                               @NotNull Collection<File> excludeDirPaths) {
    myRootDirPath = rootDirPath;
    mySourceDirPaths = sourceDirPaths;
    myGenSourceDirPaths = genSourceDirPaths;
    myResourceDirPaths = resourceDirPaths;
    myTestDirPaths = testDirPaths;
    myGenTestDirPaths = genTestDirPaths;
    myTestResourceDirPaths = testResourceDirPaths;
    myExcludeDirPaths = excludeDirPaths;
  }

  @NotNull
  public File getRootDirPath() {
    return myRootDirPath;
  }

  @NotNull
  public Collection<File> getSourceDirPaths() {
    return mySourceDirPaths;
  }

  @NotNull
  public Collection<File> getGenSourceDirPaths() {
    return myGenSourceDirPaths;
  }

  @NotNull
  public Collection<File> getResourceDirPaths() {
    return myResourceDirPaths;
  }

  @NotNull
  public Collection<File> getTestDirPaths() {
    return myTestDirPaths;
  }

  @NotNull
  public Collection<File> getGenTestDirPaths() {
    return myGenTestDirPaths;
  }

  @NotNull
  public Collection<File> getTestResourceDirPaths() {
    return myTestResourceDirPaths;
  }

  @NotNull
  public Collection<File> getExcludeDirPaths() {
    return myExcludeDirPaths;
  }
}
