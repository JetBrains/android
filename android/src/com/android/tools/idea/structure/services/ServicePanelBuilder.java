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
package com.android.tools.idea.structure.services;

import com.android.tools.idea.ui.ProportionalLayout;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.collections.ObservableList;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Builder that builds up the panel which is defined in service.xml. Use {@link #getPanel()} to get
 * the underlying Swing panel.
 */
public final class ServicePanelBuilder {

  @NotNull private final JPanel myRootPanel;
  @NotNull private final BindingsManager myBindings = new BindingsManager();
  @NotNull private final Stack<UiGrid> myGrids = new Stack<UiGrid>();

  public ServicePanelBuilder() {
    myRootPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    // Pass BindingsManager to the panel so it stays alive after this builder goes away.
    myRootPanel.putClientProperty("bindings", myBindings);
  }

  public JPanel getPanel() {
    assert myGrids.isEmpty() : "startGrid called without matching endGrid";
    return myRootPanel;
  }

  /**
   * Return the active bindings manager for this panel, useful so we can register new bindings
   * externally.
   */
  @NotNull
  public BindingsManager getBindings() {
    return myBindings;
  }

  /**
   * Begin a grid, which groups added components into a table-like layout until {@link #endGrid()}
   * is called. You can nest grids.
   */
  public JPanel startGrid(@NotNull String colDefinitions) {
    UiGrid uiGrid = new UiGrid(colDefinitions);
    myGrids.push(uiGrid);
    return uiGrid.getPanel();
  }

  public void endGrid() {
    assert !myGrids.isEmpty() : "endGrid called without matching startGrid";
    addComponent(myGrids.pop().getPanel());
  }

  /**
   * Set the row index of the next added component. It is an error to call this while we are not
   * constructing a grid.
   */
  public void setRow(int row) {
    assert !myGrids.isEmpty() : "setRow called without calling startGrid";
    myGrids.peek().setCurrRow(row);
  }

  /**
   * Set the row index of the next added component. It is an error to call this while we are not
   * constructing a grid.
   */
  public void setCol(int col) {
    assert !myGrids.isEmpty() : "setCol called without calling startGrid";
    myGrids.peek().setCurrCol(col);
  }

  public JButton addButton() {
    JButton button = new JButton();
    addComponent(button);
    return button;
  }

  public JCheckBox addCheckbox() {
    JCheckBox checkbox = new JCheckBox();
    addComponent(checkbox);
    return checkbox;
  }

  public JLabel addLabel() {
    JLabel label = new JLabel();
    addComponent(label);
    return label;
  }

  public HyperlinkLabel addLink(@NotNull String text, @NotNull final URI uri) {
    HyperlinkLabel linkLabel = new HyperlinkLabel(text);
    linkLabel.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        try {
          Desktop.getDesktop().browse(uri);
        }
        catch (IOException e1) {
          // Don't care
        }
      }
    });
    addComponent(linkLabel);
    return linkLabel;
  }

  public JTextField addField() {
    JTextField field = new JTextField();
    addComponent(field);
    return field;
  }

  public JComboBox addComboBox(@NotNull final ObservableList<String> backingList) {
    final CollectionComboBoxModel<String> model = new CollectionComboBoxModel<String>(backingList) {
      @NotNull
      @Override
      public List<String> getItems() {
        return backingList;
      }
    };

    final ComboBox comboBox = new ComboBox(model);

    InvalidationListener onListModified = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        model.update();
        if (backingList.size() > 0 && comboBox.getSelectedIndex() < 0) {
          comboBox.setSelectedIndex(0);
        }
      }
    };

    addComponent(comboBox);
    backingList.addWeakListener(onListModified);
    // Keep weak listener alive as long as the combobox is alive
    comboBox.putClientProperty("onListModified", onListModified);
    return comboBox;
  }

  private void addComponent(@NotNull JComponent component) {
    if (!myGrids.isEmpty()) {
      myGrids.peek().addComponent(component);
    }
    else {
      myRootPanel.add(component);
    }
  }

  /**
   * Tracks the state of the row of parameters currently being built.
   */
  private static class UiGrid {
    @NotNull private final JPanel myPanel;
    private final int myNumCols;
    private int myCurrCol;
    private int myCurrRow;

    public UiGrid(@NotNull String colDefinitions) {
      ProportionalLayout layout = ProportionalLayout.fromString(colDefinitions);
      myNumCols = layout.getNumColumns();
      myPanel = new JPanel(layout);
    }

    @NotNull
    public JPanel getPanel() {
      return myPanel;
    }

    public void setCurrCol(int col) {
      if (col >= myNumCols) {
        throw new IllegalArgumentException(String.format("Can't set col = %1$d on a grid with only %2$d columns", col, myNumCols));
      }
      myCurrCol = col;
    }

    public void setCurrRow(int row) {
      myCurrRow = row;
    }

    public void addComponent(@NotNull JComponent component) {
      myPanel.add(component, new ProportionalLayout.Constraint(myCurrRow, myCurrCol));
      myCurrRow = 0;
      myCurrCol = 0;
    }
  }
}
