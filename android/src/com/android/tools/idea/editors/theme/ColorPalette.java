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
package com.android.tools.idea.editors.theme;

import com.android.tools.swing.util.GraphicsUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

/**
 * Component that renders a list of colors.
 */
public class ColorPalette extends JComponent implements Scrollable, ItemSelectable {

  private int myColorBoxSize = JBUI.scale(50);
  private int myColorBoxPadding = myColorBoxSize / 10;
  private boolean myShowCheckeredBackground = false;
  private ColorPaletteModel myColorListModel;
  private final Border mySelectedBorder = UIManager.getBorder("Table.focusCellHighlightBorder");
  private int mySelectedItem = -1;

  /**
   * Model for the ColorPalette component.
   */
  public interface ColorPaletteModel {
    /**
     * Returns the number of elements.
     */
    int getCount();

    /**
     * Returns the element located at the index {@code i}.
     */
    @NotNull
    Color getColorAt(int i);

    /**
     * Returns the index a given color or -1 if it doesn't exist.
     */
    int indexOf(@NotNull Color c);

    /**
     * Returns the tooltip for the element located at the index {@code i}.
     */
    @NotNull
    String getToolTipAt(int i);
  }

  /**
   * Model that defines a static list of colors.
   */
  public static class StaticColorPaletteModel implements ColorPaletteModel {
    private final List<Color> myColorList;

    public StaticColorPaletteModel(@NotNull List<Color> colorList) {
      myColorList = ImmutableList.copyOf(colorList);
    }

    @Override
    public int getCount() {
      return myColorList.size();
    }

    @NotNull
    @Override
    public Color getColorAt(int i) {
      return myColorList.get(i);
    }

    @Override
    public int indexOf(@NotNull Color c) {
      return myColorList.indexOf(c);
    }

    @NotNull
    @Override
    public String getToolTipAt(int i) {
      return myColorList.get(i).toString();
    }
  }

  public ColorPalette(@NotNull ColorPaletteModel colorListModel) {
    myColorListModel = colorListModel;

    setToolTipText(""); // just to initialize tooltips for the component
    setOpaque(false);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int selected = itemAtPoint(e.getPoint());

        if (selected == mySelectedItem) {
          return;
        }

        if (mySelectedItem != -1) {
          itemStateChanged(mySelectedItem, ItemEvent.DESELECTED);
        }
        if (selected != -1) {
          mySelectedItem = selected;
          itemStateChanged(mySelectedItem, ItemEvent.SELECTED);
          repaint();
        }
        else {
          clearSelection();
        }
      }
    });
  }

  public ColorPalette() {
    // Constructor used to display some content on the UI designer.
    this(new StaticColorPaletteModel(Collections.<Color>emptyList()));
  }

  public void setModel(@NotNull ColorPaletteModel colorListModel) {
    myColorListModel = colorListModel;

    revalidate();
  }

  @NotNull
  public ColorPaletteModel getModel() {
    return myColorListModel;
  }

  /**
   * Sets the size of each color box in pixels.
   */
  public void setColorBoxSize(int colorSize) {
    myColorBoxSize = colorSize;

    revalidate();
  }

  /**
   * Sets the padding around each color box in pixels.
   */
  public void setColorBoxPadding(int padding) {
    myColorBoxPadding = padding;

    revalidate();
  }

  public void setShowCheckeredBackground(boolean showCheckeredBackground) {
    myShowCheckeredBackground = showCheckeredBackground;
  }

  @Override
  public Dimension getMinimumSize() {
    int minSize = myColorBoxSize + myColorBoxPadding * 2;
    return new Dimension(minSize, minSize);
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet() || myColorListModel.getCount() < 1) {
      return super.getPreferredSize();
    }

    int minSize = myColorBoxSize + myColorBoxPadding * 2;
    return new Dimension(myColorListModel.getCount() * (myColorBoxSize + myColorBoxPadding) + myColorBoxPadding, minSize);
  }

  private int itemAtPoint(@NotNull Point p) {
    if (p.y <= myColorBoxPadding || p.y > myColorBoxPadding + myColorBoxSize) {
      return -1;
    }

    if (p.x <= myColorBoxPadding) {
      return -1;
    }

    int position = (p.x - myColorBoxPadding) / (myColorBoxSize + myColorBoxPadding);
    int maxBoxX = position * (myColorBoxSize + myColorBoxPadding) + myColorBoxSize;
    // Check that the point is not outside the end boundary
    if ((p.x - myColorBoxPadding) > maxBoxX) {
      return -1;
    }

    return position;
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    int position = itemAtPoint(event.getPoint());
    if (position == -1) {
      return "";
    }

    return myColorListModel.getToolTipAt(position);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (myColorListModel.getCount() < 1) {
      return;
    }

    if (myShowCheckeredBackground) {
      GraphicsUtil.paintCheckeredBackground(g, new Rectangle(getSize()));
    }

    final int width = getWidth();
    for (int i = 0; i < myColorListModel.getCount(); i++) {
      g.setColor(myColorListModel.getColorAt(i));
      int x = i * (myColorBoxSize + myColorBoxPadding) + myColorBoxPadding;
      g.fillRect(x, myColorBoxPadding, myColorBoxSize, myColorBoxSize);

      if (mySelectedItem == i && mySelectedBorder != null) {
        g.setXORMode(Color.WHITE);
        mySelectedBorder.paintBorder(this, g, x, myColorBoxPadding, myColorBoxSize, myColorBoxSize);
        g.setPaintMode();
      }

      if (x > width) {
        break;
      }
    }
  }

  public ItemListener[] getItemListeners() {
    return listenerList.getListeners(ItemListener.class);
  }

  @Override
  public Object[] getSelectedObjects() {
    if (mySelectedItem == -1) {
      return null;
    }
    return new Color[] { myColorListModel.getColorAt(mySelectedItem) };
  }

  @Override
  public void addItemListener(ItemListener l) {
    listenerList.add(ItemListener.class, l);
  }

  @Override
  public void removeItemListener(ItemListener l) {
    listenerList.remove(ItemListener.class, l);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    Dimension preferredSize = getPreferredSize();
    return new Dimension(preferredSize.width, preferredSize.height + UIUtil.getScrollBarWidth());
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 5;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return myColorBoxSize + myColorBoxPadding;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return false;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  public void clearSelection() {
    mySelectedItem = -1;

    repaint();
  }

  private void itemStateChanged(int position, int stateChange) {
    ItemListener[] listeners = getItemListeners();
    if (listeners == null || listeners.length == 0) {
      return;
    }

    ItemEvent itemEvent = new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, myColorListModel.getColorAt(position), stateChange);
    for (ItemListener itemListener : listeners) {
      itemListener.itemStateChanged(itemEvent);
    }
  }
}
