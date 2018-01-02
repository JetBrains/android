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
package com.android.tools.idea.profilers;

import com.android.tools.adtui.FilterComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.profilers.SearchComponent;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class IntellijSearchComponent implements SearchComponent {
  private static final String REGEX = "Regex";
  private static final String MATCH_CASE = "Match Case";
  private final FilterComponent myFilterComponent;
  private final JComponent myComponent;
  private final SearchModel myModel;

  private JCheckBox myRegexCheckBox;
  private JCheckBox myMatchCaseCheckBox;

  private static class SearchModel {
    private final ArrayList<Consumer<Pattern>> myConsumers = new ArrayList<>();

    private boolean myIsRegex;
    private boolean myIsMatchCase;
    private String myFilterString;

    public void setFilterString(@NotNull String filterString) {
      if (!filterString.equals(myFilterString)) {
        myFilterString = filterString;
        notifyFilterChange();
      }
    }

    public boolean getIsRegex() {
      return myIsRegex;
    }

    public void setIsRegex(boolean regex) {
      if (myIsRegex != regex) {
        myIsRegex = regex;
        notifyFilterChange();
      }
    }

    public boolean getIsMatchCase() {
      return myIsMatchCase;
    }

    public void setIsMatchCase(boolean matchCase) {
      if (myIsMatchCase != matchCase) {
        myIsMatchCase = matchCase;
        notifyFilterChange();
      }
    }

    public void addOnFilterChange(@NotNull Consumer<Pattern> callback) {
      myConsumers.add(callback);
    }

    private void notifyFilterChange() {
      for (Consumer<Pattern> consumer : myConsumers) {
        consumer.consume(AdtUiUtils.getFilterPattern(myFilterString, myIsMatchCase, myIsRegex));
      }
    }
  }

  public IntellijSearchComponent(@NotNull String propertyName, int textFieldWidth, int delayMs) {
    myComponent = new JPanel(new TabularLayout(textFieldWidth + "px,Fit,Fit", "*,Fit,*"));
    myModel = new SearchModel();

    // Configure Filter
    myFilterComponent = new FilterComponent(propertyName, 5, delayMs) {

      @Override
      public void filter() {
        myModel.setFilterString(getFilter());
      }
    };
    myFilterComponent.setMinimumSize(new Dimension(0, myFilterComponent.getPreferredSize().height + 3));

    myComponent.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        super.componentShown(e);
        myFilterComponent.requestFocusInWindow();
      }
    });

    myComponent.add(myFilterComponent, new TabularLayout.Constraint(1, 0));

    myRegexCheckBox = new JCheckBox(REGEX);
    myRegexCheckBox.setMnemonic(KeyEvent.VK_G);
    myRegexCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myModel.setIsRegex(e.getStateChange() == ItemEvent.SELECTED);
      }
    });
    myComponent.add(myRegexCheckBox, new TabularLayout.Constraint(1, 1));
    myRegexCheckBox.setSelected(myModel.getIsRegex());

    myMatchCaseCheckBox = new JCheckBox(MATCH_CASE);
    myMatchCaseCheckBox.setMnemonic(KeyEvent.VK_C);
    myMatchCaseCheckBox.setDisplayedMnemonicIndex(MATCH_CASE.indexOf('C'));
    myMatchCaseCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myModel.setIsMatchCase(e.getStateChange() == ItemEvent.SELECTED);
      }
    });
    myComponent.add(myMatchCaseCheckBox, new TabularLayout.Constraint(1, 2));
    myMatchCaseCheckBox.setSelected(myModel.getIsMatchCase());
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void addOnFilterChange(@NotNull Consumer<Pattern> callback) {
    myModel.addOnFilterChange(callback);
  }

  @Override
  public void setText(@NotNull String text) {
    myFilterComponent.setFilter(text);
  }

  @Override
  public void requestFocusInWindow() {
    myFilterComponent.requestFocusInWindow();
  }
}
