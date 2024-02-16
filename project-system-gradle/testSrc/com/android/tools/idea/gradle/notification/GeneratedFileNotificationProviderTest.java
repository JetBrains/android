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
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.GeneratedSourceFileChangeTracker;
import com.intellij.ide.GeneratedSourceFileChangeTrackerImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.ui.EditorNotificationPanel;
import java.io.IOException;
import org.mockito.Mock;

/**
 * Tests for {@link GeneratedFileNotificationProvider}.
 */
public class GeneratedFileNotificationProviderTest extends HeavyPlatformTestCase {
  @Mock private GeneratedSourceFileChangeTrackerImpl myGeneratedSourceFileChangeTracker;
  @Mock private GradleProjectInfo myProjectInfo;
  @Mock private GradleAndroidModel myAndroidModuleModel;
  @Mock private IdeAndroidProject myAndroidProject;
  @Mock private FileEditor myFileEditor;

  private GeneratedFileNotificationProvider myNotificationProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    new IdeComponents(getProject()).replaceProjectService(GeneratedSourceFileChangeTracker.class, myGeneratedSourceFileChangeTracker);
    new IdeComponents(getProject()).replaceProjectService(GradleProjectInfo.class, myProjectInfo);

    when(myAndroidModuleModel.getAndroidProject()).thenReturn(myAndroidProject);

    myNotificationProvider = new GeneratedFileNotificationProvider();
  }

  public void testCreateNotificationPanelWithFileInBuildFolder() throws IOException {
    VirtualFile buildFolder = createFolderInProjectRoot(getProject(), "build");
    VirtualFile file = createFile(buildFolder, "test.txt");
    EditorNotificationPanel panel =
      myNotificationProvider.createNotificationPanel(file, myFileEditor, virtualToIoFile(buildFolder), myGeneratedSourceFileChangeTracker);
    assertEquals("Files under the \"build\" folder are generated and should not be edited.", panel.getText());
  }

  public void testNotificationCanBeDisabledWithKey() throws Exception {
    VirtualFile buildFolder = createFolderInProjectRoot(getProject(), "build");
    VirtualFile file = createFile(buildFolder, "test.txt");

    when(myFileEditor.getUserData(DISABLE_GENERATED_FILE_NOTIFICATION_KEY)).thenReturn(Boolean.TRUE);

    EditorNotificationPanel panel =
      myNotificationProvider.createNotificationPanel(file, myFileEditor, virtualToIoFile(buildFolder), myGeneratedSourceFileChangeTracker);
    assertNull(panel);
  }
}
