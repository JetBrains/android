/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.ide.errorTreeView.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.content.Content;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

public class MessagesToolWindowFixture extends ToolWindowFixture {
  MessagesToolWindowFixture(@NotNull Project project, @NotNull Robot robot) {
    super(ToolWindowId.MESSAGES_WINDOW, project, robot);
  }

  @NotNull
  public ContentFixture getGradleSyncContent() {
    Content content = getContent("Gradle Sync");
    assertNotNull(content);
    return new ContentFixture(content);
  }

  public static class ContentFixture {
    @NotNull private final Content myContent;

    private ContentFixture(@NotNull Content content) {
      myContent = content;
    }

    @NotNull
    public ContentFixture requireMessage(@NotNull ErrorTreeElementKind kind, @NotNull String... text) {
      NewErrorTreeViewPanel component = (NewErrorTreeViewPanel)myContent.getComponent();
      ErrorViewStructure errorView = component.getErrorViewStructure();

      Object root = errorView.getRootElement();
      if (!findError(errorView, errorView.getChildElements(root), text, kind)) {
        fail(String.format("Failed to find message with text '%1$s' and type %2$s", Arrays.toString(text), kind));
      }
      return this;
    }

    private static boolean findError(@NotNull ErrorViewStructure errorView,
                                     @NotNull ErrorTreeElement[] children,
                                     @NotNull String[] text,
                                     @NotNull ErrorTreeElementKind kind) {
      for (ErrorTreeElement child : children) {
        if (child instanceof GroupingElement && findError(errorView, errorView.getChildElements(child), text, kind)) {
          return true;
        }
        if (kind == child.getKind() && Arrays.equals(text, child.getText())) {
          return true;
        }
      }
      return false;
    }
  }
}
