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
package com.android.tools.idea.editors;

import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class AndroidGeneratedSourcesFilterTest extends AndroidGradleTestCase {

  public void test() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("AndroidGeneratedSourcesFilterTest.test temporarily disabled");
      return;
    }

    loadProject("projects/sync/multiproject", true);
    AndroidGeneratedSourcesFilter filter = new AndroidGeneratedSourcesFilter();

    VirtualFile file = findFile("module1/build/generated/source/buildConfig/debug/com/example/test/multiproject/module1/BuildConfig.java");
    assertTrue(filter.isGeneratedSource(file, getProject()));

    file = findFile("module2/build.gradle");
    assertFalse(filter.isGeneratedSource(file, getProject()));
  }

  @NotNull
  private VirtualFile findFile(@NotNull String path) {
    File filePath = new File(getProject().getBasePath(), FileUtil.toSystemDependentName(path));
    VirtualFile file = findFileByIoFile(filePath, true);
    assertNotNull("File '" + path + "' not found.", file);
    return file;
  }
}
