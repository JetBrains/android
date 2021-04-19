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

import static com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolverKeys.REFRESH_EXTERNAL_NATIVE_MODELS_KEY;
import static com.intellij.util.ThreeState.YES;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.gradle.notification.ProjectSyncStatusNotificationProvider.NotificationPanel.Type;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import org.mockito.Mock;

/**
 * Tests for {@link ProjectSyncStatusNotificationProvider}.
 */
public class ProjectSyncStatusNotificationProviderTest extends PlatformTestCase {
  @Mock private GradleProjectInfo myProjectInfo;
  @Mock private GradleSyncState mySyncState;
  @Mock private GradleFiles myGradleFiles;

  private ProjectSyncStatusNotificationProvider myNotificationProvider;
  private VirtualFile myFile;
  @SuppressWarnings("FieldCanBeLocal")
  private IdeComponents myIdeComponents;
  @SuppressWarnings("FieldCanBeLocal")
  private PropertiesComponent myPropertiesComponent;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    initMocks(this);
    new IdeComponents(myProject).replaceProjectService(GradleFiles.class, myGradleFiles);

    when(myProjectInfo.isBuildWithGradle()).thenReturn(true);

    myNotificationProvider = new ProjectSyncStatusNotificationProvider(myProjectInfo, mySyncState);
    myFile = VfsUtil.findFileByIoFile(createTempFile("build.gradle", "whatever"), true);

    myPropertiesComponent = new PropertiesComponentMock();
    myIdeComponents = new IdeComponents(myProject);
    myIdeComponents.replaceApplicationService(PropertiesComponent.class, myPropertiesComponent);
  }

  public void testNotificationPanelTypeWithSyncNeededWithNoExternalFileChanged() {
    when(mySyncState.isSyncNeeded()).thenReturn(YES);
    when(myGradleFiles.areExternalBuildFilesModified()).thenReturn(false);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.SYNC_NEEDED, type);
    ProjectSyncStatusNotificationProvider.NotificationPanel panel = createPanel(type);
    assertInstanceOf(panel, ProjectSyncStatusNotificationProvider.StaleGradleModelNotificationPanel.class);
    Boolean refreshExternalNativeModels = myProject.getUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY);
    assertNull(refreshExternalNativeModels);
  }

  public void testNotificationPanelTypeWithSyncNeededWithExternalFileChanged() {
    when(mySyncState.isSyncNeeded()).thenReturn(YES);
    when(myGradleFiles.areExternalBuildFilesModified()).thenReturn(true);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.SYNC_NEEDED, type);
    ProjectSyncStatusNotificationProvider.NotificationPanel panel = createPanel(type);
    assertInstanceOf(panel, ProjectSyncStatusNotificationProvider.StaleGradleModelNotificationPanel.class);
    Boolean refreshExternalNativeModels = myProject.getUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY);
    assertTrue(refreshExternalNativeModels);
  }

  public void testNotificationPanelTypeWithProjectNotBuiltWithGradle() {
    when(myProjectInfo.isBuildWithGradle()).thenReturn(false);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.NONE, type);
    assertNull(createPanel(type));
  }

  public void testNotificationPanelTypeWithSyncNotificationsDisabled() {
    PropertiesComponent.getInstance().setValue("PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP", "0");

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.NONE, type);
    ProjectSyncStatusNotificationProvider.NotificationPanel panel = createPanel(type);
    // Since Project Structure notification isn't really a sync notification, we will show it here if the flag is enabled.
    assertInstanceOf(panel, ProjectSyncStatusNotificationProvider.ProjectStructureNotificationPanel.class);
  }

  public void testNotificationPanelTypeWithSyncInProgress() {
    when(mySyncState.isSyncInProgress()).thenReturn(true);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.IN_PROGRESS, type);
  }

  public void testNotificationPanelTypeWithLastSyncFailed() {
    when(mySyncState.lastSyncFailed()).thenReturn(true);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.FAILED, type);
    assertInstanceOf(createPanel(type), ProjectSyncStatusNotificationProvider.SyncProblemNotificationPanel.class);
  }

  public void testProjectStructureNotificationPanelType() {
    when(mySyncState.lastSyncFailed()).thenReturn(false);
    PropertiesComponent.getInstance().setValue("PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP", "0");

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.NONE, type);

    ProjectSyncStatusNotificationProvider.NotificationPanel panel = createPanel(type);
    assertInstanceOf(panel, ProjectSyncStatusNotificationProvider.ProjectStructureNotificationPanel.class);

    // The reshow timeout should always be too large comparing to the potential time difference between statements below,
    // e.g. dozens of days.
    PropertiesComponent.getInstance().setValue("PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP",
                                               Long.toString(System.currentTimeMillis()));
    type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.NONE, type);
    assertNull(createPanel(type));
  }

  public void testNotificationPanelTypeWithSyncNeeded() {
    when(mySyncState.isSyncNeeded()).thenReturn(YES);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.SYNC_NEEDED, type);
    ProjectSyncStatusNotificationProvider.NotificationPanel panel = createPanel(type);
    assertInstanceOf(panel, ProjectSyncStatusNotificationProvider.StaleGradleModelNotificationPanel.class);
  }

  private ProjectSyncStatusNotificationProvider.NotificationPanel createPanel(Type type) {
    return type.create(myProject, myFile, myProjectInfo);
  }
}
