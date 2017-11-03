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
package com.android.tools.idea.gradle.project.build.console.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link DefaultGradleConsoleView}
 */
public class DefaultGradleConsoleViewTest extends IdeaTestCase {
  @Mock private ToolWindow myToolWindow;
  @Mock private ContentManager myContentManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
  }

  /**
   * Verify that content is not closable.
   */
  public void testContentNotClosable() throws Exception {
    Project project = getProject();
    DefaultGradleConsoleView defaultConsoleView = null;
    try {
      defaultConsoleView = new DefaultGradleConsoleView(project);
      doReturn(myContentManager).when(myToolWindow).getContentManager();
      doNothing().when(myContentManager).addContent(any());
      defaultConsoleView.createToolWindowContent(myToolWindow);
      Content consoleContent = defaultConsoleView.getConsoleContent();
      assertNotNull(consoleContent);
      assertFalse(consoleContent.isCloseable());
    }
    finally {
      if (defaultConsoleView != null) {
        Disposer.dispose(defaultConsoleView);
      }
    }
  }
}