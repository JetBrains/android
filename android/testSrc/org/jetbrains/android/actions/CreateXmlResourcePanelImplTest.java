/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.actions;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.google.common.truth.Truth;
import com.intellij.openapi.module.Module;
import javax.swing.JComponent;
import javax.swing.JTextField;
import org.jetbrains.android.AndroidTestCase;

public final class CreateXmlResourcePanelImplTest extends AndroidTestCase {

  public void testFocusedValueFieldWhenResourceNameIsGivenForString() {
    testFocusedValueFieldWhenResourceNameIsGiven(myModule, "string_name", ResourceType.STRING);
  }

  public void testFocusedValueFieldWhenResourceNameIsGivenForColor() {
    testFocusedValueFieldWhenResourceNameIsGiven(myModule, "color_name", ResourceType.COLOR);
  }

  public void testFocusedNameFieldWhenResourceValueIsGivenForString() {
    testFocusedNameFieldWhenResourceValueIsGiven(myModule, "string_value", ResourceType.STRING);
  }

  public void testFocusedNameFieldWhenResourceValueIsGivenForColor() {
    testFocusedNameFieldWhenResourceValueIsGiven(myModule, "#AFA", ResourceType.COLOR);
  }

  private static void testFocusedNameFieldWhenResourceValueIsGiven(Module module, String resourceValue, ResourceType type) {
    CreateXmlResourcePanelImpl xmlResourcePanel = new CreateXmlResourcePanelImpl(module,
                                                                                 type,
                                                                                 ResourceFolderType.VALUES,
                                                                                 null,
                                                                                 resourceValue,
                                                                                 true,
                                                                                 true,
                                                                                 true,
                                                                                 null,
                                                                                 null,
                                                                                 validatorModule -> IdeResourceNameValidator
                                                                                   .forResourceName(type));
    JComponent focusComponent = xmlResourcePanel.getPreferredFocusedComponent();
    Truth.assertThat(focusComponent).isInstanceOf(JTextField.class);
    Truth.assertThat(((JTextField)focusComponent).getText()).isEmpty();

    Truth.assertThat(xmlResourcePanel.getValue()).isEqualTo(resourceValue);
    Truth.assertThat(xmlResourcePanel.getResourceName()).isEmpty(); // If only the name is empty, then it's likely the focused component.
  }

  private static void testFocusedValueFieldWhenResourceNameIsGiven(Module module, String resourceName, ResourceType type) {
    CreateXmlResourcePanelImpl xmlResourcePanel = new CreateXmlResourcePanelImpl(module,
                                                                                 type,
                                                                                 ResourceFolderType.VALUES,
                                                                                 resourceName,
                                                                                 null,
                                                                                 true,
                                                                                 true,
                                                                                 true,
                                                                                 null,
                                                                                 null,
                                                                                 validatorModule -> IdeResourceNameValidator
                                                                                   .forResourceName(type));
    JComponent focusComponent = xmlResourcePanel.getPreferredFocusedComponent();
    Truth.assertThat(focusComponent).isInstanceOf(JTextField.class);
    Truth.assertThat(((JTextField)focusComponent).getText()).isEmpty();

    Truth.assertThat(xmlResourcePanel.getValue()).isEmpty(); // If only the value is empty, then it's likely the focused component.
    Truth.assertThat(xmlResourcePanel.getResourceName()).isEqualTo(resourceName);
  }
}