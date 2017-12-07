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
import com.intellij.find.editorHeaderActions.ToggleMatchCase;
import com.intellij.find.editorHeaderActions.ToggleRegex;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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

    // Configure Regex checkbox
    final CheckboxAction regexToggle = new CheckboxAction(REGEX) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myModel.getIsRegex();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myModel.setIsRegex(state);
      }
    };

    Component component = regexToggle.createCustomComponent((new ToggleRegex()).getTemplatePresentation());
    assert (component instanceof JCheckBox);
    myRegexCheckBox = (JCheckBox)component;
    myComponent.add(myRegexCheckBox, new TabularLayout.Constraint(1, 1));
    myRegexCheckBox.setSelected(false);

    // Configure MatchCase checkbox
    final CheckboxAction matchCaseToggle = new CheckboxAction(MATCH_CASE) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myModel.getIsMatchCase();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myModel.setIsMatchCase(state);
      }
    };

    component = matchCaseToggle.createCustomComponent((new ToggleMatchCase()).getTemplatePresentation());
    assert (component instanceof JCheckBox);
    myMatchCaseCheckBox = (JCheckBox)component;
    myComponent.add(myMatchCaseCheckBox, new TabularLayout.Constraint(1, 2));
    myMatchCaseCheckBox.setSelected(false);
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
}
