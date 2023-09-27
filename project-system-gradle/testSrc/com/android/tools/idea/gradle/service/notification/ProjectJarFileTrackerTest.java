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

import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject;
import com.android.tools.idea.gradle.service.notification.ProjectJarFileTracker.JarFileNotificationProvider;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

@RunsInEdt
public class ProjectJarFileTrackerTest {

  @Rule
  public IntegrationTestEnvironmentRule projectRule = AndroidProjectRule.withIntegrationTestEnvironment();

  @Mock private FileEditor myFileEditor;
  @Mock private EditorNotifications myEditorNotifications;
  private JarFileNotificationProvider myNotificationProvider;

  private ProjectJarFileTracker myProjectJarFileTracker;

  private PreparedTestProject preparedProject;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    myNotificationProvider = new JarFileNotificationProvider();
  }

  @Test
  public void testAddFile() throws IOException {
    preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    preparedProject.open(it -> it, project -> {
      new IdeComponents(project).replaceProjectService(EditorNotifications.class, myEditorNotifications);
      myProjectJarFileTracker = ProjectJarFileTracker.getInstance(project);
      VirtualFile projectDir = PlatformTestUtil.getOrCreateProjectBaseDir(project);
      VirtualFile jarFile = WriteAction.computeAndWait(() -> projectDir.createChildData(projectDir, "test.jar"));
      assertTrue(myProjectJarFileTracker.getJarFilesChanged());
      EditorNotificationPanel panel = myNotificationProvider.collectNotificationData(project, jarFile).apply(myFileEditor);
      assertEquals(panel.getText(),
                   "Jar files have been added/removed since last project sync. Sync may be necessary for the IDE to work properly.");
    });
  }

  @Test
  public void testAddFileInExcludedDirectory() throws IOException {
    preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    preparedProject.open(it -> it, project -> {
      new IdeComponents(project).replaceProjectService(EditorNotifications.class, myEditorNotifications);
      myProjectJarFileTracker = ProjectJarFileTracker.getInstance(project);
      Module appModule = TestModuleUtil.findAppModule(project);
      var moduleRootModel = (ModuleRootModel)ModuleRootManager.getInstance(appModule);
      var moduleDir = moduleRootModel.getContentRoots()[0];
      VirtualFile jarFile = WriteAction.computeAndWait(() -> moduleDir.createChildDirectory(this, "build")
        .createChildData(moduleDir, "test.jar"));
      assertFalse(myProjectJarFileTracker.getJarFilesChanged());
      assertNull(myNotificationProvider.collectNotificationData(project, jarFile));
    });
  }
}
