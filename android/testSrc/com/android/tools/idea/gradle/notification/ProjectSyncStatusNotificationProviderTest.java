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

import static com.intellij.util.ThreeState.YES;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.gradle.notification.ProjectSyncStatusNotificationProvider.IndexingSensitiveNotificationPanel;
import com.android.tools.idea.gradle.notification.ProjectSyncStatusNotificationProvider.NotificationPanel.Type;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.mock.MockDumbService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link ProjectSyncStatusNotificationProvider}.
 */
public class ProjectSyncStatusNotificationProviderTest extends PlatformTestCase {
  @Mock private GradleProjectInfo myProjectInfo;
  @Mock private GradleSyncState mySyncState;

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

    when(myProjectInfo.isBuildWithGradle()).thenReturn(true);
    when(mySyncState.areSyncNotificationsEnabled()).thenReturn(true);

    myNotificationProvider = new ProjectSyncStatusNotificationProvider(myProjectInfo, mySyncState);
    myFile = VfsUtil.findFileByIoFile(createTempFile("build.gradle", "whatever"), true);

    myPropertiesComponent = new PropertiesComponentMock();
    myIdeComponents = new IdeComponents(myProject);
    myIdeComponents.replaceApplicationService(PropertiesComponent.class, myPropertiesComponent);
  }

  public void testNotificationPanelTypeWithProjectNotBuiltWithGradle() {
    when(myProjectInfo.isBuildWithGradle()).thenReturn(false);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.NONE, type);
    assertNull(createPanel(type));
  }

  public void testNotificationPanelTypeWithSyncNotificationsDisabled() {
    when(mySyncState.areSyncNotificationsEnabled()).thenReturn(false);
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
    assertInstanceOf(createPanel(type), IndexingSensitiveNotificationPanel.class);
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
    assertInstanceOf(panel, IndexingSensitiveNotificationPanel.class);
  }

  private ProjectSyncStatusNotificationProvider.NotificationPanel createPanel(Type type) {
    ProjectSyncStatusNotificationProvider.NotificationPanel panel = type.create(myProject, myFile, myProjectInfo);
    // Disposing logic similar to the ProjectSyncStatusNotificationProvider.createNotificationPanel method.
    if (panel instanceof Disposable) {
      Disposer.register(getTestRootDisposable(), (Disposable)panel);
    }
    return panel;
  }

  public void testIndexingSensitiveNotificationPanel() {
    OurMockDumbService dumbService = new OurMockDumbService(myProject);

    dumbService.setDumb(true);
    IndexingSensitiveNotificationPanel initiallyInvisibleNotificationPanel =
        new IndexingSensitiveNotificationPanel(myProject, Type.SYNC_NEEDED, "Test", dumbService);
    Disposer.register(getTestRootDisposable(), initiallyInvisibleNotificationPanel);
    assertFalse(initiallyInvisibleNotificationPanel.isVisible());

    dumbService.setDumb(false);
    IndexingSensitiveNotificationPanel notificationPanel =
        new IndexingSensitiveNotificationPanel(myProject, Type.SYNC_NEEDED, "Test", dumbService);
    Disposer.register(getTestRootDisposable(), notificationPanel);
    assertTrue(notificationPanel.isVisible());

    dumbService.setDumb(true);
    assertFalse(notificationPanel.isVisible());
    dumbService.setDumb(false);
    assertTrue(notificationPanel.isVisible());

    // Must dispose the message bus connection
    Disposer.dispose(notificationPanel);
    dumbService.setDumb(true);
    assertTrue(notificationPanel.isVisible());
    dumbService.setDumb(false);
    assertTrue(notificationPanel.isVisible());
  }

  private static class OurMockDumbService extends MockDumbService {
    private boolean myDumb;
    private DumbModeListener myPublisher;

    OurMockDumbService(@NotNull Project project) {
      super(project);
      myDumb = false;
      myPublisher = project.getMessageBus().syncPublisher(DUMB_MODE);
    }

    public void setDumb(boolean dumb) {
      if (dumb) {
        myDumb = true;
        myPublisher.enteredDumbMode();
      }
      else {
        myDumb = false;
        myPublisher.exitDumbMode();
      }
    }

    @Override
    public boolean isDumb() {
      return myDumb;
    }
  }
}
