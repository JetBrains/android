/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.apk.debugging;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

/**
 * Tests for {@link ExternalSourceFolders}.
 */
public class ExternalSourceFoldersTest extends AndroidGradleTestCase {
  private ModifiableRootModel myModuleModel;

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myModuleModel != null) {
        ApplicationManager.getApplication().runWriteAction(myModuleModel::dispose);
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testAddSourceFolders() throws IOException {
    // Just copy the project without syncing with Gradle, we don't want any content entries in the project.
    prepareProjectForImport(SIMPLE_APPLICATION);

    File appModulePath = new File(getBaseDirPath(getProject()), "app");
    Module appModule = createModule(appModulePath, JavaModuleType.getModuleType());

    ModuleRootManager rootManager = ModuleRootManager.getInstance(appModule);
    VirtualFile[] contentRoots = rootManager.getContentRoots();
    // The module should not have content roots.
    assertThat(contentRoots).isEmpty();

    myModuleModel = rootManager.getModifiableModel();
    ExternalSourceFolders externalSourceFolders = new ExternalSourceFolders(myModuleModel);
    VirtualFile javaSourceFolder = findJavaSourceFolder(appModulePath);
    List<VirtualFile> files = externalSourceFolders.addSourceFolders(new VirtualFile[]{javaSourceFolder}, () -> {
      ApplicationManager.getApplication().runWriteAction(myModuleModel::commit);
      myModuleModel = null;
    });

    assertThat(files).containsExactly(javaSourceFolder);

    ContentEntry[] contentEntries = rootManager.getContentEntries();
    // Content entry should have been added.
    assertThat(contentEntries).hasLength(1);

    ContentEntry contentEntry = contentEntries[0];
    VirtualFile[] sourceFolderFiles = contentEntry.getSourceFolderFiles();
    assertThat(sourceFolderFiles).hasLength(1);

    VirtualFile sourceFolderFile = sourceFolderFiles[0];
    assertEquals(javaSourceFolder.getPath(), sourceFolderFile.getPath());
  }

  @NotNull
  private static VirtualFile findJavaSourceFolder(@NotNull File appModulePath) {
    File javaSourceFolderPath = new File(appModulePath, join("src", "main", "java"));
    VirtualFile javaSourceFolder = findFileByIoFile(javaSourceFolderPath, true);
    assert javaSourceFolder != null;
    return javaSourceFolder;
  }
}