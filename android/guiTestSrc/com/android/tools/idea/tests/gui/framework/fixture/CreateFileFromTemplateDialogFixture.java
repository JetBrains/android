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
import com.android.tools.lint.detector.api.TextFormat;
import com.google.common.base.Joiner;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.ide.actions.as.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.as.CreateFileFromTemplateDialog.Visibility;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ui.components.JBScrollPane;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.fest.swing.timing.Pause.pause;

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
    JTextField nameField = robot().finder().findByLabel(target(), "Name:", JTextField.class);
    GuiActionRunner.execute(new SelectFieldTask(nameField));
    robot().enterText(name);
    return this;
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture selectKind(@NotNull Kind kind) {
    TemplateKindCombo kindBox = robot().finder().findByType(target(), TemplateKindCombo.class);
    kindBox.setSelectedName(kind.getTemplateName());
    return this;
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture setPackage(@NotNull String newPackage) {
    JTextField packageField = robot().finder().findByLabel(target(), "Package:", JTextField.class);
    GuiActionRunner.execute(new SelectFieldTask(packageField));
    packageField.setText("");
    robot().enterText(newPackage);
    return this;
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture waitForErrorMessageToAppear() {
    pause(new Condition("Waiting for an error message to appear.") {
      @Override
      public boolean test() {
        return getDialogWrapper().isShowingError();
      }
    }, GuiTests.SHORT_TIMEOUT);
    return this;
  }

  public JCheckBox getAbstractCheckBox() {
    return robot().finder().findByName(target(), "abstract_check_box", JCheckBox.class);
  }

  public JCheckBox getFinalCheckBox() {
    return robot().finder().findByName(target(), "final_check_box", JCheckBox.class);
  }

  public JCheckBox getOverridesCheckBox() {
    return robot().finder().findByName(target(), "overrides_check_box", JCheckBox.class);
  }

  public void clickOk() {
    focus();
    GuiTests.findAndClickOkButton(this);
  }

  public void setVisibility(Visibility visibility) {
    switch (visibility) {
      case PUBLIC:
        robot().finder().findByName(target(), "public_radio_button", JRadioButton.class).setSelected(true);
        break;

      case PACKAGE_PRIVATE:
        robot().finder().findByName(target(), "package_private_radio_button", JRadioButton.class).setSelected(true);
        break;
    }
  }

  public void setInterfaces(@NotNull Iterable<String> interfaces) {
    JTextField interfacesField = robot().finder().findByLabel(target(), "Interface(s):", JTextField.class);
    GuiActionRunner.execute(new SelectFieldTask(interfacesField));
    robot().enterText(Joiner.on(", ").join(interfaces));
  }

  public void setSuperclass(@NotNull String superclass) {
    JTextField superclassField = robot().finder().findByLabel(target(), "Superclass:", JTextField.class);
    GuiActionRunner.execute(new SelectFieldTask(superclassField));
    robot().enterText(superclass);
  }

  public enum Kind {
    CLASS("class", JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME),
    ENUM("enum", JavaTemplateUtil.INTERNAL_ENUM_TEMPLATE_NAME),
    INTERFACE("interface", JavaTemplateUtil.INTERNAL_INTERFACE_TEMPLATE_NAME),
    ANNOTATION("@interface", JavaTemplateUtil.INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME);

    private final String myKindName;
    private final String myTemplateName;

    Kind(String kindName, String templateName) {
      myKindName = kindName;
      myTemplateName = templateName;
    }

    @Override
    public String toString() {
      return myKindName;
    }

    public String getTemplateName() {
      return myTemplateName;
    }
  }

  private static class SelectFieldTask extends GuiTask {
    private JComponent myJComponent;

    private SelectFieldTask(JComponent component) {
      myJComponent = component;
    }

    @Override
    protected void executeInEDT() throws Throwable {
      myJComponent.requestFocus();
    }
  }

  public String getHtml() {
    JBScrollPane scrollPane = robot().finder().findByType(target(), JBScrollPane.class);
    final JLabel errorMessage = robot().finder().findByType(scrollPane, JLabel.class);
    return GuiActionRunner.execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return errorMessage.getText();
      }
    });
  }

  public String getMessage() {
    return TextFormat.HTML.convertTo(getHtml(), TextFormat.TEXT).trim();
  }
}
