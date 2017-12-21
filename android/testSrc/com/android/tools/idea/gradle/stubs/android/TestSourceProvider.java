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

import com.android.ide.common.gradle.model.stubs.SourceProviderStub;
import com.android.tools.idea.gradle.stubs.FileStructure;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class TestSourceProvider extends SourceProviderStub {
  @NotNull private final FileStructure myFileStructure;

  /**
   * Creates a new {@code SourceProviderStub}.
   *
   * @param fileStructure the file structure of the Android project this {@code SourceProvider} belongs to.
   */
  TestSourceProvider(@NotNull FileStructure fileStructure) {
    super("test", new File("manifest"));
    myFileStructure = fileStructure;
  }

  public void setManifestFile(@NotNull String manifestFilePath) {
    setManifestFile(myFileStructure.createProjectFile(manifestFilePath));
  }

  /**
   * Adds the given path to the list of 'java' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'java' directory, relative to the root directory of the Android project.
   */
  public void addJavaDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getJavaDirectories().add(directory);
  }

  /**
   * Adds the given path to the list of 'resources' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'resources' directory to add, relative to the root directory of the Android project.
   */
  public void addResourcesDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getResourcesDirectories().add(directory);
  }

  /**
   * Adds the given path to the list of 'aidl' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'aidl' directory to add, relative to the root directory of the Android project.
   */
  public void addAidlDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getAidlDirectories().add(directory);
  }

  /**
   * Adds the given path to the list of 'renderscript' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'renderscript' directory to add, relative to the root directory of the Android project.
   */
  public void addRenderscriptDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getRenderscriptDirectories().add(directory);
  }

  /**
   * Adds the given path to the list of 'Cpp' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'Cpp' directory to add, relative to the root directory of the Android project.
   */
  public void addCppDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getCppDirectories().add(directory);
  }

  /**
   * Adds the given path to the list of 'C' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'C' directory to add, relative to the root directory of the Android project.
   */
  public void addCDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getCDirectories().add(directory);
  }

  /**
   * Adds the given path to the list of 'res' directories. It also creates the directory in the file system.
   *
   * @param path path of the 'res' directory to add, relative to the root directory of the Android project.
   */
  public void addResDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getResDirectories().add(directory);
  }

  /**
   * Adds the given path to the list of 'assets' directories. It also creates the directory in the
   * filesystem.
   *
   * @param path path of the 'assets' directory to add, relative to the root directory of the Android project.
   */
  public void addAssetsDirectory(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getAssetsDirectories().add(directory);
  }
}
