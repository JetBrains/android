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
package com.android.tools.idea.gradle.notification;

import static com.android.tools.idea.FileEditorUtil.DISABLE_GENERATED_FILE_NOTIFICATION_KEY;
import static com.android.tools.idea.testing.ProjectFiles.createFile;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.notification.GeneratedFileNotificationProvider.MyEditorNotificationPanel;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import java.io.IOException;
import org.mockito.Mock;

/**
 * Tests for {@link GeneratedFileNotificationProvider}.
 */
public class GeneratedFileNotificationProviderTest extends PlatformTestCase {
  @Mock private GradleProjectInfo myProjectInfo;
  @Mock private AndroidModuleModel myAndroidModuleModel;
  @Mock private IdeAndroidProject myAndroidProject;
  @Mock private FileEditor myFileEditor;

  private GeneratedFileNotificationProvider myNotificationProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    new IdeComponents(getProject()).replaceProjectService(GradleProjectInfo.class, myProjectInfo);

    when(myAndroidModuleModel.getAndroidProject()).thenReturn(myAndroidProject);

    myNotificationProvider = new GeneratedFileNotificationProvider();
  }

  public void testCreateNotificationPanelWithFileInBuildFolder() throws IOException {
    VirtualFile buildFolder = createFolderInProjectRoot(getProject(), "build");
    VirtualFile file = createFile(buildFolder, "test.txt");

    when(myProjectInfo.findAndroidModelInModule(file, false)).thenReturn(myAndroidModuleModel);
    when(myAndroidProject.getBuildFolder()).thenReturn(virtualToIoFile(buildFolder));

    MyEditorNotificationPanel panel = (MyEditorNotificationPanel)myNotificationProvider.createNotificationPanel(file, myFileEditor, getProject(), myProjectInfo);
    assertEquals("Files under the \"build\" folder are generated and should not be edited.", panel.getText());

    // Ensure that "excluded" files are not ignored.
    verify(myProjectInfo).findAndroidModelInModule(file, false);
  }

  public void testNotificationCanBeDisabledWithKey() throws Exception {
    VirtualFile buildFolder = createFolderInProjectRoot(getProject(), "build");
    VirtualFile file = createFile(buildFolder, "test.txt");

    when(myProjectInfo.findAndroidModelInModule(file, false)).thenReturn(myAndroidModuleModel);
    when(myAndroidProject.getBuildFolder()).thenReturn(virtualToIoFile(buildFolder));
    when(myFileEditor.getUserData(DISABLE_GENERATED_FILE_NOTIFICATION_KEY)).thenReturn(Boolean.TRUE);

    MyEditorNotificationPanel panel = (MyEditorNotificationPanel)myNotificationProvider.createNotificationPanel(file, myFileEditor, getProject());
    assertNull(panel);
  }
}