/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ThemeSelectionDialog;
import com.android.tools.idea.configurations.ThemeSelectionPanel;
import com.android.tools.idea.editors.theme.attributes.editors.StyleListCellRenderer;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NewStyleDialog extends DialogWrapper {
  private final IdeResourceNameValidator myResourceNameValidator;
  private JPanel contentPane;
  private JTextField myStyleNameTextField;
  private JLabel myMessageLabel;
  private JLabel myParentStyleLabel;
  private JLabel myStyleNameLabel;
  private JComboBox myParentStyleComboBox;
  /** Message displayed when the style name is empty */
  private final String myEmptyStyleValidationText;
  private @Nullable ThemeSelectionPanel.ThemeChangedListener myThemeChangedListener;

  /**
   * Creates a new style dialog. This dialog it's used both to create new themes and new styles.
   * @param isTheme Whether the new item will be a theme or a regular style. This will only affect the messages displayed to user.
   * @param context The current theme editor context.
   * @param defaultParentName The parent style that will be preselected in the parent text field.
   * @param currentThemeName The current theme name. This is used to automatically generate style names suggestions.
   * @param message Message to display to the user when creating the new style.
   */
  public NewStyleDialog(boolean isTheme,
                        @NotNull ThemeEditorContext context,
                        @Nullable String defaultParentName,
                        @Nullable final String currentThemeName,
                        @Nullable String message) {
    super(true);

    if (!Strings.isNullOrEmpty(message)) {
      myMessageLabel.setText(message);
      myMessageLabel.setVisible(true);
    } else {
      myMessageLabel.setVisible(false);
    }

    final Configuration configuration = context.getConfiguration();
    myResourceNameValidator =
      IdeResourceNameValidator.forResourceName(ResourceType.STYLE, AppResourceRepository.getOrCreateInstance(configuration.getModule()));

    String styleTypeString = isTheme ? "theme"  : "style";
    setTitle("New " + StringUtil.capitalize(styleTypeString));
    myStyleNameLabel.setText(String.format("New %1$s name:", styleTypeString));
    myParentStyleLabel.setText(String.format("Parent %1$s name:", styleTypeString));
    myEmptyStyleValidationText = String.format("You must specify a %1$s name", styleTypeString);

    myStyleNameTextField.setText(getNewStyleNameSuggestion(defaultParentName, currentThemeName));

    final ThemeResolver themeResolver = context.getThemeResolver();
    final ImmutableList<String> defaultThemeNames = ThemeEditorUtils.getDefaultThemeNames(themeResolver);

    myParentStyleComboBox.setRenderer(new StyleListCellRenderer(context, myParentStyleComboBox));
    final ParentThemesListModel parentThemesListModel = new ParentThemesListModel(defaultThemeNames, defaultParentName);
    myParentStyleComboBox.setModel(parentThemesListModel);
    myParentStyleComboBox.addActionListener(e -> {
      String selectedValue = (String)myParentStyleComboBox.getSelectedItem();
      if (ParentThemesListModel.SHOW_ALL_THEMES.equals(selectedValue)) {
        myParentStyleComboBox.hidePopup();
        final ThemeSelectionDialog dialog = new ThemeSelectionDialog(configuration);

        if (myThemeChangedListener != null) {
          dialog.setThemeChangedListener(myThemeChangedListener);
        }

        dialog.show();
        selectedValue = dialog.isOK() ? dialog.getTheme() : null;
      }
      if (selectedValue == null) {
        selectedValue = (String)parentThemesListModel.getElementAt(0);
      }
      else if (!defaultThemeNames.contains(selectedValue)) {
          parentThemesListModel.removeElement(selectedValue);
          parentThemesListModel.insertElementAt(selectedValue, 0);
      }
      myParentStyleComboBox.setSelectedItem(selectedValue);
      myStyleNameTextField.setText(getNewStyleNameSuggestion(selectedValue, currentThemeName));
      if (myThemeChangedListener != null) {
        myThemeChangedListener.themeChanged(selectedValue);
      }
    });

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStyleNameTextField;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String newStyleName = myStyleNameTextField.getText();
    if (Strings.isNullOrEmpty(newStyleName)) {
      return new ValidationInfo(myEmptyStyleValidationText, myStyleNameTextField);
    }

    if (!myResourceNameValidator.checkInput(newStyleName)) {
      // If checkInput is false, getErrorText will be not null.
      //noinspection ConstantConditions
      return new ValidationInfo(myResourceNameValidator.getErrorText(newStyleName), myStyleNameTextField);
    }

    return super.doValidate();
  }

  @Override
  public void show() {
    if (myThemeChangedListener != null) {
      myThemeChangedListener.themeChanged((String)myParentStyleComboBox.getSelectedItem());
    }
    super.show();
  }

  public String getStyleName() {
    return myStyleNameTextField.getText();
  }

  public String getStyleParentName() {
    return (String)myParentStyleComboBox.getSelectedItem();
  }

  private static final String[] COMMON_THEME_NAMES = {"Material", "Holo", "Leanback", "Micro", "DeviceDefault", "AppCompat"};

  /**
   * Returns a suggestion for a new style name based on both the parent style name and the current theme name. It will try to replace parent
   * theme names with the passed theme name.
   * <p/>
   * <p/>For a parent style name like <pre>Widget.Material.Button</pre> and a theme name <pre>MyTheme</pre>, it would generate the name
   * <pre>Widget.MyTheme.Button</pre>
   *
   * @param parentQualifiedName  The parent style name, possibly with "android:" prefix.
   * @param currentThemeName The current theme name.
   */
  @NotNull
  static String getNewStyleNameSuggestion(@Nullable String parentQualifiedName, @Nullable String currentThemeName) {
    if (Strings.isNullOrEmpty(parentQualifiedName) || Strings.isNullOrEmpty(currentThemeName)) {
      return "";
    }

    String parentStyleName = ResolutionUtils.getNameFromQualifiedName(parentQualifiedName);
    if (parentStyleName.equals(currentThemeName)) {
      return "";
    }
    currentThemeName = currentThemeName.replace("Theme.", "");
    for (String themeName : COMMON_THEME_NAMES) {
      if (parentStyleName.matches(".*\\b" + themeName + "\\b.*")) {
        // The name it's at the end
        return parentStyleName.replaceFirst("\\b" + themeName + "\\b", currentThemeName);
      }
    }

    return parentStyleName + '.' + currentThemeName;
  }

  public void setThemeChangedListener(@NotNull ThemeSelectionPanel.ThemeChangedListener themeChangedListener) {
    myThemeChangedListener = themeChangedListener;
  }

  public void enableParentChoice(boolean enable) {
    myParentStyleComboBox.setEnabled(enable);
  }
}
