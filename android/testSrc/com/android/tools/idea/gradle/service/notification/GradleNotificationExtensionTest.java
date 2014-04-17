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
package com.android.tools.idea.gradle.service.notification;

import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationExtension.CustomizationResult;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link GradleNotificationExtension}.
 */
public class GradleNotificationExtensionTest extends TestCase {
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

    CustomizationResult notification = GradleNotificationExtension.createNotification(myProject, errorMsg, myHyperlink1, myHyperlink2);

    verify(myProject);

    String title = notification.getTitle();
    assertNotNull(title);
    assertTrue(title.contains("'" + projectName + "'"));

    assertEquals(errorMsg + "\n<a href=\"1\">Hyperlink 1</a> <a href=\"2\">Hyperlink 2</a>", notification.getMessage());

    CustomNotificationListener notificationListener = (CustomNotificationListener)notification.getListener();
    assertNotNull(notificationListener);
    NotificationHyperlink[] hyperlinks = notificationListener.getHyperlinks();
    assertEquals(2, hyperlinks.length);
    assertSame(myHyperlink1, hyperlinks[0]);
    assertSame(myHyperlink2, hyperlinks[1]);
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
