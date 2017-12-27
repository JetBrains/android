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
package com.android.tools.idea.apk.debugging.editor;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.apk.debugging.DexSourceFiles;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.util.FileOrFolderChooser;
import com.intellij.mock.MockDumbService;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.Facets.createAndAddApkFacet;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ChooseAndAttachJavaSourcesTask}.
 */
public class ChooseAndAttachSourcesTaskTest extends AndroidGradleTestCase {
  @Mock private EditorNotifications myEditorNotifications;
  @Mock private DexSourceFiles myDexSourceFiles;
  @Mock private FileOrFolderChooser myFileOrFolderChooser;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
  }

  public void testRun() throws IOException {
    // Just copy the project without syncing with Gradle, we don't want any content entries in the project.
    prepareProjectForImport(SIMPLE_APPLICATION);

    File appModulePath = new File(getBaseDirPath(getProject()), "app");
    Module appModule = createModule(appModulePath, JavaModuleType.getModuleType());
    ApkFacet apkFacet = createAndAddApkFacet(appModule);

    VirtualFile javaSourceFolder = findJavaSourceFolder(appModulePath);
    when(myFileOrFolderChooser.choose(getProject())).thenReturn(new VirtualFile[]{javaSourceFolder});

    String classFqn = "a.b.c";
    ChooseAndAttachJavaSourcesTask task = new ChooseAndAttachJavaSourcesTask(classFqn, appModule, new MockDumbService(getProject()),
                                                                             myEditorNotifications, myDexSourceFiles, myFileOrFolderChooser);
    task.run();

    ContentEntry[] contentEntries = ModuleRootManager.getInstance(appModule).getContentEntries();
    // Content entry should have been added.
    assertThat(contentEntries).hasLength(1);

    verify(myDexSourceFiles).navigateToJavaFile(classFqn);
    verify(myEditorNotifications).updateAllNotifications();

    Set<String> javaSourceFolderPaths = apkFacet.getConfiguration().JAVA_SOURCE_FOLDER_PATHS;
    assertThat(javaSourceFolderPaths).hasSize(1);
    assertEquals(virtualToIoFile(javaSourceFolder).getPath(), getFirstItem(javaSourceFolderPaths));
  }

  @NotNull
  private static VirtualFile findJavaSourceFolder(@NotNull File appModulePath) {
    File javaSourceFolderPath = new File(appModulePath, join("src", "main", "java"));
    VirtualFile javaSourceFolder = findFileByIoFile(javaSourceFolderPath, true);
    assert javaSourceFolder != null;
    return javaSourceFolder;
  }
}