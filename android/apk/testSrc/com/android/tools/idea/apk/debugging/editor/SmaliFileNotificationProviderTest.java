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

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.Facets.createAndAddApkFacet;
import static com.android.tools.idea.testing.TestProjectPaths.APK_SAN_ANGELES;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.jetbrains.android.AndroidTestBase.getTestDataPath;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.apk.debugging.DexSourceFiles;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.testing.FileSubject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.EditorNotificationPanel;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link SmaliFileNotificationProvider}.
 */
public class SmaliFileNotificationProviderTest extends HeavyPlatformTestCase {
  @Mock private FileEditor myFileEditor;
  private SmaliFileNotificationProvider myNotificationProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myNotificationProvider = new SmaliFileNotificationProvider();
  }

  @Override
  protected void tearDown() throws Exception {
    myNotificationProvider = null;
    super.tearDown();
  }

  public void testCreateNotificationPanelWithSmaliFile() throws Exception {
    loadProject(APK_SAN_ANGELES);

    File outputFolderPath = DexSourceFiles.getInstance(getProject()).getDefaultSmaliOutputFolderPath();
    File rSmaliFilePath = new File(outputFolderPath, join("com", "example", "SanAngeles", "R.smali"));
    assertAbout(FileSubject.file()).that(rSmaliFilePath).isFile();

    VirtualFile rSmaliFile = findFileByIoFile(rSmaliFilePath, true);
    assertNotNull(rSmaliFile);

    var panelProvider = myNotificationProvider.collectNotificationData(myProject, rSmaliFile);
    assertNotNull(panelProvider);
    EditorNotificationPanel notificationPanel = panelProvider.apply(myFileEditor);
    assertNotNull(notificationPanel);
  }

  public void testCreateNotificationPanelWithNonSmaliFile() throws Exception {
    loadProject(APK_SAN_ANGELES);
    var panelProvider = myNotificationProvider.collectNotificationData(myProject, PlatformTestUtil.getOrCreateProjectBaseDir(myProject));
    assertNull(panelProvider);
  }

  private void loadProject(@NotNull String relativePath) throws Exception {
    Project project = getProject();

    File root = new File(getTestDataPath(), toSystemDependentName(relativePath));
    assertTrue(root.getPath(), root.exists());
    File projectRootPath = getBaseDirPath(project);
    copyDir(root, projectRootPath);

    Module rootModule = createRootModule(projectRootPath);
    addContentEntry(rootModule, projectRootPath);
    createAndAddApkFacet(rootModule);
  }

  @NotNull
  private Module createRootModule(@NotNull File projectRootPath) {
    ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
    Module rootModule = modifiableModel.newModule(projectRootPath.getPath(), StdModuleTypes.JAVA.getId());
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
    return rootModule;
  }

  private static void addContentEntry(@NotNull Module rootModule, @NotNull File projectRootPath) {
    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(rootModule).getModifiableModel();
    modifiableModel.addContentEntry(FilePaths.pathToIdeaUrl(projectRootPath));
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
  }
}
