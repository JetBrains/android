/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.io;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.intellij.openapi.application.ApplicationManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** File system operations. Mocked out in tests involving file manipulations. */
public class FileOperationProvider {

  public static FileOperationProvider getInstance() {
    return ApplicationManager.getApplication().getService(FileOperationProvider.class);
  }

  public boolean exists(File file) {
    return file.exists();
  }

  public boolean isDirectory(File file) {
    return file.isDirectory();
  }

  public boolean isFile(File file) {
    return file.isFile();
  }

  public long getFileModifiedTime(File file) {
    return file.lastModified();
  }

  public boolean setFileModifiedTime(File file, long time) {
    return file.setLastModified(time);
  }

  public long getFileSize(File file) {
    return file.length();
  }

  @Nullable
  public File[] listFiles(File file) {
    return file.listFiles();
  }

  public void createSymbolicLink(File link, File target) throws IOException {
    Files.createSymbolicLink(link.toPath(), target.toPath());
  }

  public File readSymbolicLink(File link) throws IOException {
    return Files.readSymbolicLink(link.toPath()).toFile();
  }

  public boolean isSymbolicLink(File file) {
    return Files.isSymbolicLink(file.toPath());
  }

  public File getCanonicalFile(File file) throws IOException {
    return file.getCanonicalFile();
  }

  public Path createTempFile(
      Path tempDirectory, String prefix, String suffix, FileAttribute<?>... attributes)
      throws IOException {
    return Files.createTempFile(tempDirectory, prefix, suffix, attributes);
  }

  public File copy(File source, File target, CopyOption... options) throws IOException {
    return Files.copy(source.toPath(), target.toPath(), options).toFile();
  }

  public boolean mkdirs(File file) {
    return file.mkdirs();
  }

  /** Walk through the directory and return all meet requirement files in it. */
  public ImmutableList<Path> listFilesRecursively(
      File dir, int maxDepth, BiPredicate<Path, BasicFileAttributes> matcher) throws IOException {
    try (Stream<Path> stream = Files.find(dir.toPath(), maxDepth, matcher)) {
      return stream.collect(toImmutableList());
    }
  }

  public void deleteRecursively(File file) throws IOException {
    deleteRecursively(file, false);
  }

  /**
   * Deletes the file or directory at the given path recursively, allowing insecure delete according
   * to {@code allowInsecureDelete}.
   *
   * @see RecursiveDeleteOption#ALLOW_INSECURE
   */
  public void deleteRecursively(File file, boolean allowInsecureDelete) throws IOException {
    if (allowInsecureDelete) {
      MoreFiles.deleteRecursively(file.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
    } else {
      MoreFiles.deleteRecursively(file.toPath());
    }
  }

  /**
   * Deletes all files within the directory at the given path recursively, allowing insecure delete
   * according to {@code allowInsecureDelete}. Does not delete the directory itself.
   *
   * @see RecursiveDeleteOption#ALLOW_INSECURE
   */
  public void deleteDirectoryContents(File file, boolean allowInsecureDelete) throws IOException {
    if (allowInsecureDelete) {
      MoreFiles.deleteDirectoryContents(file.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
    } else {
      MoreFiles.deleteDirectoryContents(file.toPath());
    }
  }

  // If the file is too big, this method can blow up RAM as it reads the file contents
  // entirely into memory. Only use this for small files.
  public List<String> readAllLines(File file) throws IOException {
    return Files.readAllLines(file.toPath());
  }
}
