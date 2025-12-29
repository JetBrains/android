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

import static com.android.testutils.AssumeUtil.assumeNotWindows;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.Facets.createAndAddApkFacet;
import static com.android.tools.idea.testing.TestProjectPaths.APK_SAN_ANGELES;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.workspaceModel.ide.legacyBridge.impl.java.JavaModuleTypeUtils.JAVA_MODULE_ENTITY_TYPE_ID_NAME;
import static org.jetbrains.android.AndroidTestBase.getTestDataPath;
import static org.mockito.Mockito.mock;

import com.android.tools.idea.apk.debugging.DexSourceFiles;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.testing.FileSubject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectRule;
import com.intellij.testFramework.RuleChain;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.ui.EditorNotificationPanel;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SmaliFileNotificationProvider}.
 */
@RunWith(JUnit4.class)
@RunsInEdt
public class SmaliFileNotificationProviderTest {
  private final ProjectRule projectRule = new ProjectRule();

  @Rule
  public RuleChain rule = new RuleChain(projectRule, new EdtRule());

  private final FileEditor fileEditor = mock(FileEditor.class);
  private final SmaliFileNotificationProvider notificationProvider = new SmaliFileNotificationProvider();

  @Test
  public void testCreateNotificationPanelWithSmaliFile() throws Exception {
    // b/440156195 Flaky by 0.2% on windows. Disabling for now.
    assumeNotWindows();

    loadProject();

    File outputFolderPath = DexSourceFiles.getInstance(getProject()).getDefaultSmaliOutputFolderPath();
    File rSmaliFilePath = new File(outputFolderPath, join("com", "example", "SanAngeles", "R.smali"));
    assertAbout(FileSubject.file()).that(rSmaliFilePath).isFile();

    VirtualFile rSmaliFile = findFileByIoFile(rSmaliFilePath, true);
    assertThat(rSmaliFile).isNotNull();

    var panelProvider = notificationProvider.collectNotificationData(getProject(), rSmaliFile);
    assertThat(panelProvider).isNotNull();
    EditorNotificationPanel notificationPanel = panelProvider.apply(fileEditor);
    assertThat(notificationPanel).isNotNull();
  }

  @Test
  public void testCreateNotificationPanelWithNonSmaliFile() throws Exception {
    loadProject();
    var panelProvider =
      notificationProvider.collectNotificationData(getProject(), PlatformTestUtil.getOrCreateProjectBaseDir(getProject()));
    assertThat(panelProvider).isNull();
  }

  private void loadProject() throws Exception {
    Project project = getProject();

    File root = new File(getTestDataPath(), toSystemDependentName(APK_SAN_ANGELES));
    assertThat(root.exists()).named(root.getPath()).isTrue();
    File projectRootPath = getBaseDirPath(project);
    copyDir(root, projectRootPath);

    Module rootModule = createRootModule(projectRootPath);
    addContentEntry(rootModule, projectRootPath);
    createAndAddApkFacet(rootModule);
  }

  @NotNull
  private Module createRootModule(@NotNull File projectRootPath) {
    ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
    Module rootModule = modifiableModel.newModule(projectRootPath.getPath(), JAVA_MODULE_ENTITY_TYPE_ID_NAME);
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
    return rootModule;
  }

  private static void addContentEntry(@NotNull Module rootModule, @NotNull File projectRootPath) {
    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(rootModule).getModifiableModel();
    modifiableModel.addContentEntry(FilePaths.pathToIdeaUrl(projectRootPath));
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
  }

  private Project getProject() {
    return projectRule.getProject();
  }
}
