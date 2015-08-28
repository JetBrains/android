/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.monitor;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

public abstract class BaseMonitorView implements HierarchyListener, TimelineEventListener {
  @NotNull protected static final String PAUSED_LABEL = "This monitor is disabled.";
  @NotNull private static final Integer OVERLAY_LAYER = JLayeredPane.DEFAULT_LAYER + 10;
  @NotNull private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();

  @NotNull protected Project myProject;
  @NotNull protected JLayeredPane myContentPane;
  @NotNull private JPanel myTextPanel;
  @NotNull private JTextPane myOverlayText;
  @NotNull private HashMap<String, ZOrderedOverlayText> myOverlayLookup;
  @NotNull private PriorityQueue<ZOrderedOverlayText> myVisibleOverlays;

  private static class ZOrderedOverlayText {
    @NotNull private String myText;
    private int myZ;

    private ZOrderedOverlayText(@NotNull String text, int z) {
      myText = text;
      myZ = z;
    }
  }

  protected BaseMonitorView(@NotNull Project project) {
    myProject = project;
    myContentPane = new JBLayeredPane() {
      @Override
      public void doLayout() {
        final Component[] components = getComponents();
        final Rectangle r = getBounds();
        for (Component c : components) {
          c.setBounds(0, 0, r.width, r.height);
        }
      }

      @Override
      public Dimension getPreferredSize() {
        return getBounds().getSize();
      }
    };

    // Use GridBagLayout because it center aligns content by default
    //noinspection UseJBColor
    myTextPanel = new JPanel(new GridBagLayout()) {
      Color translucentBackgroundColor = ColorUtil.toAlpha(BACKGROUND_COLOR, 192);

      @Override
      public void paintComponent(@NotNull Graphics g) {
        g.setColor(translucentBackgroundColor);
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
      }
    };
    myTextPanel.setOpaque(false);

    myOverlayText = new JTextPane();
    myOverlayText.setEditable(false);
    myOverlayText.setOpaque(false);
    myOverlayText.setEditorKit(UIUtil.getHTMLEditorKit());
    myOverlayText.setBackground(UIUtil.TRANSPARENT_COLOR);
    myOverlayText.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    myOverlayLookup = new HashMap<String, ZOrderedOverlayText>();
    myVisibleOverlays = new PriorityQueue<ZOrderedOverlayText>(5, new Comparator<ZOrderedOverlayText>() {
      @Override
      public int compare(ZOrderedOverlayText a, ZOrderedOverlayText b) {
        return a.myZ - b.myZ;
      }
    });

    myTextPanel.add(myOverlayText);
    myTextPanel.setVisible(false);
    myContentPane.add(myTextPanel, OVERLAY_LAYER, 0);
    myContentPane.addHierarchyListener(this);
  }

  @Override
  public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
    if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
      if (!getSampler().isRunning()) {
        getSampler().start();
      }
    }
  }

  @NotNull
  public ComponentWithActions createComponent() {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(myContentPane, BorderLayout.CENTER);
    return new ComponentWithActions.Impl(getToolbarActions(), null, null, null, wrapper);
  }

  public abstract ActionGroup getToolbarActions();

  public abstract void setPaused(boolean paused);

  public abstract boolean isPaused();

  @Override
  public void onStart() {}

  @Override
  public void onStop() {}

  @NotNull
  public abstract String getDescription();

  protected abstract DeviceSampler getSampler();

  /**
   * Registers the given text in the list of possible strings usable in the overlay.
   *
   * @param text   is the unique text reference representing the text to be displayed in the overlay
   * @param index  is the priority of the text, in the range of [0, Integer.MAX_VALUE]
   */
  protected final void addOverlayText(@NotNull String text, int index) {
    assert !myOverlayLookup.containsKey(text) && index >= 0 && index < Integer.MAX_VALUE;
    myOverlayLookup.put(text, new ZOrderedOverlayText(text, index));
  }

  /**
   * Enables and, if lowest {@code index} in all calls to {@link #addOverlayText(String, int)}, displays the text over this view.
   *
   * @param text     is the unique text reference given to {@link #addOverlayText(String, int)}
   * @param enabled  true to enable, false to disable
   */
  protected final void setOverlayEnabled(@NotNull String text, boolean enabled) {
    assert myOverlayLookup.containsKey(text);

    ZOrderedOverlayText orderedText = myOverlayLookup.get(text);

    if (enabled) {
      if (!myVisibleOverlays.contains(orderedText)) {
        myVisibleOverlays.add(orderedText);
        updateOverlayText(myVisibleOverlays.peek().myText);
      }
    }
    else {
      myVisibleOverlays.remove(orderedText);
      if (myVisibleOverlays.size() > 0) {
        updateOverlayText(myVisibleOverlays.peek().myText);
      }
      else {
        updateOverlayText("");
      }
    }
  }

  protected final void setViewComponent(@NotNull JComponent component) {
    myContentPane.add(component, JLayeredPane.DEFAULT_LAYER, 0);
  }

  private void updateOverlayText(String text) {
    myOverlayText.setText(text);
    myTextPanel.setVisible(!text.isEmpty());
    myTextPanel.invalidate();
  }
}
