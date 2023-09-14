/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification;

import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.service.notification.ProjectJarFileTracker;
import com.android.tools.idea.gradle.service.notification.ProjectJarFileTracker.JarFileNotificationProvider;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import java.io.IOException;
import org.junit.Test;
import org.mockito.Mock;

public class ProjectJarFileTrackerTest extends LightPlatformTestCase {
  @Mock private FileEditor myFileEditor;
  @Mock private EditorNotifications myEditorNotifications;
  private JarFileNotificationProvider myNotificationProvider;

  private ProjectJarFileTracker myProjectJarFileTracker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    new IdeComponents(getProject(), getTestRootDisposable()).replaceProjectService(EditorNotifications.class, myEditorNotifications);

    myProjectJarFileTracker = ProjectJarFileTracker.getInstance(getProject());
    myNotificationProvider = new JarFileNotificationProvider();
  }

  @Test
  public void testAddFile() throws IOException {
    VirtualFile projectDir = PlatformTestUtil.getOrCreateProjectBaseDir(getProject());
    VirtualFile jarFile =  WriteAction.computeAndWait(() -> projectDir.createChildData(projectDir, "test.jar"));
    assertTrue(myProjectJarFileTracker.getJarFilesChanged());
    EditorNotificationPanel panel = myNotificationProvider.collectNotificationData(getProject(), jarFile).apply(myFileEditor);
    assertEquals(panel.getText(), "Jar files have been added/removed since last project sync. Sync may be necessary for the IDE to work properly.");
  }



}