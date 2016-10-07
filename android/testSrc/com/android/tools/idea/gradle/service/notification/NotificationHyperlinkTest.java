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

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink}.
 */
public class NotificationHyperlinkTest extends TestCase {
  private boolean myExecuted;
  private Project myProject;
  private NotificationHyperlink myHyperlink;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject = createMock(Project.class);
    myHyperlink = new NotificationHyperlink("openFile", "Open File") {
      @Override
      protected void execute(@NotNull Project project) {
        myExecuted = true;
      }
    };
  }

  public void testExecuteIfClickedWhenDescriptionMatchesUrl() {
    HyperlinkEvent event = createMock(HyperlinkEvent.class);
    expect(event.getDescription()).andReturn("openFile");
    replay(event);

    assertTrue(myHyperlink.executeIfClicked(myProject, event));

    verify(event);

    assertTrue(myExecuted);
  }

  public void testExecuteIfClickedWhenDescriptionDoesNotMatchUrl() {
    HyperlinkEvent event = createMock(HyperlinkEvent.class);
    expect(event.getDescription()).andReturn("browse");
    replay(event);

    assertFalse(myHyperlink.executeIfClicked(myProject, event));

    verify(event);

    assertFalse(myExecuted);
  }

  public void testToString() {
    assertEquals("<a href=\"openFile\">Open File</a>", myHyperlink.toHtml());
  }
}
