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

import static com.intellij.openapi.wm.ToolWindowAnchor.BOTTOM;
import static com.intellij.openapi.wm.ToolWindowAnchor.LEFT;
import static com.intellij.openapi.wm.ToolWindowAnchor.RIGHT;
import static com.intellij.util.ui.UIUtil.FontSize;
import static com.intellij.util.ui.UIUtil.getLabelFont;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.impl.AnchoredButton;
import com.intellij.toolWindow.StripeButtonUi;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.EventDispatcher;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.EventListener;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ToolWindowPanel extends JPanel implements Disposable {
  @NotNull private final ToolWindowHeader myHeader;
  @NotNull private final EventDispatcher<RestoreListener> myEventDispatcher = EventDispatcher.create(RestoreListener.class);

  private JPanel myMinimizedPanel;
  private AnchoredToolWindowButton myAnchoredButton;

  protected ToolWindowPanel(@NotNull @NlsContexts.TabTitle String title, @NotNull Icon icon, @Nullable ToolWindowAnchor anchor) {
    super(new BorderLayout());
    myHeader = ToolWindowHeader.createAndAdd(title, icon, this, anchor);

    if (anchor != null) {
      myAnchoredButton = new AnchoredToolWindowButton(myHeader, anchor);
      myAnchoredButton.addActionListener(e -> {
        myAnchoredButton.setSelected(false);
        myEventDispatcher.getMulticaster().restored();
      });

      myMinimizedPanel = new MinimizedContainerPanel(myAnchoredButton);
    }
  }

  public void addRestoreListener(@NotNull RestoreListener listener) {
    myEventDispatcher.addListener(listener, this);
  }

  @Nullable
  public JPanel getMinimizedPanel() {
    return myMinimizedPanel;
  }

  @NotNull
  public ToolWindowHeader getHeader() {
    return myHeader;
  }

  @Nullable
  public ToolWindowAnchor getAnchor() {
    return myAnchoredButton != null ? myAnchoredButton.getAnchor() : null;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myHeader);
  }

  public interface RestoreListener extends EventListener {
    void restored();
  }

  /**
   * Panel that displays a minimized {@link ToolWindowPanel}.
   */
  private static class MinimizedContainerPanel extends JPanel {
    @NotNull private final AnchoredToolWindowButton myAnchoredButton;

    MinimizedContainerPanel(@NotNull AnchoredToolWindowButton anchoredButton) {
      myAnchoredButton = anchoredButton;
      configureBorder();
      add(myAnchoredButton);
    }

    private void configureBorder() {
      int borderStyle;
      ToolWindowAnchor anchor = myAnchoredButton.getAnchor();
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
      Dimension size = myAnchoredButton.getPreferredSize();
      if (myAnchoredButton.getAnchor() == BOTTOM) {
        myAnchoredButton.setBounds(0, 1, size.width, 25);
      }
      else {
        myAnchoredButton.setBounds(0, 0, getWidth(), size.height);
      }
    }
  }

  private static class AnchoredToolWindowButton extends AnchoredButton {
    @NotNull private final ToolWindowAnchor myAnchor;

    AnchoredToolWindowButton(@NotNull ToolWindowHeader header, @NotNull ToolWindowAnchor anchor) {
      super(header.getTitle(), header.getIcon());
      myAnchor = anchor;
      setBorder(BorderFactory.createEmptyBorder());
      setFocusable(false);

      setRolloverEnabled(true);
      setOpaque(false);
    }

    @Override
    public void updateUI() {
      setUI(new StripeButtonUi());
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
