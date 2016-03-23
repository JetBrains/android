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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.designer.propertyTable.PropertyTablePanel;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;

/**
 * Fixture representing the property sheet in an associated layout editor
 */
public class PropertySheetFixture {
  private final Robot myRobot;
  private final LayoutEditorFixture myEditorFixture;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final AndroidDesignerEditorPanel myPanel;
  private final PropertyTablePanel myPropertyTablePanel;

  public PropertySheetFixture(@NotNull Robot robot, @NotNull LayoutEditorFixture editorFixture,
                              @NotNull AndroidDesignerEditorPanel panel) {
    myRobot = robot;
    myEditorFixture = editorFixture;
    myPanel = panel;

    myPropertyTablePanel = waitUntilFound(myRobot, GuiTests.matcherForType(PropertyTablePanel.class));
  }

  @Nullable
  public PropertyFixture findProperty(@NotNull String name) {
    List<LayoutEditorComponentFixture> selection = myEditorFixture.getSelection();
    if (selection.isEmpty()) {
      return null;
    }
    LayoutEditorComponentFixture selected = selection.get(0);
    Property property = PropertyTable.findProperty(selected.getComponent().getProperties(), name);
    if (property != null) {
      return new PropertyFixture(myRobot, this, selected, property);
    }

    return null;
  }

  @NotNull
  PropertyTablePanel getPropertyTablePanel() {
    return myPropertyTablePanel;
  }
}
