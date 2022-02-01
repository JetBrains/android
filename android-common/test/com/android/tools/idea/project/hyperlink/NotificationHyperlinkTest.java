/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.project.hyperlink;

import com.intellij.openapi.project.Project;
import javax.swing.event.HyperlinkEvent;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

/**
 * Tests for {@link NotificationHyperlink}.
 */
public class NotificationHyperlinkTest extends TestCase {
  private boolean myExecuted;
  private Project myProject;
  private NotificationHyperlink myHyperlink;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject = Mockito.mock(Project.class);
    myHyperlink = new NotificationHyperlink("openFile", "Open File") {
      @Override
      protected void execute(@NotNull Project project) {
        myExecuted = true;
      }
    };
  }

  public void testExecuteIfClickedWhenDescriptionMatchesUrl() {
    HyperlinkEvent event = Mockito.mock(HyperlinkEvent.class);
    Mockito.when(event.getDescription()).thenReturn("openFile");

    assertTrue(myHyperlink.executeIfClicked(myProject, event));
    assertTrue(myExecuted);
  }

  public void testExecuteIfClickedWhenDescriptionDoesNotMatchUrl() {
    HyperlinkEvent event = Mockito.mock(HyperlinkEvent.class);
    Mockito.when(event.getDescription()).thenReturn("browse");

    assertFalse(myHyperlink.executeIfClicked(myProject, event));
    assertFalse(myExecuted);
  }

  public void testToString() {
    assertEquals("<a href=\"openFile\">Open File</a>", myHyperlink.toHtml());
  }
}
