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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.util.ui.Header;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.impl.AnchoredButton;
import com.intellij.openapi.wm.impl.StripeButtonUI;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ChildFocusWatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.util.EventListener;

import static com.intellij.openapi.wm.ToolWindowAnchor.*;
import static com.intellij.util.ui.UIUtil.FontSize;
import static com.intellij.util.ui.UIUtil.getLabelFont;
import static javax.swing.SwingUtilities.isDescendingFrom;

public abstract class ToolWindowPanel extends JPanel implements Disposable {
  @NotNull private final Header myHeader;
  @NotNull private final ChildFocusWatcher myFocusWatcher;

  private final EventDispatcher<StateChangeListener> myEventDispatcher = EventDispatcher.create(StateChangeListener.class);

  private JPanel myMinimizedContainerPanel;
  private MinimizeButton myMinimizeButton;
  private AnAction myMinimizeAction;

  protected ToolWindowPanel(@NotNull String title, @Nullable MinimizedInfo minimizedInfo) {
    super(new BorderLayout());
    myHeader = new Header(title) {
      @Override
      public boolean isActive() {
        return isFocused();
      }
    };
    add(myHeader, BorderLayout.NORTH);

    myFocusWatcher = new ChildFocusWatcher(this) {
      @Override
      protected void onFocusGained(FocusEvent event) {
        myHeader.repaint();
      }

      @Override
      protected void onFocusLost(FocusEvent event) {
        myHeader.repaint();
      }
    };

    if (minimizedInfo != null) {
      myMinimizeButton = new MinimizeButton(title, minimizedInfo.icon, minimizedInfo.anchor);
      myMinimizeButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myEventDispatcher.getMulticaster().maximized();
        }
      });

      myMinimizedContainerPanel = new MinimizedContainerPanel(myMinimizeButton);
      myMinimizeAction = new DumbAwareAction("Minimize", "", AllIcons.General.HideRight) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myEventDispatcher.getMulticaster().minimized();
        }
      };
    }
  }

  private boolean isFocused() {
    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component focusOwner = focusManager.getFocusOwner();
    return focusOwner != null && isDescendingFrom(focusOwner, this);
  }

  public void addStateChangeListener(@NotNull StateChangeListener listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  @Nullable
  public JPanel getMinimizedContainerPanel() {
    return myMinimizedContainerPanel;
  }

  @Nullable
  protected AnAction getMinimizeAction() {
    return myMinimizeAction;
  }

  @NotNull
  protected Header getHeader() {
    return myHeader;
  }

  @Nullable
  public ToolWindowAnchor getAnchor() {
    return myMinimizeButton != null ? myMinimizeButton.getAnchor() : null;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myFocusWatcher);
  }

  public static class MinimizedInfo {
    @NotNull final Icon icon;
    @NotNull final ToolWindowAnchor anchor;

    public MinimizedInfo(@NotNull Icon icon, @NotNull ToolWindowAnchor anchor) {
      this.icon = icon;
      this.anchor = anchor;
    }
  }

  public interface StateChangeListener extends EventListener {
    void maximized();

    void minimized();
  }

  /**
   * Panel that displays a minimized {@link ToolWindowPanel}.
   */
  private static class MinimizedContainerPanel extends JPanel {
    @NotNull private final MinimizeButton myMinimizeButton;

    MinimizedContainerPanel(@NotNull MinimizeButton minimizeButton) {
      myMinimizeButton = minimizeButton;
      configureBorder();
      add(myMinimizeButton);
    }

    private void configureBorder() {
      int borderStyle;
      ToolWindowAnchor anchor = myMinimizeButton.getAnchor();
      if (anchor == LEFT) {
        borderStyle = SideBorder.RIGHT;
      }
      else if (anchor == RIGHT) {
        borderStyle = SideBorder.LEFT;
      }
      else if (anchor == BOTTOM) {
        borderStyle = SideBorder.TOP;
      }
      else {
        return;
      }
      setBorder(IdeBorderFactory.createBorder(borderStyle));
    }

    @Override
    public void doLayout() {
      Dimension size = myMinimizeButton.getPreferredSize();
      if (myMinimizeButton.getAnchor() == BOTTOM) {
        myMinimizeButton.setBounds(0, 1, size.width, 25);
      }
      else {
        myMinimizeButton.setBounds(0, 0, getWidth(), size.height);
      }
    }
  }

  private static class MinimizeButton extends AnchoredButton {
    @NotNull private final ToolWindowAnchor myAnchor;

    MinimizeButton(@NotNull String title, @NotNull Icon icon, @NotNull ToolWindowAnchor anchor) {
      super(title, icon);
      myAnchor = anchor;
      setBorder(BorderFactory.createEmptyBorder());
      setFocusable(false);

      setRolloverEnabled(true);
      setOpaque(false);
    }

    @Override
    public void updateUI() {
      setUI(StripeButtonUI.createUI(this));
      setFont(getLabelFont(FontSize.SMALL));
    }

    @Override
    public int getMnemonic2() {
      return 0;
    }

    @Override
    @NotNull
    public ToolWindowAnchor getAnchor() {
      return myAnchor;
    }
  }
}
