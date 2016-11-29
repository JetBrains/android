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
package com.android.tools.idea.gradle.stubs;

import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Structure, in the file system, of an Android-Gradle project.
 */
public class FileStructure {
  @NotNull private final File myRootFolderPath;

  /**
   * Creates a new {@link FileStructure}. The directory structure is created in a temporary folder.
   *
   * @param rootFolderName name of the root directory.
   */
  public FileStructure(@NotNull String rootFolderName) {
    this(Files.createTempDir(), rootFolderName);
  }

  /**
   * Creates a new {@link FileStructure}.
   *
   * @param parentFolderPath parent directory.
   * @param rootDirName      name of the root directory.
   */
  public FileStructure(@NotNull File parentFolderPath, @NotNull String rootDirName) {
    this(createDirectory(parentFolderPath, rootDirName));
  }

  /**
   * Creates a new {@link FileStructure}.
   *
   * @param rootFolderPath the root directory.
   */
  public FileStructure(@NotNull File rootFolderPath) {
    myRootFolderPath = rootFolderPath;
    setUpStructure();
  }

  private void setUpStructure() {
    createProjectDir("build/apk");
    createProjectDir("build/assets");
    createProjectDir("build/classes");
    createProjectDir("build/dependency-cache");
    createProjectDir("build/incremental");
    createProjectDir("build/libs");
    createProjectDir("build/manifests");
    createProjectDir("build/res");
    createProjectDir("build/source");
    createProjectDir("build/symbols");
    createProjectDir("src/main/assets");
    createProjectDir("src/main/java");
    createProjectDir("src/main/res");
    createProjectDir("src/instrumentTest/java");
  }

  /**
   * Creates a directory using the given path.
   *
   * @param path the path of the directory to create. It is relative to {@link #getRootFolderPath()}.
   * @return the created directory.
   */
  @NotNull
  public File createProjectDir(@NotNull String path) {
    File dir = createDirectory(myRootFolderPath, path);
    String msg = String.format("Directory '%s' should exist", path);
    Assert.assertTrue(msg, dir.isDirectory());
    return dir;
  }

  @NotNull
  private static File createDirectory(@NotNull File parent, @NotNull String name) {
    File dir = new File(parent, name);
    FileUtil.createDirectory(dir);
    return dir;
  }

  /**
   * Creates a file using the given path.
   *
   * @param path the path of the file to create. It is relative to {@link #getRootFolderPath()}.
   * @return the created file.
   */
  @NotNull
  public File createProjectFile(@NotNull String path) {
    File file = new File(myRootFolderPath, path);
    FileUtil.createIfDoesntExist(file);
    return file;
  }

  /**
   * @return the root directory.
   */
  @NotNull
  public File getRootFolderPath() {
    return myRootFolderPath;
  }

  /**
   * Deletes all the directories in this file structure.
   */
  public void dispose() {
    FileUtil.delete(myRootFolderPath);
  }
}
