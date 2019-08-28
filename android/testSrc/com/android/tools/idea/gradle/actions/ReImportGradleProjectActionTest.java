/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ReImportGradleProjectAction}.
 */
public class ReImportGradleProjectActionTest extends JavaProjectTestCase {
  @Mock private GradleProjectImporter myImporter;
  @Mock GradleSyncState mySyncState;
  @Mock private AnActionEvent myEvent;

  private ReImportGradleProjectAction myAction;
  private Presentation myPresentation;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myPresentation = new Presentation();
    when(myEvent.getPresentation()).thenReturn(myPresentation);

    myAction = new ReImportGradleProjectAction("Tests message", myImporter);
  }

  /**
   * Check action is not enabled while syncing.
   */
  public void testDoUpdateWithSyncInProgress() {
    Project project = getProject();
    ServiceContainerUtil
      .replaceService(project, GradleSyncState.class, mySyncState, getTestRootDisposable());
    when(mySyncState.isSyncInProgress()).thenReturn(true);

    myAction.doUpdate(myEvent, project);

    assertFalse(myPresentation.isEnabled());
  }

  /**
   * Check project is re imported.
   */
  public void testDoPerform() throws IOException {
    Project project = getProject();
    File projectPath = new File(project.getBasePath());
    String projectName = project.getName();
    myAction.doPerform(myEvent, project);

    verify(myImporter).importProject(eq(projectName), eq(projectPath), any());
    // Confirm project is disposed since mocked importer does nothing
    assertTrue("Project should be disposed after performing action", project.isDisposed());
    // Remove project since it was already disposed.
    this.myProject = null;
  }
}