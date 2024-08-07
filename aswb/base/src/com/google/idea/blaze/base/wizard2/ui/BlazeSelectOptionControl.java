/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2.ui;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.settings.ui.ProjectViewUi;
import com.google.idea.blaze.base.wizard2.BlazeNewProjectBuilder;
import com.google.idea.blaze.base.wizard2.BlazeWizardOption;
import com.google.idea.blaze.base.wizard2.BlazeWizardUserSettings;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI.Borders;
import java.util.Collection;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

/** UI for selecting a client during the import process. */
public abstract class BlazeSelectOptionControl<T extends BlazeWizardOption> {
  private final BlazeWizardUserSettings userSettings;
  private final JPanel canvas;
  private final JLabel titleLabel;
  final Collection<OptionUiEntry<T>> optionUiEntryList;

  static class OptionUiEntry<T> {
    final T option;
    final JRadioButton radioButton;

    OptionUiEntry(T option, JRadioButton radioButton) {
      this.option = option;
      this.radioButton = radioButton;
    }
  }

  BlazeSelectOptionControl(BlazeNewProjectBuilder builder, Collection<T> options) {
    this.userSettings = builder.getUserSettings();

    JPanel canvas = new JPanel(new VerticalLayout(4));

    canvas.setPreferredSize(ProjectViewUi.getContainerSize());

    titleLabel = new JLabel(getTitle());
    canvas.add(titleLabel);
    canvas.add(new JSeparator());

    JPanel content = new JPanel(new VerticalLayout(12));
    content.setBorder(Borders.empty(20, 20, 0, 0));
    JScrollPane scrollPane = new JBScrollPane(content);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    canvas.add(scrollPane);

    ButtonGroup buttonGroup = new ButtonGroup();
    Collection<OptionUiEntry<T>> optionUiEntryList = Lists.newArrayList();
    for (T option : options) {
      JPanel vertical = new JPanel(new VerticalLayout(10));
      JRadioButton radioButton = new JRadioButton();
      radioButton.setText(option.getDescription());
      vertical.add(radioButton);

      JComponent optionComponent = option.getUiComponent();
      if (optionComponent != null) {
        JPanel horizontal = new JPanel(new HorizontalLayout(0));
        horizontal.setBorder(Borders.emptyLeft(25));
        horizontal.add(optionComponent);
        vertical.add(horizontal);

        option.optionDeselected();
        radioButton.addItemListener(
            itemEvent -> {
              if (radioButton.isSelected()) {
                option.optionSelected();
              } else {
                option.optionDeselected();
              }
            });
      }

      content.add(vertical);
      buttonGroup.add(radioButton);
      optionUiEntryList.add(new OptionUiEntry<>(option, radioButton));
    }

    OptionUiEntry<?> selected = null;
    String previouslyChosenOption = userSettings.get(getOptionKey(), null);
    if (previouslyChosenOption != null) {
      for (OptionUiEntry<T> entry : optionUiEntryList) {
        if (entry.option.getOptionName().equals(previouslyChosenOption)) {
          selected = entry;
          break;
        }
      }
    }
    if (selected == null) {
      selected = Iterables.getFirst(optionUiEntryList, null);
    }
    if (selected != null) {
      selected.radioButton.setSelected(true);
    }

    this.canvas = canvas;
    this.optionUiEntryList = optionUiEntryList;
  }

  void validateAndUpdateModel(BlazeNewProjectBuilder builder) throws ConfigurationException {
    T option = getSelectedOption();
    if (option == null) {
      throw new ConfigurationException("No option selected.");
    }
    option.validateAndUpdateBuilder(builder);
  }

  public JComponent getUiComponent() {
    return canvas;
  }

  public T getSelectedOption() {
    for (OptionUiEntry<T> entry : optionUiEntryList) {
      if (entry.radioButton.isSelected()) {
        return entry.option;
      }
    }
    return null;
  }

  public void commit() {
    T selectedOption = getSelectedOption();
    if (selectedOption != null) {
      userSettings.put(getOptionKey(), selectedOption.getOptionName());
    }
  }

  /** Call when the title changes. */
  protected void setTitle(String newTitle) {
    titleLabel.setText(newTitle);
  }

  protected abstract String getTitle();

  protected abstract String getOptionKey();
}
