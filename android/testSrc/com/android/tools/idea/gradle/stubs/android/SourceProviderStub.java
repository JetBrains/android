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

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

public class SourceProviderStub implements SourceProvider {
  @NotNull private final Set<File> myAidlDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myAssetsDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myJavaDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myJniDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myRenderscriptDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myResDirectories = Sets.newHashSet();
  @NotNull private final Set<File> myResourcesDirectories = Sets.newHashSet();

  @Nullable File myManifestFile;

  @NotNull private final FileStructure myFileStructure;

  /**
   * Creates a new {@code SourceProviderStub}.
   *
   * @param fileStructure the file structure of the Android project this {@code SourceProvider} belongs to.
   */
  SourceProviderStub(@NotNull FileStructure fileStructure) {
    myFileStructure = fileStructure;
  }

  public void setManifestFile(@NotNull String manifestFilePath) {
    myManifestFile = myFileStructure.createProjectFile(manifestFilePath);
  }

  @NotNull
  @Override
  public File getManifestFile() {
    if (myManifestFile != null) {
      return myManifestFile;
    }
    throw new UnsupportedOperationException();
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

  @NotNull
  @Override
  public Set<File> getJavaDirectories() {
    return myJavaDirectories;
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

  @NotNull
  @Override
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

  @NotNull
  @Override
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

  @NotNull
  @Override
  public Set<File> getRenderscriptDirectories() {
    return myRenderscriptDirectories;
  }

  /**
   * Adds the given path to the list of 'jni' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'jni' directory to add, relative to the root directory of the Android project.
   */
  public void addJniDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myJniDirectories.add(directory);
  }

  @NotNull
  @Override
  public Set<File> getJniDirectories() {
    return myJniDirectories;
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

  @NotNull
  @Override
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

  @NotNull
  @Override
  public Set<File> getAssetsDirectories() {
    return myAssetsDirectories;
  }
}
