/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui;

import static com.intellij.icons.AllIcons.General.HideToolWindow;
import static javax.swing.SwingUtilities.isDescendingFrom;

import com.android.tools.idea.gradle.util.ui.Header;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ChildFocusWatcher;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.event.FocusEvent;
import java.util.EventListener;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToolWindowHeader extends Header implements Disposable {
  @NotNull private final Icon myIcon;
  @Nullable private final ToolWindowAnchor myAnchor;

  private JComponent myPreferredFocusedComponent;
  private AnAction myMinimizeAction;
  private ChildFocusWatcher myFocusWatcher;

  private final EventDispatcher<MinimizeListener> myEventDispatcher = EventDispatcher.create(MinimizeListener.class);

  @NotNull
  public static ToolWindowHeader createAndAdd(@NotNull @NlsContexts.TabTitle String title,
                                              @NotNull Icon icon,
                                              @NotNull JComponent parent,
                                              @Nullable ToolWindowAnchor anchor) {
    LayoutManager layout = parent.getLayout();
    assert layout instanceof BorderLayout;

    ToolWindowHeader header = new ToolWindowHeader(title, icon, anchor) {
      @Override
      public boolean isActive() {
        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Component focusOwner = focusManager.getFocusOwner();
        return focusOwner != null && isDescendingFrom(focusOwner, parent);
      }
    };
    parent.add(header, BorderLayout.NORTH);

    MyFocusWatcher focusWatcher = new MyFocusWatcher(parent) {
      @Override
      void onFocusChange(FocusEvent event) {
        header.repaint();
      }
    };

    header.setFocusWatcher(focusWatcher);
    return header;
  }

  private ToolWindowHeader(@NotNull String title, @NotNull Icon icon, @Nullable ToolWindowAnchor anchor) {
    super(title);
    myIcon = icon;
    myAnchor = anchor;
    if (myAnchor != null) {
      myMinimizeAction =
        new DumbAwareAction(AndroidBundle.messagePointer("action.DumbAware.ToolWindowHeader.text.hide"), () -> "", HideToolWindow) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          myEventDispatcher.getMulticaster().minimized();
        }
      };
      setAdditionalActions(Lists.newArrayList(myMinimizeAction));
    }
    addActivationListener(() -> {
      if (myPreferredFocusedComponent != null) {
        myPreferredFocusedComponent.requestFocusInWindow();
      }
    }, this);
  }

  @Override
  public void setAdditionalActions(@NotNull List<AnAction> actions) {
    List<AnAction> allActions = actions;
    if (myMinimizeAction != null && !actions.contains(myMinimizeAction)) {
      allActions = Lists.newArrayList(actions);
      allActions.add(myMinimizeAction);
    }
    super.setAdditionalActions(allActions);
  }

  private void setFocusWatcher(@NotNull ChildFocusWatcher focusWatcher) {
    myFocusWatcher = focusWatcher;
  }

  public void setPreferredFocusedComponent(@Nullable JComponent preferredFocusedComponent) {
    myPreferredFocusedComponent = preferredFocusedComponent;
  }

  @Override
  public void dispose() {
    if (myFocusWatcher != null) {
      Disposer.dispose(myFocusWatcher);
    }
  }

  @NotNull
  Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public ToolWindowAnchor getAnchor() {
    return myAnchor;
  }

  public void addMinimizeListener(@NotNull MinimizeListener listener) {
    myEventDispatcher.addListener(listener, this);
  }

  private static abstract class MyFocusWatcher extends ChildFocusWatcher {
    MyFocusWatcher(@NotNull JComponent parent) {
      super(parent);
    }

    @Override
    protected void onFocusGained(FocusEvent event) {
      onFocusChange(event);
    }

    @Override
    protected void onFocusLost(FocusEvent event) {
      onFocusChange(event);
    }

    abstract void onFocusChange(FocusEvent event);
  }

  public interface MinimizeListener extends EventListener {
    void minimized();
  }
}
