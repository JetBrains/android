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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.intellij.designer.model.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.ThrowableComputable;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Fixture representing a property in the property sheet
 */
public class PropertyFixture {
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final Robot myRobot;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final PropertySheetFixture myPropertySheetFixture;
  private final Property myProperty;
  private final LayoutEditorComponentFixture myComponent;

  public PropertyFixture(@NotNull Robot robot, @NotNull PropertySheetFixture propertySheetFixture,
                         @NotNull LayoutEditorComponentFixture component, @NotNull Property property) {
    myRobot = robot;
    myPropertySheetFixture = propertySheetFixture;
    myComponent = component;
    myProperty = property;
  }

  /**
   * Requires the property display name to be the given name
   */
  public PropertyFixture requireDisplayName(@NotNull String name) throws Exception {
    assertEquals(name, myProperty.getName());
    return this;
  }

  /**
   * Requires the property value to be the given value
   */
  public PropertyFixture requireValue(@NotNull String value) throws Exception {
    assertEquals(value, getValue());
    return this;
  }

  /**
   * Reads the current value from the property
   */
  @SuppressWarnings("unchecked")
  @NotNull
  public String getValue() throws Exception {
    // TODO: Read via the UI property widget instead
    return ApplicationManager.getApplication().runReadAction(new ThrowableComputable<String,Exception>() {
      @Override
      public String compute() throws Exception {
        Object value = myProperty.getValue(myComponent.getComponent());
        return value == null ? "<null>" : value.toString();
      }
    });
  }

  /** Types in the given value into the property */
  @SuppressWarnings("unchecked")
  public void enterValue(@NotNull final String value) {
    // TODO: This should use the robot on the *UI* in the property sheet to edit the value instead!
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        //noinspection ConstantConditions
        WriteCommandAction.runWriteCommandAction(null, new Runnable() {
          @Override
          public void run() {
            try {
              myProperty.setValue(myComponent.getComponent(), value);
            }
            catch (Exception e) {
              fail(e.toString());
            }
          }
        });
      }
    });
  }
}
