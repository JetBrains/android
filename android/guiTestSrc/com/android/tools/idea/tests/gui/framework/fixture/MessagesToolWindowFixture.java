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

import com.google.common.base.Joiner;
import com.intellij.ide.errorTreeView.*;
import com.intellij.openapi.externalSystem.service.notification.EditableNotificationMessageElement;
import com.intellij.openapi.externalSystem.service.notification.NotificationMessageElement;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.util.Consumer;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.TreeCellEditor;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static javax.swing.event.HyperlinkEvent.EventType.ACTIVATED;
import static junit.framework.Assert.*;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.awt.AWT.visibleCenterOf;
import static org.fest.util.Strings.quote;

public class MessagesToolWindowFixture extends ToolWindowFixture {
  MessagesToolWindowFixture(@NotNull Project project, @NotNull Robot robot) {
    super(ToolWindowId.MESSAGES_WINDOW, project, robot);
  }

  @NotNull
  public AbstractContentFixture getGradleSyncContent() {
    Content content = getContent("Gradle Sync");
    assertNotNull(content);
    return new SyncContentFixture(content);
  }

  @NotNull
  public AbstractContentFixture getGradleBuildContent() {
    Content content = getContent("Gradle Build");
    assertNotNull(content);
    return new BuildContentFixture(content);
  }

  public abstract static class AbstractContentFixture {
    @NotNull private final Content myContent;

    private AbstractContentFixture(@NotNull Content content) {
      myContent = content;
    }

    @NotNull
    public MessageFixture findMessageContainingText(@NotNull ErrorTreeElementKind kind, @NotNull final String text) {
      ErrorTreeElement element = doFindMessage(kind, new MessageMatcher() {
        @Override
        protected boolean matches(@NotNull String[] lines) {
          for (String s : lines) {
            if (s.contains(text)) {
              return true;
            }
          }
          return false;
        }
      });
      return createFixture(element);
    }

    @NotNull
    public MessageFixture findMessage(@NotNull ErrorTreeElementKind kind, @NotNull MessageMatcher matcher) {
      ErrorTreeElement found = doFindMessage(kind, matcher);
      return createFixture(found);
    }

    @NotNull
    protected abstract MessageFixture createFixture(@NotNull ErrorTreeElement element);

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

  public class SyncContentFixture extends AbstractContentFixture {
    SyncContentFixture(@NotNull Content content) {
      super(content);
    }

    @Override
    @NotNull
    protected MessageFixture createFixture(@NotNull ErrorTreeElement element) {
      return new SyncMessageFixture(myRobot, element);
    }
  }

  public class BuildContentFixture extends AbstractContentFixture {

    public BuildContentFixture(@NotNull Content content) {
      super(content);
    }

    @Override
    @NotNull
    protected MessageFixture createFixture(@NotNull ErrorTreeElement element) {
      return new BuildMessageFixture(myRobot, element);
    }
  }

  public interface MessageFixture {
    @NotNull
    HyperlinkFixture findHyperlink(@NotNull String hyperlinkText);

    @NotNull
    MessageFixture requireLocation(@NotNull File filePath, int line);
  }

  public abstract static class AbstractMessageFixture implements MessageFixture {
    private static final Pattern ANCHOR_TAG_PATTERN = Pattern.compile("<a href=\"(.*?)\">([^<]+)</a>");

    @NotNull protected final Robot myRobot;
    @NotNull protected final ErrorTreeElement myTarget;

    protected AbstractMessageFixture(@NotNull Robot robot, @NotNull ErrorTreeElement target) {
      myRobot = robot;
      myTarget = target;
    }

    @NotNull
    protected String extractUrl(@NotNull String wholeText, @NotNull String hyperlinkText) {
      String url = null;
      Matcher matcher = ANCHOR_TAG_PATTERN.matcher(wholeText);
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
      return url;
    }

    protected void doRequireLocation(@NotNull File filePath, int line) {
      assertThat(myTarget).isInstanceOf(NotificationMessageElement.class);
      NotificationMessageElement element = (NotificationMessageElement)myTarget;

      Navigatable navigatable = element.getNavigatable();
      assertThat(navigatable).isInstanceOf(OpenFileDescriptor.class);

      OpenFileDescriptor descriptor = (OpenFileDescriptor)navigatable;
      File actualPath = virtualToIoFile(descriptor.getFile());
      assertTrue(String.format("Expected:'%1$s' but was:'%2$s'", filePath.getPath(), actualPath.getPath()),
                 filesEqual(filePath, actualPath));

      assertThat((descriptor.getLine() + 1)).as("line").isEqualTo(line); // descriptor line is zero-based.
    }
  }

  public static class SyncMessageFixture extends AbstractMessageFixture {
    public SyncMessageFixture(@NotNull Robot robot, @NotNull ErrorTreeElement target) {
      super(robot, target);
    }

    @Override
    @NotNull
    public SyncHyperlinkFixture findHyperlink(@NotNull String hyperlinkText) {
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
      String url = extractUrl(text, hyperlinkText);
      return new SyncHyperlinkFixture(myRobot, url, editorComponent);
    }

    @Override
    @NotNull
    public SyncMessageFixture requireLocation(@NotNull File filePath, int line) {
      doRequireLocation(filePath, line);
      return this;
    }
  }

  public static class BuildMessageFixture extends AbstractMessageFixture {
    public BuildMessageFixture(@NotNull Robot robot, @NotNull ErrorTreeElement target) {
      super(robot, target);
    }

    @Override
    @NotNull
    public HyperlinkFixture findHyperlink(@NotNull String hyperlinkText) {
      String wholeText = Joiner.on('\n').join(myTarget.getText());
      String url = extractUrl(wholeText, hyperlinkText);
      Object data = myTarget.getData();
      if (!(data instanceof Consumer)) {
        fail(String.format("Can't create hyperlink fixture for a link with text '%s' from message '%s'. Reason: link action is undefined",
                           hyperlinkText, wholeText));
      }
      //noinspection unchecked
      return new BuildHyperlinkFixture(myRobot, url, (Consumer<String>)data);
    }

    @Override
    @NotNull
    public BuildMessageFixture requireLocation(@NotNull File filePath, int line) {
      doRequireLocation(filePath, line);
      return this;
    }
  }

  public interface HyperlinkFixture {
    /**
     * Emulates target link's click
     *
     * @param synchronous  a flag which determines if current method call should wait until UI actions triggered by the link click
     *                     are finished. For example, suppose that the click opens particular dialog. Test execution flow continues
     *                     only after the dialog is hidden if this argument is set to <code>true</code>; execution proceeds immediately
     *                     if the flag is set to <code>false</code>
     * @return
     */
    @NotNull
    HyperlinkFixture click(boolean synchronous);

    @NotNull
    HyperlinkFixture requireUrl(@NotNull String expected);
  }

  public abstract static class AbstractHyperlinkFixture implements HyperlinkFixture {
    @NotNull protected final Robot  myRobot;
    @NotNull protected final String myUrl;

    protected AbstractHyperlinkFixture(@NotNull Robot robot, @NotNull String url) {
      myRobot = robot;
      myUrl = url;
    }

    @Override
    @NotNull
    public HyperlinkFixture requireUrl(@NotNull String expected) {
      assertThat(myUrl).as("URL").isEqualTo(expected);
      return this;
    }

    @NotNull
    @Override
    public HyperlinkFixture click(boolean synchronous) {
      final Runnable action = new Runnable() {
        @Override
        public void run() {
          doClick();
        }
      };
      if (synchronous) {
        GuiActionRunner.execute(new GuiTask() {
          @Override
          protected void executeInEDT() throws Throwable {
            action.run();
          }
        });
      }
      else {
        SwingUtilities.invokeLater(action);
      }
      return this;
    }

    protected abstract void doClick();
  }

  public static class SyncHyperlinkFixture extends AbstractHyperlinkFixture {
    @NotNull private final JEditorPane myTarget;

    public SyncHyperlinkFixture(@NotNull Robot robot, @NotNull String url, @NotNull JEditorPane target) {
      super(robot, url);
      myTarget = target;
    }

    @Override
    protected void doClick() {
      // at least move the mouse where the message is, so we can know that something is happening.
      myRobot.moveMouse(visibleCenterOf(myTarget));
      myTarget.fireHyperlinkUpdate(new HyperlinkEvent(this, ACTIVATED, null, myUrl));
    }
  }

  public static class BuildHyperlinkFixture extends AbstractHyperlinkFixture {

    @NotNull private final Consumer<String> myUrlAction;

    public BuildHyperlinkFixture(@NotNull Robot robot, @NotNull String url, @NotNull Consumer<String> urlAction) {
      super(robot, url);
      myUrlAction = urlAction;
    }

    @Override
    protected void doClick() {
      myUrlAction.consume(myUrl);
    }
  }
}
