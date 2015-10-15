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
import com.google.common.base.Charsets;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

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

    // Regression test for http://b.android.com/133655: Ensure that files in top level build dir (non-Android facet modules)
    // are still considered generated!
    // Can't use myFixture.addFileToProject(...,....) because this writes to a different
    // folder than the directory where loadProject above has placed the project
    VirtualFile cacheFile = WriteCommandAction.runWriteCommandAction(getProject(), new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        PsiDirectory dexCache = DirectoryUtil.mkdirs(PsiManager.getInstance(getProject()), getProject().getBasePath()
                                                                                           + "/build/intermediates/dex-cache");
        assertNotNull(dexCache);
        PsiFile cachePsiFile = dexCache.createFile("cache.xml");
        String xml =
          "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<items version=\"2\" >\n" +
          "    <item />\n" +
          "    </item>\n" +
          "\n" +
          "</items>";
        VirtualFile cacheFile = cachePsiFile.getVirtualFile();
        assertNotNull(cacheFile);
        try {
          cacheFile.setBinaryContent(xml.getBytes(Charsets.UTF_8));
        }
        catch (IOException e) {
          fail(e.getMessage());
        }
        return cacheFile;
      }
    });
    assertTrue(filter.isGeneratedSource(cacheFile, getProject()));
  }

  @NotNull
  private VirtualFile findFile(@NotNull String path) {
    File filePath = new File(getProject().getBasePath(), FileUtil.toSystemDependentName(path));
    VirtualFile file = findFileByIoFile(filePath, true);
    assertNotNull("File '" + path + "' not found.", file);
    return file;
  }
}
