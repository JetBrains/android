/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui;


import com.android.tools.adtui.flat.FlatToggleButton;
import com.android.tools.adtui.model.FilterModel;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.SearchTextField;
import com.intellij.util.Alarm;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

/**
 * A modified version of IJ's FilterComponent that allows users to specify a custom delay between typing and
 * the filter box triggering a change event.
 */

public class FilterComponent extends JPanel {
  static final String OPEN_AND_FOCUS_ACTION = "OpenAndFocusSearchAction";
  static final String CLOSE_ACTION = "CloseSearchAction";
  static final KeyStroke FILTER_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_F, SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK);

  private static final String REGEX = "Regex";
  private static final String MATCH_CASE = "Match Case";
  private final FilterModel myModel;

  private JCheckBox myRegexCheckBox;
  private JCheckBox myMatchCaseCheckBox;
  private final SearchTextField myFilter;
  private final Alarm myUpdateAlarm = new Alarm();
  private final int myDelayMs;

  public FilterComponent(int textFieldWidth, int historySize, int delayMs) {
    super(new TabularLayout("4px," + textFieldWidth + "px,5px,Fit,5px,Fit", "Fit"));
    myDelayMs = delayMs;
    myModel = new FilterModel();
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        super.componentShown(e);
        requestFocusInWindow();
      }
    });

    // Configure filter text field
    myFilter = new SearchTextField() {
      @Override
      protected Runnable createItemChosenCallback(JList list) {
        final Runnable callback = super.createItemChosenCallback(list);
        return () -> {
          callback.run();
          filter();
        };
      }

      @Override
      protected Component getPopupLocationComponent() {
        return FilterComponent.this.getPopupLocationComponent();
      }

      @Override
      protected void onFocusLost() {
        addCurrentTextToHistory();
        super.onFocusLost();
      }
    };
    myFilter.getTextEditor().addKeyListener(new KeyAdapter() {
      //to consume enter in combo box - do not process this event by default button from DialogWrapper
      @Override
      public void keyPressed(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          myFilter.addCurrentTextToHistory();
          filter();
        }
        else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          onEscape(e);
        }
      }
    });

    myFilter.addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        onChange();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        onChange();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        onChange();
      }
    });

    myFilter.setHistorySize(historySize);

    add(myFilter, new TabularLayout.Constraint(0, 1));

    // Configure check boxes
    myMatchCaseCheckBox = new JCheckBox(MATCH_CASE);
    myMatchCaseCheckBox.setMnemonic(KeyEvent.VK_C);
    myMatchCaseCheckBox.setDisplayedMnemonicIndex(MATCH_CASE.indexOf('C'));
    myMatchCaseCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myModel.setIsMatchCase(e.getStateChange() == ItemEvent.SELECTED);
      }
    });
    add(myMatchCaseCheckBox, new TabularLayout.Constraint(0, 3));

    myRegexCheckBox = new JCheckBox(REGEX);
    myRegexCheckBox.setMnemonic(KeyEvent.VK_G);
    myRegexCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myModel.setIsRegex(e.getStateChange() == ItemEvent.SELECTED);
      }
    });
    add(myRegexCheckBox, new TabularLayout.Constraint(0, 5));
  }

  protected JComponent getPopupLocationComponent() {
    return myFilter;
  }

  public JTextField getTextEditor() {
    return myFilter.getTextEditor();
  }

  private void onChange() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(() -> filter(), myDelayMs);
  }

  public void reset() {
    myFilter.reset();
  }

  protected void onEscape(KeyEvent e) {
  }

  public String getFilter() {
    return myFilter.getText();
  }

  public void setSelectedItem(final String filter) {
    myFilter.setSelectedItem(filter);
  }

  public void setFilter(final String filter) {
    myFilter.setText(filter);
  }

  public void selectText() {
    myFilter.selectText();
  }

  private void filter() {
    myModel.setFilterString(getFilter());
  }

  @NotNull
  public FilterModel getModel() {
    return myModel;
  }

  @Override
  public boolean requestFocusInWindow() {
    return myFilter.requestFocusInWindow();
  }

  public void dispose() {
    myUpdateAlarm.cancelAllRequests();
  }

  @TestOnly
  JCheckBox getMatchCaseCheckBox() {
    return myMatchCaseCheckBox;
  }

  @TestOnly
  JCheckBox getRegexCheckBox() {
    return myRegexCheckBox;
  }


  public void addOnFilterChange(@NotNull BiConsumer<Pattern, FilterModel> callback) {
    myModel.addOnFilterChange(callback);
  }

  /**
   * A helper method for configuring the default key bindings and focus behavior for a {@link SearchComponent}.
   * Intended behavior:
   * Ctrl+F should make the SearchComponent visible and put focus on the filter textbox.
   * Esc should hide the searchComponent and clear the filter.
   * Clicking on the button should toggle the visibility of the SearchComponent, and in both caes, the filter textbox should be rest.
   *
   * @param containerComponent This is the component designated for handling the key events (Ctrl+F, Esc). It is expected that the key
   *                           bindings only work if this component (or one of its descendants) have focus
   *                           (See also JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT). Whenever the SearchComponent's visibility is
   *                           toggled, the containerComponent is revalidated to make sure the layout is up to date.
   * @param filterComponent    The SearchComponent instance that the containerComponent and showHideButton are associated with.
   * @param showHideButton     The toggle button used for showing/hiding the SearchComponent.
   */
  static public void configureKeyBindingAndFocusBehaviors(@NotNull JComponent containerComponent,
                                                          @NotNull FilterComponent filterComponent,
                                                          @NotNull JToggleButton showHideButton) {
    showHideButton.addActionListener(event -> {
      filterComponent.setVisible(showHideButton.isSelected());
      // Reset the filter content.
      filterComponent.setFilter("");
      if (showHideButton.isSelected()) {
        filterComponent.requestFocusInWindow();
      }
      containerComponent.revalidate();
    });

    InputMap inputMap = containerComponent.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    ActionMap actionMap = containerComponent.getActionMap();

    inputMap.put(FILTER_KEY_STROKE, OPEN_AND_FOCUS_ACTION);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CLOSE_ACTION);
    actionMap.put(OPEN_AND_FOCUS_ACTION, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!showHideButton.isSelected()) {
          // Let the button's ActionListener handle the event.
          showHideButton.doClick(0);
        }
        else {
          // Otherwise, just reset the focus.
          filterComponent.requestFocusInWindow();
        }
      }
    });

    actionMap.put(CLOSE_ACTION, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!showHideButton.isSelected()) {
          // Do nothing since search component is not opened.
          return;
        }

        // Let the button's ActionListener handle the event.
        showHideButton.doClick(0);
        // Put the focus back on the container, so the key bindings still work.
        containerComponent.requestFocusInWindow();
      }
    });
  }

  @NotNull
  static public FlatToggleButton createFilterToggleButton() {
    FlatToggleButton filterButton = new FlatToggleButton("", StudioIcons.Common.FILTER);
    filterButton.setToolTipText(String.format("Filter (%s)", KeymapUtil.getKeystrokeText(FILTER_KEY_STROKE)));
    return filterButton;
  }
}

