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
package com.android.tools.idea.gradle.stubs.gradle;

import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class IdeaContentRootStub implements IdeaContentRoot {
  @NotNull private final List<IdeaSourceDirectory> mySourceDirs = new ArrayList<>();
  @NotNull private final List<IdeaSourceDirectory> myTestDirs = new ArrayList<>();
  @NotNull private final Set<File> myExcludedDirs = Sets.newHashSet();

  @NotNull private final FileStructure myFileStructure;

  IdeaContentRootStub(@NotNull File rootDir) {
    myFileStructure = new FileStructure(rootDir);
    addSourceDir("src/main/java");
    addTestDir("src/test/java");
    addExcludedDir("/classes");
  }

  /**
   * Adds the given path to the list of 'source' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'source' directory, relative to the root directory of the content root.
   */
  @NotNull
  public IdeaSourceDirectoryStub addSourceDir(@NotNull String path) {
    File dir = myFileStructure.createProjectDir(path);
    IdeaSourceDirectoryStub sourceDir = new IdeaSourceDirectoryStub(dir);
    mySourceDirs.add(sourceDir);
    return sourceDir;
  }

  /**
   * Adds the given path to the list of 'test' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'test' directory, relative to the root directory of the content root.
   */
  @NotNull
  public IdeaSourceDirectoryStub addTestDir(@NotNull String path) {
    File dir = myFileStructure.createProjectDir(path);
    IdeaSourceDirectoryStub testDir = new IdeaSourceDirectoryStub(dir);
    myTestDirs.add(testDir);
    return testDir;
  }

  /**
   * Adds the given path to the list of 'excluded' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'excluded' directory, relative to the root directory of the content root.
   */
  @NotNull
  public File addExcludedDir(@NotNull String path) {
    File dir = myFileStructure.createProjectDir(path);
    myExcludedDirs.add(dir);
    return dir;
  }

  @NotNull
  @Override
  public File getRootDirectory() {
    return myFileStructure.getRootFolderPath();
  }

  @Override
  public DomainObjectSet<? extends IdeaSourceDirectory> getSourceDirectories() {
    return ImmutableDomainObjectSet.of(mySourceDirs);
  }

  @Override
  public DomainObjectSet<? extends IdeaSourceDirectory> getTestDirectories() {
    return ImmutableDomainObjectSet.of(myTestDirs);
  }

  @Override
  public DomainObjectSet<? extends IdeaSourceDirectory> getResourceDirectories() {
    return ImmutableDomainObjectSet.of(Collections.emptyList());
  }

  @Override
  public DomainObjectSet<? extends IdeaSourceDirectory> getTestResourceDirectories() {
    return ImmutableDomainObjectSet.of(Collections.emptyList());
  }

  @Override
  public Set<File> getExcludeDirectories() {
    return myExcludedDirs;
  }
}
