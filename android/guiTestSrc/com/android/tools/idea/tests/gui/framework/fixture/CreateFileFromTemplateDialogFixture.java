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
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.ide.actions.as.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.as.CreateFileFromTemplateDialog.Visibility;
import com.intellij.ui.EditorTextField;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
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
  public CreateFileFromTemplateDialogFixture selectKind(@NotNull final Kind kind) {
    final TemplateKindCombo kindBox = robot().finder().findByType(target(), TemplateKindCombo.class);
    GuiActionRunner.execute(new SelectFieldTask(kindBox));
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        kindBox.setSelectedName(kind.getTemplateName());
      }
    });

    return this;
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture setPackage(@NotNull final String newPackage) {
    final EditorTextField packageField = robot().finder().findByLabel(target(), "Package:", EditorTextField.class);
    GuiActionRunner.execute(new SelectFieldTask(packageField));
    GuiTests.setText(packageField, newPackage);
    return this;
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture waitForErrorMessageToAppear() {
    pause(new Condition("an error message to appear") {
      @Override
      public boolean test() {
        try {
          robot().finder().findByName(target(), "error_text_label", JLabel.class);
          return true;
        }
        catch (ComponentLookupException e) {
          return false;
        }
        catch (Exception e) {
          return false;
        }
      }
    }, GuiTests.SHORT_TIMEOUT);
    return this;
  }

  public JCheckBox getAbstractCheckBox(ComponentVisibility visibility) {
    return robot().finder().findByName(target(), "abstract_check_box", JCheckBox.class, visibility.equals(ComponentVisibility.VISIBLE));
  }

  public JCheckBox getFinalCheckBox(ComponentVisibility visibility) {
    return robot().finder().findByName(target(), "final_check_box", JCheckBox.class, visibility.equals(ComponentVisibility.VISIBLE));
  }

  public JCheckBox getOverridesCheckBox(ComponentVisibility visibility) {
    return robot().finder().findByName(target(), "overrides_check_box", JCheckBox.class, visibility.equals(ComponentVisibility.VISIBLE));
  }

  public void clickAddInterfaceButton() {
    focus();
    GuiTests.findAndClickButton(this, "+");
  }

  public void clickOk() {
    focus();
    GuiTests.findAndClickOkButton(this);
  }

  public void setVisibility(Visibility visibility) {
    final JRadioButton visibilityButton = visibility.equals(Visibility.PUBLIC) ?
                                          robot().finder().findByName(target(), "public_radio_button", JRadioButton.class) :
                                          robot().finder().findByName(target(), "package_private_radio_button", JRadioButton.class);
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        visibilityButton.setSelected(true);
      }
    });
  }

  public void setInterface(@NotNull String iface) {
    EditorTextField interfacesField = robot().finder().findByLabel(target(), "Interface(s):", EditorTextField.class);
    GuiActionRunner.execute(new SelectFieldTask(interfacesField));
    GuiTests.setText(interfacesField, iface);
  }

  public void setSuperclass(@NotNull String superclass) {
    EditorTextField superclassField = robot().finder().findByLabel(target(), "Superclass:", EditorTextField.class);
    GuiActionRunner.execute(new SelectFieldTask(superclassField));
    GuiTests.setText(superclassField, superclass);
  }

  public enum Kind {
    CLASS("class", "ASClass"),
    ENUM("enum", "ASEnum"),
    INTERFACE("interface", "ASInterface"),
    ANNOTATION("@interface", "ASAnnotationType");

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
    final JLabel errorMessage = robot().finder().findByName(target(), "error_text_label", JLabel.class);
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

  public enum ComponentVisibility {
    VISIBLE,
    NOT_VISIBLE
  }
}
