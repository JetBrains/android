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


import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.filter.FilterModel;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.ui.SearchTextField;
import com.intellij.util.Alarm;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A modified version of IJ's FilterComponent that allows users to specify a custom delay between typing and
 * the filter box triggering a change event.
 */

public final class FilterComponent extends JPanel {
  static final String OPEN_AND_FOCUS_ACTION = "OpenAndFocusSearchAction";
  static final String CLOSE_ACTION = "CloseSearchAction";
  static final KeyStroke FILTER_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_F, AdtUiUtils.getActionMask());

  private static final String REGEX = "Regex";
  private static final String MATCH_CASE = "Match Case";
  private final FilterModel myModel;

  private JCheckBox myRegexCheckBox;
  private JCheckBox myMatchCaseCheckBox;
  private JLabel myCountLabel;
  private final SearchTextField mySearchField;
  private final Alarm myUpdateAlarm = new Alarm();
  private final int myDelayMs;

  public FilterComponent(@NotNull Filter filter, int textFieldWidth, int historySize, int delayMs) {
    super(new TabularLayout("4px," + textFieldWidth + "px,5px,Fit-,5px,Fit-,20px,Fit-", "Fit-"));

    myDelayMs = delayMs;
    myModel = new FilterModel();
    myModel.setFilter(filter);

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        super.componentShown(e);
        requestFocusInWindow();
      }
    });

    // Configure filter text field
    mySearchField = new SearchTextField() {
      @Override
      protected Runnable createItemChosenCallback(JList list) {
        final Runnable callback = super.createItemChosenCallback(list);
        return () -> {
          callback.run();
          updateModel();
        };
      }

      @Override
      protected Component getPopupLocationComponent() {
        return mySearchField;
      }

      @Override
      protected void onFocusLost() {
        addCurrentTextToHistory();
        super.onFocusLost();
      }
    };
    if (!filter.isEmpty()) {
      mySearchField.setText(filter.getFilterString());
    }

    mySearchField.getTextEditor().addKeyListener(new KeyAdapter() {
      //to consume enter in combo box - do not process this event by default button from DialogWrapper
      @Override
      public void keyPressed(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          mySearchField.addCurrentTextToHistory();
          updateModel();
        }
      }
    });

    mySearchField.addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        onChanged();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        onChanged();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        onChanged();
      }

      private void onChanged() {
        myUpdateAlarm.cancelAllRequests();
        myUpdateAlarm.addRequest(() -> updateModel(), myDelayMs);
      }
    });

    mySearchField.setHistorySize(historySize);

    add(mySearchField, new TabularLayout.Constraint(0, 1));

    // Configure check boxes
    myMatchCaseCheckBox = new JCheckBox(MATCH_CASE, filter.isMatchCase());
    myMatchCaseCheckBox.setMnemonic(KeyEvent.VK_C);
    myMatchCaseCheckBox.setDisplayedMnemonicIndex(MATCH_CASE.indexOf('C'));
    myMatchCaseCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateModel();
      }
    });
    add(myMatchCaseCheckBox, new TabularLayout.Constraint(0, 3));

    myRegexCheckBox = new JCheckBox(REGEX, filter.isRegex());
    myRegexCheckBox.setMnemonic(KeyEvent.VK_G);
    myRegexCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateModel();
      }
    });
    add(myRegexCheckBox, new TabularLayout.Constraint(0, 5));

    myCountLabel = new JLabel();
    myCountLabel.setFont(myCountLabel.getFont().deriveFont(Font.BOLD));
    add(myCountLabel, new TabularLayout.Constraint(0, 7));

    myModel.addMatchResultListener(result -> {
      String text = "";
      if (result.isFilterEnabled()) {
        int count = result.getMatchCount();
        if (count == 0) {
          text = "No matches";
        }
        else if (count == 1) {
          text = "One match";
        }
        else {
          text = new DecimalFormat("#,###").format(count) + " matches";
        }
      }
      myCountLabel.setText(text);
    });
  }

  public FilterComponent(int textFieldWidth, int historySize, int delayMs) {
    this(Filter.EMPTY_FILTER, textFieldWidth, historySize, delayMs);
  }

  public void setFilterText(final String filterText) {
    myUpdateAlarm.cancelAllRequests();
    mySearchField.setText(filterText);
  }

  @NotNull
  public FilterModel getModel() {
    return myModel;
  }

  @Override
  public boolean requestFocusInWindow() {
    return mySearchField.requestFocusInWindow();
  }

  @VisibleForTesting
  @NotNull
  public SearchTextField getSearchField() {
    return mySearchField;
  }

  @VisibleForTesting
  @NotNull
  JLabel getCountLabel() {
    return myCountLabel;
  }

  /**
   * For performance reasons, FilterComponent doesn't apply the filter immediately, but waits for
   * a delay period (so a filter operation is only applied once even if a user types a long String,
   * for example). In tests, however, it may be prudent to call this method after a call to
   * {@link #setFilterText(String)} - otherwise, your test may finish and then the filter component's
   * alarm may fire.
   */
  @VisibleForTesting
  public void waitForFilterUpdated() throws InterruptedException, ExecutionException, TimeoutException {
    myUpdateAlarm.waitForAllExecuted(Long.MAX_VALUE, TimeUnit.SECONDS);
  }

  private void updateModel() {
    myModel.setFilter(new Filter(mySearchField.getText(), myMatchCaseCheckBox.isSelected(), myRegexCheckBox.isSelected()));
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
      if (!showHideButton.isSelected()) {
        // Reset the filter content when dismissed
        filterComponent.setFilterText("");
      }
      else {
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
  static public CommonToggleButton createFilterToggleButton() {
    CommonToggleButton filterButton = new CommonToggleButton("", StudioIcons.Common.FILTER);
    filterButton.setToolTipText(String.format("Filter (%s)", KeymapUtil.getKeystrokeText(FILTER_KEY_STROKE)));
    return filterButton;
  }
}

