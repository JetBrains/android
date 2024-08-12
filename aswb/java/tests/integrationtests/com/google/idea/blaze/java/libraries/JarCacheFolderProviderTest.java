/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link JarCacheFolderProvider} */
@RunWith(JUnit4.class)
public class JarCacheFolderProviderTest extends BlazeIntegrationTestCase {

  private JarCacheFolderProvider subject;
  private Path expectedJarFolderPath;

  @Before
  public void doSetUp() {
    // Need to use the TestFileSystem for test.
    registerApplicationService(
        FileOperationProvider.class,
        new FileOperationProvider() {
          @Override
          public boolean mkdirs(File file) {
            return fileSystem.createDirectory(file.getPath()).isDirectory();
          }

          @Override
          public boolean exists(File file) {
            return fileSystem.findFile(file.getPath()) != null
                && fileSystem.findFile(file.getPath()).exists();
          }

          @Override
          public boolean isDirectory(File file) {
            return fileSystem.findFile(file.getPath()) != null
                && fileSystem.findFile(file.getPath()).isDirectory();
          }
        });
    subject = new JarCacheFolderProvider(getProject());
    expectedJarFolderPath = Paths.get(projectDataDirectory.getPath(), ".blaze", "libraries");
  }

  @Test
  public void testGetJarCacheFolderReturnsFileWithExpectedPath() {
    assertThat(subject.getJarCacheFolder().getPath()).isEqualTo(expectedJarFolderPath.toString());
  }

  @Test
  public void testIsJarCacheFolderReadyCreatesFolderIfItDoesNotExist() {
    assertThat(subject.isJarCacheFolderReady()).isTrue();
    assertThat(fileSystem.findFile(expectedJarFolderPath.toString()).exists()).isTrue();
  }

  @Test
  public void testIsJarCacheFolderReadyReturnsFalseIfItIsNotFolder() {
    fileSystem.createFile(expectedJarFolderPath.toString());

    assertThat(subject.isJarCacheFolderReady()).isFalse();
  }

  @Test
  public void testIsJarCacheFolderReadyReturnsTrueIfItIsFolder() {
    fileSystem.createDirectory(expectedJarFolderPath.toString());

    assertThat(subject.isJarCacheFolderReady()).isTrue();
  }

  @Test
  public void testGetCacheFileByKeyReturnsFileWithExpectedPath() {
    assertThat(subject.getCacheFileByKey("file-key").getPath())
        .isEqualTo(Paths.get(expectedJarFolderPath.toString(), "file-key").toString());
  }

  @Test
  public void isJarCacheFolderReady_whenSettingsAreNotPresent_isFalse() {
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(null);

    assertThat(subject.isJarCacheFolderReady()).isFalse();
  }
}
