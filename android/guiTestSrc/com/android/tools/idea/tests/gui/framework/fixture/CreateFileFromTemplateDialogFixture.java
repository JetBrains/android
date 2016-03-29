/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.theme.EditorTextFieldFixture;
import com.android.tools.lint.detector.api.TextFormat;
import com.intellij.androidstudio.actions.CreateFileFromTemplateDialog;
import com.intellij.androidstudio.actions.CreateFileFromTemplateDialog.Visibility;
import com.intellij.ui.EditorTextField;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JRadioButtonFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;


public class CreateFileFromTemplateDialogFixture extends IdeaDialogFixture<CreateFileFromTemplateDialog> {

  protected CreateFileFromTemplateDialogFixture(@NotNull Robot robot,
                                                @NotNull DialogAndWrapper<CreateFileFromTemplateDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public static CreateFileFromTemplateDialogFixture find(@NotNull Robot robot) {
    return new CreateFileFromTemplateDialogFixture(robot, find(robot, CreateFileFromTemplateDialog.class));
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture setName(@NotNull String name) {
    EditorTextFieldFixture.findByLabel(robot(), target(), "Name:").replaceText(name);
    return this;
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture selectKind(@NotNull final Kind kind) {
    JComboBoxFixture kindFixture = new JComboBoxFixture(robot(), robot().finder().findByType(target(), JComboBox.class));
    kindFixture.selectItem(kind.getTemplateName());
    return this;
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture setPackage(@NotNull final String newPackage) {
    EditorTextFieldFixture.findByLabel(robot(), target(), "Package:").replaceText(newPackage);
    return this;
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture waitForErrorMessageToAppear(@NotNull final String errorMessage) {
    GuiTests.waitUntilShowing(robot(), target(), new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel label) {
        String labelText = label.getText();
        if (labelText != null) {
          return errorMessage.equals(TextFormat.HTML.convertTo(labelText, TextFormat.TEXT).trim());
        }
        return false;
      }
    });
    return this;
  }

  @NotNull
  public <T extends Component> T find(@Nullable String name, @NotNull Class<T> type, @NotNull ComponentVisibility visibility) {
    return robot().finder().findByName(target(), name, type, visibility.equals(ComponentVisibility.VISIBLE));
  }

  public void clickOk() {
    GuiTests.findAndClickOkButton(this);
  }

  public void setVisibility(@NotNull Visibility visibility) {
    String buttonName = visibility.equals(Visibility.PUBLIC) ? "public_radio_button" : "package_private_radio_button";
    JRadioButtonFixture visibilityButton = new JRadioButtonFixture(robot(), buttonName);
    visibilityButton.setSelected(true);
  }

  public void setModifier(@NotNull Modifier modifier) {
    String buttonName = modifier.toString().toLowerCase(Locale.US) + "_radio_button";
    JRadioButtonFixture modifierButton = new JRadioButtonFixture(robot(), buttonName);
    modifierButton.setSelected(true);
  }

  public void setInterface(@NotNull String iface) {
    EditorTextFieldFixture.findByLabel(robot(), target(), "Interface(s):").replaceText(iface);
  }

  // TODO: REMOVE
  // This is for mystery logging purposes only.
  public String getInterface() {
    return robot().finder().findByLabel(target(), "Interface(s):", EditorTextField.class).getText();
  }
  // TODO: REMOVE
  // This is for mystery logging purposes only.
  public String getSuperclass() {
    return robot().finder().findByLabel(target(), "Superclass:", EditorTextField.class).getText();
  }


  public void setSuperclass(@NotNull String superclass) {
    EditorTextFieldFixture.findByLabel(robot(), target(), "Superclass:").replaceText(superclass);
  }

  @NotNull
  public JCheckBoxFixture findCheckBox(@NotNull String name) {
    return new JCheckBoxFixture(robot(), name);
  }

  public enum Kind {
    CLASS("class", "Class"),
    ENUM("enum", "Enum"),
    INTERFACE("interface", "Interface"),
    ANNOTATION("@interface", "Annotation");

    private final String myKindName;
    private final String myTemplateName;

    Kind(String kindName, String templateName) {
      myKindName = kindName;
      myTemplateName = templateName;
    }

    @Override
    @NotNull
    public String toString() {
      return myKindName;
    }

    @NotNull
    public String getTemplateName() {
      return myTemplateName;
    }
  }

  public enum ComponentVisibility {
    VISIBLE,
    NOT_VISIBLE
  }

  public enum Modifier {
    ABSTRACT,
    FINAL,
    NONE
  }
}
