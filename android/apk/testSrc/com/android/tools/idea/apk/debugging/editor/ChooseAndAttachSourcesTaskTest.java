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

import static com.android.tools.idea.testing.Facets.createAndAddApkFacet;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.apk.debugging.DexSourceFiles;
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.android.tools.idea.testing.ProjectFiles;
import com.android.tools.idea.util.FileOrFolderChooser;
import com.intellij.mock.MockDumbService;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ProjectRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.Set;

import static com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject.SIMPLE_APPLICATION;
import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.android.tools.idea.testing.Facets.createAndAddApkFacet;
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
@RunsInEdt
public class ChooseAndAttachSourcesTaskTest {
  @Mock private EditorNotifications myEditorNotifications;
  @Mock private DexSourceFiles myDexSourceFiles;
  @Mock private FileOrFolderChooser myFileOrFolderChooser;

  @Rule
  public ProjectRule projectRule = new ProjectRule();
  @Rule
  public IntegrationTestEnvironmentRule rule = AndroidProjectRule.withIntegrationTestEnvironment();

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  public void testRun() {
    // Just copy the project without syncing with Gradle, we don't want any content entries in the project.
    PreparedTestProject p = prepareTestProject(rule, SIMPLE_APPLICATION);

    File appModulePath = new File(p.getRoot(), "app");
    Module appModule = ProjectFiles.createModule(projectRule.getProject(), appModulePath, JavaModuleType.getModuleType());
    ApkFacet apkFacet = createAndAddApkFacet(appModule);

    VirtualFile javaSourceFolder = findJavaSourceFolder(appModulePath);
    when(myFileOrFolderChooser.choose(projectRule.getProject())).thenReturn(new VirtualFile[]{javaSourceFolder});

    String classFqn = "a.b.c";
    ChooseAndAttachJavaSourcesTask task = new ChooseAndAttachJavaSourcesTask(classFqn, appModule, new MockDumbService(projectRule.getProject()),
                                                                             myEditorNotifications, myDexSourceFiles, myFileOrFolderChooser);
    task.run();

    ContentEntry[] contentEntries = ModuleRootManager.getInstance(appModule).getContentEntries();
    // Content entry should have been added.
    assertThat(contentEntries).hasLength(1);

    verify(myDexSourceFiles).navigateToJavaFile(classFqn);
    verify(myEditorNotifications).updateAllNotifications();

    Set<String> javaSourceFolderPaths = apkFacet.getConfiguration().JAVA_SOURCE_FOLDER_PATHS;
    assertThat(javaSourceFolderPaths).hasSize(1);
    assertThat(getFirstItem(javaSourceFolderPaths)).isEqualTo(virtualToIoFile(javaSourceFolder).getPath());
  }

  @NotNull
  private static VirtualFile findJavaSourceFolder(@NotNull File appModulePath) {
    File javaSourceFolderPath = new File(appModulePath, join("src", "main", "java"));
    VirtualFile javaSourceFolder = findFileByIoFile(javaSourceFolderPath, true);
    assert javaSourceFolder != null;
    return javaSourceFolder;
  }
}