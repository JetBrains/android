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
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.theme.EditorTextFieldFixture;
import com.intellij.androidstudio.actions.CreateFileFromTemplateDialog;
import com.intellij.androidstudio.actions.CreateFileFromTemplateDialog.Visibility;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.ui.EditorTextField;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JRadioButtonFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
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
    // TODO: Replace this with a fixture.
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
    EditorTextFieldFixture.findByLabel(robot(), target(), "Package:").replaceText(newPackage);
    return this;
  }

  @NotNull
  public CreateFileFromTemplateDialogFixture waitForErrorMessageToAppear(@NotNull final String errorMessage) {
    Wait.minutes(2).expecting("an error message to appear").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        try {
          GuiTests.findLabelByText(CreateFileFromTemplateDialogFixture.this, errorMessage);
          return true;
        }
        catch (ComponentLookupException e) {
          return false;
        }
        catch (Exception e) {
          return false;
        }
      }
    });
    return this;
  }

  @Nonnull
  public <T extends Component> T find(@Nullable String name, @Nonnull Class<T> type, @NotNull ComponentVisibility visibility) {
    return robot().finder().findByName(target(), name, type, visibility.equals(ComponentVisibility.VISIBLE));
  }

  public void clickOk() {
    focus();
    GuiTests.findAndClickOkButton(this);
  }

  public void setVisibility(Visibility visibility) {
    String buttonName = visibility.equals(Visibility.PUBLIC) ? "public_radio_button" : "package_private_radio_button";
    JRadioButtonFixture visibilityButton = new JRadioButtonFixture(robot(), buttonName);
    visibilityButton.setSelected(true);
  }

  public void setModifier(Modifier modifier) {
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

  public JCheckBoxFixture findCheckBox(@NotNull String name) {
    return new JCheckBoxFixture(robot(), name);
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
