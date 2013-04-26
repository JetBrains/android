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
package com.android.tools.idea.gradle.stubs.android;

import com.android.build.gradle.model.Variant;
import com.android.builder.model.ProductFlavor;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class VariantStub implements Variant {
  @NotNull private final List<File> myGeneratedResourceFolders = Lists.newArrayList();
  @NotNull private final List<File> myGeneratedSourceFolders = Lists.newArrayList();
  @NotNull private final List<File> myGeneratedTestResourceFolders = Lists.newArrayList();
  @NotNull private final List<File> myGeneratedTestSourceFolders = Lists.newArrayList();

  @NotNull private final List<String> myProductFlavors = Lists.newArrayList();

  @NotNull private final String myName;
  @NotNull private final FileStructure fileStructure;

  /**
   * Creates a new {@link VariantStub}.
   *
   * @param name          the name of the variant.
   * @param fileStructure the file structure of the Gradle project this variant belongs to.
   */
  VariantStub(@NotNull String name, @NotNull FileStructure fileStructure) {
    this.myName = name;
    this.fileStructure = fileStructure;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public List<String> getBootClasspath() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public File getOutputFile() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSigned() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public File getOutputTestFile() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getAssembleTaskName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getAssembleTestTaskName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getBuildType() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<String> getProductFlavors() {
    return myProductFlavors;
  }

  @NotNull
  @Override
  public ProductFlavor getMergedFlavor() {
    throw new UnsupportedOperationException();
  }

  /**
   * Adds the given path to the list of generated source directories. It also creates the directory in the file system.
   *
   * @param path path of the generated source directory to add, relative to the root directory of the Android project.
   */
  public void addGeneratedSourceFolder(@NotNull String path) {
    File directory = fileStructure.createProjectDir(path);
    myGeneratedSourceFolders.add(directory);
  }

  @NotNull
  @Override
  public List<File> getGeneratedSourceFolders() {
    return myGeneratedSourceFolders;
  }

  /**
   * Adds the given path to the list of generated resource directories. It also creates the directory in the file system.
   *
   * @param path path of the generated resource directory to add, relative to the root directory of the Android project.
   */
  public void addGeneratedResourceFolder(@NotNull String path) {
    File directory = fileStructure.createProjectDir(path);
    myGeneratedResourceFolders.add(directory);
  }

  @NotNull
  @Override
  public List<File> getGeneratedResourceFolders() {
    return myGeneratedResourceFolders;
  }

  /**
   * Adds the given path to the list of generated test source directories. It also creates the directory in the file system.
   *
   * @param path path of the generated test source directory to add, relative to the root directory of the Android project.
   */
  public void addGeneratedTestSourceFolder(@NotNull String path) {
    File directory = fileStructure.createProjectDir(path);
    myGeneratedTestSourceFolders.add(directory);
  }

  @NotNull
  @Override
  public List<File> getGeneratedTestSourceFolders() {
    return myGeneratedTestSourceFolders;
  }

  /**
   * Adds the given path to the list of generated test resource directories. It also creates the directory in the file system.
   *
   * @param path path of the generated test resource directory to add, relative to the root directory of the Android project.
   */
  public void addGeneratedTestResourceFolder(@NotNull String path) {
    File directory = fileStructure.createProjectDir(path);
    myGeneratedTestResourceFolders.add(directory);
  }

  @NotNull
  @Override
  public List<File> getGeneratedTestResourceFolders() {
    return myGeneratedTestResourceFolders;
  }

  public void addProductFlavors(@NotNull String... flavorNames) {
    myProductFlavors.addAll(Arrays.asList(flavorNames));
  }
}
