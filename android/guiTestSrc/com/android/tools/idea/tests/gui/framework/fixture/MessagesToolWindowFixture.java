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
import com.intellij.openapi.externalSystem.service.notification.EditableNotificationMessageElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.content.Content;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.TreeCellEditor;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.swing.event.HyperlinkEvent.EventType.ACTIVATED;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.awt.AWT.visibleCenterOf;
import static org.fest.util.Strings.quote;

public class MessagesToolWindowFixture extends ToolWindowFixture {
  MessagesToolWindowFixture(@NotNull Project project, @NotNull Robot robot) {
    super(ToolWindowId.MESSAGES_WINDOW, project, robot);
  }

  @NotNull
  public ContentFixture getGradleSyncContent() {
    Content content = getContent("Gradle Sync");
    assertNotNull(content);
    return new ContentFixture(myRobot, content);
  }

  public static class ContentFixture {
    @NotNull private final Robot myRobot;
    @NotNull private final Content myContent;

    private ContentFixture(@NotNull Robot robot, @NotNull Content content) {
      myRobot = robot;
      myContent = content;
    }

    @NotNull
    public ContentFixture requireMessage(@NotNull ErrorTreeElementKind kind, @NotNull final String... text) {
      MessageMatcher equalMatcher = new MessageMatcher() {
        @Override
        public boolean matches(@NotNull String[] actual) {
          return Arrays.equals(text, actual);
        }

        @Override
        public String toString() {
          return Arrays.toString(text);
        }
      };
      return requireMessage(kind, equalMatcher);
    }

    @NotNull
    public ContentFixture requireMessage(@NotNull ErrorTreeElementKind kind, @NotNull MessageMatcher matcher) {
      doFindMessage(kind, matcher);
      return this;
    }

    @NotNull
    public MessageFixture findMessage(@NotNull ErrorTreeElementKind kind, @NotNull MessageMatcher matcher) {
      ErrorTreeElement found = doFindMessage(kind, matcher);
      return new MessageFixture(myRobot, found);
    }

    @NotNull
    private ErrorTreeElement doFindMessage(@NotNull final ErrorTreeElementKind kind, @NotNull final MessageMatcher matcher) {
      ErrorTreeElement found = GuiActionRunner.execute(new GuiQuery<ErrorTreeElement>() {
        @Override
        @Nullable
        protected ErrorTreeElement executeInEDT() throws Throwable {
          NewErrorTreeViewPanel component = (NewErrorTreeViewPanel)myContent.getComponent();
          ErrorViewStructure errorView = component.getErrorViewStructure();

          Object root = errorView.getRootElement();
          return findMessage(errorView, errorView.getChildElements(root), matcher, kind);
        }
      });

      if (found == null) {
        fail(String.format("Failed to find message of type %1$s and matching text %2$s", kind, matcher.toString()));
      }
      return found;
    }

    @Nullable
    private static ErrorTreeElement findMessage(@NotNull ErrorViewStructure errorView,
                                                @NotNull ErrorTreeElement[] children,
                                                @NotNull MessageMatcher matcher,
                                                @NotNull ErrorTreeElementKind kind) {
      for (ErrorTreeElement child : children) {
        if (child instanceof GroupingElement) {
          ErrorTreeElement found = findMessage(errorView, errorView.getChildElements(child), matcher, kind);
          if (found != null) {
            return found;
          }
        }
        if (kind == child.getKind() && matcher.matches(child.getText())) {
          return child;
        }
      }
      return null;
    }

  }

  public static abstract class MessageMatcher {
    protected abstract boolean matches(@NotNull String[] text);

    @NotNull
    public static MessageMatcher firstLineStartingWith(@NotNull final String prefix) {
      return new MessageMatcher() {
        @Override
        public boolean matches(@NotNull String[] text) {
          return text[0].startsWith(prefix);
        }

        @Override
        public String toString() {
          return "first line starting with " + quote(prefix);
        }
      };
    }
  }

  public static class MessageFixture {
    private static final Pattern ANCHOR_TAG_PATTERN = Pattern.compile("<a href=\"(.*?)\">([^<]+)</a>");

    @NotNull private final Robot myRobot;
    @NotNull private final ErrorTreeElement myTarget;

    MessageFixture(@NotNull Robot robot, @NotNull ErrorTreeElement target) {
      myRobot = robot;
      myTarget = target;
    }

    @NotNull
    public HyperlinkFixture findHyperlink(@NotNull String hyperlinkText) {
      // There is no specific UI component for a hyperlink in the "Messages" window. Instead we have a JEditorPane with HTML. This method
      // finds the anchor tags, and matches the text of each of them against the given text. If a matching hyperlink is found, we fire a
      // HyperlinkEvent, simulating a click on the actual hyperlink.

      assertThat(myTarget).isInstanceOf(EditableNotificationMessageElement.class);

      // Find the URL of the hyperlink.
      final EditableNotificationMessageElement message = (EditableNotificationMessageElement)myTarget;

      final JEditorPane editorComponent = GuiActionRunner.execute(new GuiQuery<JEditorPane>() {
        @Override
        protected JEditorPane executeInEDT() throws Throwable {
          TreeCellEditor cellEditor = message.getRightSelfEditor();
          return field("editorComponent").ofType(JEditorPane.class).in(cellEditor).get();
        }
      });

      String text = GuiActionRunner.execute(new GuiQuery<String>() {
        @Override
        protected String executeInEDT() throws Throwable {
          return editorComponent.getText();
        }
      });

      String url = null;
      Matcher matcher = ANCHOR_TAG_PATTERN.matcher(text);
      while (matcher.find()) {
        String anchorText = matcher.group(2);
        // Text may be spread across multiple lines. Put everything in one line.
        if (anchorText != null) {
          anchorText = anchorText.replaceAll("[\\s]+", " ");
          if (anchorText.equals(hyperlinkText)) {
            url = matcher.group(1);
            break;
          }
        }
      }
      assertNotNull("Failed to find URL for hyperlink " + quote(hyperlinkText), url);
      return new HyperlinkFixture(myRobot, editorComponent, url);
    }
  }

  public static class HyperlinkFixture {
    @NotNull private final Robot myRobot;
    @NotNull private final JEditorPane myTarget;
    @NotNull private final String myUrl;

    HyperlinkFixture(@NotNull Robot robot, @NotNull JEditorPane target, @NotNull String url) {
      myRobot = robot;
      myTarget = target;
      myUrl = url;
    }

    @NotNull
    public HyperlinkFixture click() {
      // at least move the mouse where the message is, so we can know that something is happening.
      myRobot.moveMouse(visibleCenterOf(myTarget));

      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          myTarget.fireHyperlinkUpdate(new HyperlinkEvent(this, ACTIVATED, null, myUrl));
        }
      });
      return this;
    }

    @NotNull
    public HyperlinkFixture requireUrl(@NotNull String expected) {
      assertThat(myUrl).as("URL").isEqualTo(expected);
      return this;
    }
  }
}
