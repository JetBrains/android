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
package com.android.tools.idea.gradle.messages;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.gradle.messages.navigatable.ProjectSyncErrorNavigatable;
import com.intellij.compiler.impl.CompilerErrorTreeView;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Service that collects and displays, in the "Messages" tool window, post-sync project setup messages (errors, warnings, etc.)
 */
public class ProjectSyncMessages {
  private static final Key<Key<?>> CONTENT_ID_KEY = Key.create("PROJECT_SYNC_MESSAGES_CONTENT_ID");
  private static final Content[] EMPTY_CONTENTS = new Content[0];

  @NonNls public static final String CONTENT_NAME = "Gradle Sync";

  @NotNull private final Key<Key<?>> myContentIdKey = CONTENT_ID_KEY;
  @NotNull private final Key<Key<?>> myContentId = Key.create("project_setup_content");

  @NotNull private final Project myProject;

  @NotNull private final Object myErrorTreeViewLock = new Object();

  @GuardedBy("myErrorTreeViewLock")
  @Nullable private NewErrorTreeViewPanel myErrorTreeView;

  private boolean myMessageViewIsPrepared;
  private boolean myMessagesAutoActivated;

  private int myErrorCount;
  private int myMessageCount;

  @NotNull
  public static ProjectSyncMessages getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectSyncMessages.class);
  }

  public int getErrorCount() {
    return myErrorCount;
  }

  public ProjectSyncMessages(@NotNull Project project) {
    myProject = project;
  }

  public void removeMessages(@NotNull String groupName) {
    synchronized (myErrorTreeViewLock) {
      if (myErrorTreeView != null) {
        ErrorViewStructure errorViewStructure = myErrorTreeView.getErrorViewStructure();
        errorViewStructure.removeGroup(groupName);
      }
    }
  }

  public int getMessageCount(@NotNull String groupName) {
    synchronized (myErrorTreeViewLock) {
      if (myErrorTreeView != null) {
        ErrorViewStructure errorViewStructure = myErrorTreeView.getErrorViewStructure();
        GroupingElement grouping = errorViewStructure.lookupGroupingElement(groupName);
        if (grouping != null) {
          return errorViewStructure.getChildCount(grouping);
        }
      }
    }
    return 0;
  }

  public void add(@NotNull final Message message) {
    Navigatable navigatable = message.getNavigatable();
    if (navigatable instanceof ProjectSyncErrorNavigatable) {
      ((ProjectSyncErrorNavigatable)navigatable).setProject(myProject);
    }
    if (message.getType() == Message.Type.ERROR) {
      myErrorCount++;
    }
    myMessageCount++;
    Runnable showMessageTask = new Runnable() {
      @Override
      public void run() {
        openMessageView();
        show(message);
      }
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      showMessageTask.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(showMessageTask, ModalityState.NON_MODAL);
    }
  }

  public void clearView() {
    prepareMessageView();
    if (getMessageViewContents().length > 0) {
      removeAllContents(null);
    }
    myErrorCount = 0;
    myMessageCount = 0;
  }

  private void prepareMessageView() {
    if (myMessageViewIsPrepared) {
      return;
    }
    myMessageViewIsPrepared = true;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!myProject.isDisposed()) {
          synchronized (myErrorTreeViewLock) {
            // Clear messages from the previous session.
            if (myErrorTreeView == null) {
              // If message view != null, the contents has already been cleared.
              removeAllContents(null);
            }
          }
        }
      }
    });
  }

  private void openMessageView() {
    JComponent component;
    synchronized (myErrorTreeViewLock) {
      if (myErrorTreeView != null) {
        return;
      }
      //noinspection ConstantConditions
      myErrorTreeView = new CompilerErrorTreeView(myProject, null);
      myErrorTreeView.setProcessController(new NewErrorTreeViewPanel.ProcessController() {
        @Override
        public void stopProcess() {
        }

        @Override
        public boolean isProcessStopped() {
          return true;
        }
      });
      component = myErrorTreeView.getComponent();
    }

    MessagesContentManager myContentsManager = new MessagesContentManager();

    Content content = ContentFactory.SERVICE.getInstance().createContent(component, CONTENT_NAME, true);
    content.putUserData(myContentIdKey, myContentId);

    MessageView messageView = getMessageView();
    ContentManager messageViewContentManager = messageView.getContentManager();
    messageViewContentManager.addContent(content);

    myContentsManager.setContent(messageViewContentManager, content);

    removeAllContents(content);
    messageViewContentManager.setSelectedContent(content);
  }

  private void removeAllContents(@Nullable Content toKeep) {
    Content[] contents = getMessageViewContents();
    if (contents.length == 0) {
      return;
    }
    MessageView messageView = getMessageView();
    ContentManager contentManager = messageView.getContentManager();

    for (Content content : contents) {
      if (content.isPinned() || content == toKeep) {
        continue;
      }
      if (content.getUserData(myContentIdKey) != null) { // the content was added by me
        contentManager.removeContent(content, true);
      }
    }
  }

  @NotNull
  private Content[] getMessageViewContents() {
    ToolWindow window = getMessagesToolWindow();
    if (window == null) {
      return EMPTY_CONTENTS;
    }

    Content[] contents = null;
    ContentManager contentManager = getMessageView().getContentManager();
    if (contentManager != null) {
      contents = contentManager.getContents();
    }
    return contents == null ? EMPTY_CONTENTS : contents;
  }

  @NotNull
  private MessageView getMessageView() {
    return MessageView.SERVICE.getInstance(myProject);
  }

  private void show(@NotNull Message message) {
    synchronized (myErrorTreeViewLock) {
      if (myErrorTreeView != null && !myProject.isDisposed()) {
        myErrorTreeView.addMessage(message.getType().getValue(), message.getText(), message.getGroupName(), message.getNavigatable(), null,
                                   null, null);

        boolean autoActivate =
          !myMessagesAutoActivated && (message.getType() == Message.Type.ERROR || (message.getType() == Message.Type.WARNING));
        if (autoActivate) {
          myMessagesAutoActivated = true;
          activateView();
        }
      }
    }
  }

  public void activateView() {
    synchronized (myErrorTreeViewLock) {
      if (myErrorTreeView != null) {
        MessageView messageView = getMessageView();
        Content[] contents = messageView.getContentManager().getContents();
        if (contents.length > 0) {
          messageView.getContentManager().setSelectedContent(contents[0]);
        }

        ToolWindow window = getMessagesToolWindow();
        if (window != null) {
          window.activate(null, false);
        }
      }
    }
  }

  @Nullable
  private ToolWindow getMessagesToolWindow() {
    return ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
  }

  public boolean isEmpty() {
    return myMessageCount == 0;
  }

  private class MessagesContentManager extends ContentManagerAdapter {
    @NotNull private ContentManager myContentManager;
    @Nullable private Content myContent;

    void setContent(@NotNull ContentManager contentManager, @Nullable Content content) {
      myContent = content;
      myContentManager = contentManager;
      contentManager.addContentManagerListener(this);
    }

    @Override
    public void contentRemoved(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        synchronized (myErrorTreeViewLock) {
          if (myErrorTreeView != null && !myProject.isDisposed()) {
            myErrorTreeView.dispose();
            myErrorTreeView = null;
          }
        }
        myContentManager.removeContentManagerListener(this);
        if (myContent != null) {
          myContent.release();
        }
        myContent = null;
      }
    }
  }
}
