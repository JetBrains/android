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

import com.android.builder.model.v2.CustomSourceDirectory;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Sets;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class SourceProviderStub implements SourceProvider {
  @NotNull private final Set<File> myAidlDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myAssetsDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myJavaDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myKotlinDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myCppDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myCDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myRenderscriptDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myBaselineProfileDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myResDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myResourcesDirectories = Sets.newHashSet();

  @NotNull String myName = "test";
  @NotNull File myManifestFile;

  @NotNull private final FileStructure myFileStructure;

  @NotNull
  public FileStructure getFileStructure() {
    return myFileStructure;
  }

  /**
   * Creates a new {@code SourceProviderStub}.
   *
   * @param fileStructure the file structure of the Android project this {@code SourceProvider} belongs to.
   */
  public SourceProviderStub(@NotNull FileStructure fileStructure, @NotNull String manifestFilePath) {
    myFileStructure = fileStructure;
    myManifestFile = myFileStructure.createProjectFile(manifestFilePath);
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public File getManifestFile() {
    if (myManifestFile != null) {
      return myManifestFile;
    }
    return new File("fake");
  }

  /**
   * Adds the given path to the list of 'java' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'java' directory, relative to the root directory of the Android project.
   */
  public void addJavaDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myJavaDirectories.add(directory);
  }

  @Override
  @NotNull
  public Set<File> getJavaDirectories() {
    return myJavaDirectories;
  }

  @NotNull
  @Override
  public Collection<File> getKotlinDirectories() {
    return myKotlinDirectories;
  }

  /**
   * Adds the given path to the list of 'resources' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'resources' directory to add, relative to the root directory of the Android project.
   */
  public void addResourcesDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myResourcesDirectories.add(directory);
  }

  @Override
  @NotNull
  public Set<File> getResourcesDirectories() {
    return myResourcesDirectories;
  }

  /**
   * Adds the given path to the list of 'aidl' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'aidl' directory to add, relative to the root directory of the Android project.
   */
  public void addAidlDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myAidlDirectories.add(directory);
  }

  @Override
  @NotNull
  public Set<File> getAidlDirectories() {
    return myAidlDirectories;
  }
  /**
   * Adds the given path to the list of 'renderscript' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'renderscript' directory to add, relative to the root directory of the Android project.
   */
  public void addRenderscriptDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myRenderscriptDirectories.add(directory);
  }

  @Override
  @NotNull
  public Set<File> getRenderscriptDirectories() {
    return myRenderscriptDirectories;
  }

  /**
   * Adds the given path to the list of 'baselineProfiles' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'baselineProfiles' directory to add, relative to the root directory of the Android project.
   */
  public void addBaselineProfileDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myBaselineProfileDirectories.add(directory);
  }

  /**
   * Adds the given path to the list of 'Cpp' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'Cpp' directory to add, relative to the root directory of the Android project.
   */
  public void addCppDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myCppDirectories.add(directory);
  }

  @Override
  @NotNull
  public Set<File> getCppDirectories() {
    return myCppDirectories;
  }

  /**
   * Adds the given path to the list of 'C' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'C' directory to add, relative to the root directory of the Android project.
   */
  public void addCDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myCDirectories.add(directory);
  }

  @Override
  @NotNull
  public Set<File> getCDirectories() {
    return myCDirectories;
  }

  /**
   * Adds the given path to the list of 'res' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'res' directory to add, relative to the root directory of the Android project.
   */
  public void addResDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myResDirectories.add(directory);
  }

  @Override
  @NotNull
  public Set<File> getResDirectories() {
    return myResDirectories;
  }

  /**
   * Adds the given path to the list of 'assets' directories. It also creates the directory in the
   * filesystem.
   *
   * @param path path of the 'assets' directory to add, relative to the root directory of the Android project.
   */
  public void addAssetsDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myAssetsDirectories.add(directory);
  }

  @Override
  @NotNull
  public Set<File> getAssetsDirectories() {
    return myAssetsDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getJniLibsDirectories() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<File> getShadersDirectories() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<File> getMlModelsDirectories() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<CustomSourceDirectory> getCustomDirectories() {
    return Collections.emptyList();
  }
}
