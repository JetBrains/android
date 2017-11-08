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

import com.android.tools.idea.gradle.notification.ProjectSyncStatusNotificationProvider.IndexingSensitiveNotificationPanel;
import com.android.tools.idea.gradle.notification.ProjectSyncStatusNotificationProvider.NotificationPanel.Type;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.mock.MockDumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.intellij.util.ThreeState.YES;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProjectSyncStatusNotificationProvider}.
 */
public class ProjectSyncStatusNotificationProviderTest extends IdeaTestCase {
  @Mock private GradleProjectInfo myProjectInfo;
  @Mock private GradleSyncState mySyncState;
  @Mock private GradleSyncSummary mySyncSummary;

  private ProjectSyncStatusNotificationProvider myNotificationProvider;
  private IdeComponents myIdeComponents;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    initMocks(this);

    when(mySyncState.getSummary()).thenReturn(mySyncSummary);
    when(myProjectInfo.isBuildWithGradle()).thenReturn(true);
    when(mySyncState.areSyncNotificationsEnabled()).thenReturn(true);

    myNotificationProvider = new ProjectSyncStatusNotificationProvider(getProject(), myProjectInfo, mySyncState);
    myIdeComponents = new IdeComponents(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testNotificationPanelTypeWithProjectNotBuiltWithGradle() {
    when(myProjectInfo.isBuildWithGradle()).thenReturn(false);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.NONE, type);
  }

  public void testNotificationPanelTypeWithSyncNotificationsDisabled() {
    when(mySyncState.areSyncNotificationsEnabled()).thenReturn(false);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.NONE, type);
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
    assertInstanceOf(type.create(myProject), IndexingSensitiveNotificationPanel.class);
  }

  public void testNotificationPanelTypeWithSyncErrors() {
    when(mySyncSummary.hasSyncErrors()).thenReturn(true);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.ERRORS, type);
    assertInstanceOf(type.create(myProject), IndexingSensitiveNotificationPanel.class);
  }

  public void testNotificationPanelTypeWithSyncNeeded() {
    when(mySyncState.isSyncNeeded()).thenReturn(YES);

    Type type = myNotificationProvider.notificationPanelType();
    assertEquals(Type.SYNC_NEEDED, type);
    assertInstanceOf(type.create(myProject), IndexingSensitiveNotificationPanel.class);
  }

  public void testIndexingSensitiveNotificationPanel() {
    OurMockDumbService dumbService = new OurMockDumbService(myProject);

    dumbService.setDumb(true);
    IndexingSensitiveNotificationPanel initiallyInvisibleNotificationPanel =
      new IndexingSensitiveNotificationPanel(myProject, Type.SYNC_NEEDED, "Test", dumbService);
    assertFalse(initiallyInvisibleNotificationPanel.isVisible());

    dumbService.setDumb(false);
    IndexingSensitiveNotificationPanel notificationPanel =
      new IndexingSensitiveNotificationPanel(myProject, Type.SYNC_NEEDED, "Test", dumbService);
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

    public OurMockDumbService(@NotNull Project project) {
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