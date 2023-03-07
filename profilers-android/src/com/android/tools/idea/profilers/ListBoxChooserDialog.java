/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.adtui.TabularLayout;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

/**
 * Listbox dialog chooser for an array of options.
 */
public class ListBoxChooserDialog<T> extends DialogWrapper {
  /**
   * Message to be shown above listbox.
   */
  @Nullable
  private final String myMessage;
  /**
   * This value represents the currently selected option in the dialog, which is never null.
   */
  @NotNull
  private T mySelectedOption;
  /**
   * Presentation used for showing the currently selected option.
   */
  @NotNull
  private final Presentation myActivePresentation;
  /**
   * List of options to present to the user. The first option in the list is the default selected.
   */
  @NotNull
  private final List<T> myOptions;
  /**
   * Function to convert from options to strings for use in the presentation adapter.
   */
  @NotNull
  private final Function<T, String> myPresentationAdapter;

  /**
   * Constructs a new list box dialog box.
   *
   * @param title               title to show in the title bar.
   * @param message             message to show above the list box.
   * @param options             options to present to the user in the list box.
   * @param presentationAdapter adapter to convert from option to string for presentation.
   */
  public ListBoxChooserDialog(@NotNull String title,
                              @Nullable String message,
                              @NotNull List<T> options,
                              @NotNull Function<T, String> presentationAdapter) {
    super(false);
    setTitle(title);
    myMessage = message;

    myPresentationAdapter = presentationAdapter;
    myOptions = options;
    assert !myOptions.isEmpty();
    mySelectedOption = myOptions.get(0);
    myActivePresentation = new Presentation();
    init();
  }

  private void updateActivePresentation() {
    myActivePresentation.setText(myPresentationAdapter.apply(mySelectedOption));
  }

  @NotNull
  public T getSelectedValue() {
    return mySelectedOption;
  }

  @Override
  protected JComponent createCenterPanel() {
    // Create a panel with our message, and combo box to show what option the user has currently selected.
    JPanel panel = new JPanel(new TabularLayout("*", "Fit-,5px,*,5px")) {

      // To prevent the "Select a process" popup dialog from hiding the selection dropdown when the height
      // is too small, a minimum height is set.
      @Override
      public Dimension getMinimumSize() {
        return new Dimension(super.getMinimumSize().width, super.getPreferredSize().height);
      }
    };
    if (myMessage != null) {
      panel.add(new JLabel(myMessage), new TabularLayout.Constraint(0, 0));
    }
    updateActivePresentation();
    panel.add(new OptionsSelectorComboBox().createCustomComponent(myActivePresentation, ActionPlaces.UNKNOWN), new TabularLayout.Constraint(2, 0));
    return panel;
  }

  /**
   * Helper class to build our UI for the selected action combo box. This helper is also responsible for converting the options
   * to {@link AnAction} elements.
   */
  private class OptionsSelectorComboBox extends ComboBoxAction {
    NonOpaquePanel myPanel = new NonOpaquePanel(new BorderLayout());

    @NotNull
    @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      ComboBoxButton button = new ComboBoxButton(presentation) {
        @Override
        public Dimension getPreferredSize() {
          Dimension d = super.getPreferredSize();
          d.width = Math.max(d.width, JBUI.scale(75));
          return d;
        }
      };
      myPanel.setBorder(JBUI.Borders.emptyRight(2));
      myPanel.add(button);
      return myPanel;
    }

    @Override
    protected int getMaxRows() {
      // Due to a bug in IJ this does nothing, but when the bug is fixed we will limit the list box to show 10 rows, and a scrollbar.
      // See: https://youtrack.jetbrains.com/issue/IJSDK-399
      return 10;
    }

    @Override
    protected int getMinWidth() {
      return myPanel.getWidth();
    }

    @Override
    @NotNull
    protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
      final DefaultActionGroup allActionsGroup = new DefaultActionGroup();
      for (T option : myOptions) {
        SelectTargetAction action = new SelectTargetAction(option);
        allActionsGroup.add(action);
      }
      return allActionsGroup;
    }
  }

  /**
   * Simple action element that updates the combo box presentation, as well as the actively selected option.
   */
  private class SelectTargetAction extends AnAction {
    private final T myOption;

    private SelectTargetAction(T option) {
      myOption = option;
      String name = myPresentationAdapter.apply(option);
      Presentation presentation = getTemplatePresentation();
      presentation.setText(name, false);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySelectedOption = myOption;
      updateActivePresentation();
    }
  }
}