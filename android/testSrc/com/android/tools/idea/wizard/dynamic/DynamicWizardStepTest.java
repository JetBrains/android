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
package com.android.tools.idea.wizard.dynamic;

import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link DynamicWizardStep}
 */
public class DynamicWizardStepTest extends TestCase {
  private DummyDynamicWizardStep myStep;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myStep = new DummyDynamicWizardStep("TestStep");
    myStep.onEnterStep();
  }

  public void testUpdateModelFromUI() throws Exception {

  }

  public void testCanGoNext() throws Exception {
    assertTrue(myStep.canGoNext());
    myStep.myState.put(myStep.INPUT_KEY, "");
    myStep.myState.put(myStep.DERIVED_KEY, "not valid");
    // Update should have been invoked automatically, thus invalidating the step
    assertFalse(myStep.canGoNext());
    // Test inserting value into the state
    myStep.myState.put(myStep.INPUT_KEY, "Hello again");
    assertTrue(myStep.canGoNext());

    // Return to not valid
    myStep.myState.put(myStep.DERIVED_KEY, "not valid");
    assertFalse(myStep.canGoNext());

    myStep.myInputField.setText("Hello once more");
    // Update and deriver should have fired here
    assertTrue(myStep.canGoNext());
  }

  public void testVisibility() throws Exception {
    assertTrue(myStep.isStepVisible());
    myStep.myState.put(myStep.VISIBLE_KEY, false);
    assertFalse(myStep.isStepVisible());
    myStep.myState.put(myStep.VISIBLE_KEY, true);
    assertTrue(myStep.isStepVisible());
  }

  public void testCanGoPrev() throws Exception {
    assertTrue(myStep.canGoPrevious());
  }

  public void testSetErrorHtml() throws Exception {
    myStep.setErrorHtml("Testing");
    assertEquals("<html>Testing</html>", myStep.getMessageLabel().getText());
    myStep.setErrorHtml("<html><h1>Testing</h1></html>");
    assertEquals("<html><h1>Testing</h1></html>", myStep.getMessageLabel().getText());
  }

  public static class DummyDynamicWizardStep extends DynamicWizardStep {

    protected final ScopedStateStore.Key<String> INPUT_KEY;
    protected final ScopedStateStore.Key<Boolean> VISIBLE_KEY;
    protected final ScopedStateStore.Key<String> DERIVED_KEY;
    protected final ScopedStateStore.Key<Boolean> VALID_KEY;

    private JLabel myMessageLabel = new JLabel("Messages Go Here");
    private JPanel myPanel = new JPanel(new BorderLayout(5, 5));
    private JTextField myInputField = new JTextField();
    private String myName;
    private JTextField myDerivedField = new JTextField();

    public DummyDynamicWizardStep(@NotNull String name) {
      myName = name;
      INPUT_KEY = myState.createKey(getStepName() + ":inputValue", String.class);
      DERIVED_KEY = myState.createKey(getStepName() + ":derivedValue", String.class);
      VISIBLE_KEY = myState.createKey(getStepName() + ":isVisible", Boolean.class);
      VALID_KEY = myState.createKey(getStepName() + ":isValid", Boolean.class);
    }

    @Override
    public void init() {
      JPanel mySecondPanel = new JPanel();
      mySecondPanel.add(myInputField);
      mySecondPanel.add(myDerivedField);
      myDerivedField.setEditable(false);
      myInputField.setPreferredSize(JBUI.size(300, 30));
      myDerivedField.setPreferredSize(JBUI.size(300, 30));
      myPanel.add(mySecondPanel, BorderLayout.CENTER);
      myPanel.add(myMessageLabel, BorderLayout.SOUTH);

      myMessageLabel.setText("Hello World!");
      register(INPUT_KEY, myInputField);
      register(DERIVED_KEY, myDerivedField);
      myState.remove(INPUT_KEY);
      registerValueDeriver(DERIVED_KEY, new ValueDeriver<String>() {
        @Nullable
        @Override
        public Set<ScopedStateStore.Key<?>> getTriggerKeys() {
          Set<ScopedStateStore.Key<?>> filterKeys = new HashSet<>(1);
          filterKeys.add(INPUT_KEY);
          return filterKeys;
        }

        @NotNull
        @Override
        public String deriveValue(@NotNull ScopedStateStore state, ScopedStateStore.Key changedKey, @Nullable String currentValue) {
          return state.get(INPUT_KEY) + "_derived!";
        }
      });
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AndroidIcons.Wizards.NewModuleSidePanel;
    }

    @Override
    public boolean isStepVisible() {
      Boolean visible = myState.get(VISIBLE_KEY);
      return visible == null || visible;
    }

    @Override
    public boolean validate() {
      String inputString = myState.get(INPUT_KEY);
      String derivedString = myState.get(DERIVED_KEY);
      Boolean validBool = myState.get(VALID_KEY);
      if (inputString != null) {
        return derivedString != null && derivedString.equals(inputString + "_derived!");
      } else {
        return validBool == null || validBool;
      }
    }

    @NotNull
    @Override
    public JComponent createStepBody() {
      return myPanel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myInputField;
    }

    @NotNull
    @Override
    public JLabel getMessageLabel() {
      return myMessageLabel;
    }

    @NotNull
    @Override
    public String getStepName() {
      return myName;
    }

    @NotNull
    @Override
    protected String getStepTitle() {
      return getStepName();
    }

    @Nullable
    @Override
    protected String getStepDescription() {
      return null;
    }
  }
}
