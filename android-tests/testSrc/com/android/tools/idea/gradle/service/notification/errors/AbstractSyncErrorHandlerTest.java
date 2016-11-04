/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link AbstractSyncErrorHandler}.
 */
public class AbstractSyncErrorHandlerTest extends TestCase {
  private Project myProject;
  private NotificationHyperlink myHyperlink1;
  private NotificationHyperlink myHyperlink2;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProject = createMock(Project.class);
    myHyperlink1 = new TestingHyperlink("1", "Hyperlink 1");
    myHyperlink2 = new TestingHyperlink("2", "Hyperlink 2");
  }

  public void testCreateNotification() {
    String projectName = "project1";
    String errorMsg = "Hello";

    expect(myProject.getName()).andReturn(projectName);
    replay(myProject);

    NotificationData notification = new NotificationData("title", "msg", NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    AbstractSyncErrorHandler.updateNotification(notification, myProject, errorMsg, myHyperlink1, myHyperlink2);

    verify(myProject);

    String title = notification.getTitle();
    assertNotNull(title);
    assertTrue(title.contains("'" + projectName + "'"));

    assertEquals(errorMsg + "\n<a href=\"1\">Hyperlink 1</a><br><a href=\"2\">Hyperlink 2</a>", notification.getMessage());

    NotificationListener notificationListener = notification.getListener();
    assertNotNull(notificationListener);
    List<String> hyperlinks = notification.getRegisteredListenerIds();
    assertEquals(2, hyperlinks.size());
    assertTrue(hyperlinks.contains(myHyperlink1.getUrl()));
    assertTrue(hyperlinks.contains(myHyperlink2.getUrl()));
  }

  private static class TestingHyperlink extends NotificationHyperlink {
    TestingHyperlink(@NotNull String url, @NotNull String text) {
      super(url, text);
    }

    @Override
    protected void execute(@NotNull Project project) {
    }
  }
}
