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
package com.android.tools.idea.gradle.testing;

import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.createIfDoesntExist;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public abstract class TestArtifactsCodeInsightTest extends AndroidGradleTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadProject("projects/testArtifacts/multiproject", false);
  }

  @NotNull
  protected VirtualFile setUnitTestFileContent(@NotNull String filename, @NotNull String content) {
    return setFileContent("module1/src/test/java/" + filename, content);
  }

  @NotNull
  protected VirtualFile setAndroidTestFileContent(@NotNull String filename, @NotNull String content) {
    return setFileContent("module1/src/androidTest/java/" + filename, content);
  }

  @NotNull
  protected VirtualFile setCommonFileContent(@NotNull String filename, @NotNull String content) {
    return setFileContent("module1/src/main/java/" + filename, content);
  }

  /**
   * Set content of specific file and load the file into the in-memory editor.
   *
   * @param path    relative path to the project root
   * @param content desired file content
   * @return virtual file for the specific path
   */
  @NotNull
  protected VirtualFile setFileContent(@NotNull String path, @NotNull String content) {
    File file = new File(myFixture.getProject().getBasePath(), path.replace('/', File.separatorChar));
    createIfDoesntExist(file);
    VirtualFile virtualFile = findFileByIoFile(file, true);
    assertNotNull(virtualFile);
    myFixture.saveText(virtualFile, content);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    return virtualFile;
  }
}
