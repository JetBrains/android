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
package com.android.tools.idea.wizard;

import com.android.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;
import static com.android.tools.idea.wizard.ScopedStateStore.unwrap;

/**
 *
 */
public class DummyWizardForTesting extends AnAction {
  @VisibleForTesting
  public static void showDummyUIForTestingPurposesOnly() {
    DynamicWizard myWizard = new DummyDynamicWizard(null, null, "TestWizard");
    DynamicWizardPath myPath1 = new DummyDynamicWizardPath("TestPath1");
    DynamicWizardPath myPath2 = new DummyDynamicWizardPath("TestPath2");
    DynamicWizardStep myStep1 = new DummyDynamicWizardStep("TestStep1");
    DynamicWizardStep myStep2 = new DummyDynamicWizardStep("TestStep2");
    DynamicWizardStep myStep3 = new DummyDynamicWizardStep("TestStep3");
    DynamicWizardStep myStep4 = new DummyDynamicWizardStep("TestStep4");

    myPath1.addStep(myStep1);
    myPath1.addStep(myStep2);
    myPath2.addStep(myStep3);
    myPath2.addStep(myStep4);
    myWizard.addPath(myPath1);
    myWizard.addPath(myPath2);

    myWizard.init();
    myWizard.show();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    showDummyUIForTestingPurposesOnly();
  }

  public static class DummyDynamicWizard extends DynamicWizard {

    public DummyDynamicWizard(@Nullable Project project, @Nullable Module module, @NotNull String name) {
      super(project, module, name);
    }

    @Override
    public void performFinishingActions() {

    }

    @Override
    protected void init() {
      super.init();
    }
  }

  public static class DummyDynamicWizardPath extends DynamicWizardPath {

    protected final Key<Boolean> VISIBLE_KEY;
    protected final Key<Boolean> REQUIRED_KEY;
    protected final Key<String> DERIVED_KEY;
    protected final Key<Boolean> VALID_KEY;
    private String myName;

    public DummyDynamicWizardPath(@NotNull String name) {
      myName = name;
      VALID_KEY = myState.createKey(getPathName() + ":inputValue", Boolean.class);
      DERIVED_KEY = myState.createKey(getPathName() + ":derivedValue", String.class);
      VISIBLE_KEY = myState.createKey(getPathName() + ":isVisible", Boolean.class);
      REQUIRED_KEY = myState.createKey(getPathName() + ":isRequired", Boolean.class);
    }

    @Override
    public void deriveValues(Set<Key> modified) {
      myState.put(DERIVED_KEY, "derived!");
    }

    @Override
    public boolean validate() {
      Boolean valid = unwrap(myState.get(VALID_KEY));
      String derivedString = unwrap(myState.get(DERIVED_KEY));
      if (valid != null) {
        return valid;
      } else {
        return derivedString != null && "derived!".equals(derivedString);
      }
    }

    @Override
    public boolean isPathVisible() {
      Boolean visible = unwrap(myState.get(VISIBLE_KEY));
      return visible == null || visible;
    }

    @Override
    public boolean isPathRequired() {
      Boolean required = unwrap(myState.get(REQUIRED_KEY));
      return required == null || required;
    }

    @NotNull
    @Override
    public String getPathName() {
      return myName;
    }

    @Override
    public void performFinishingActions() {

    }
  }

  public static class DummyDynamicWizardStep extends DynamicWizardStep {

    protected final Key<String> INPUT_KEY;
    protected final Key<Boolean> VISIBLE_KEY;
    protected final Key<String> DERIVED_KEY;
    protected final Key<Boolean> VALID_KEY;

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
      myInputField.setPreferredSize(new Dimension(300, 30));
      myDerivedField.setPreferredSize(new Dimension(300, 30));
      myPanel.add(mySecondPanel, BorderLayout.CENTER);
      myPanel.add(myMessageLabel, BorderLayout.SOUTH);

      myMessageLabel.setText("Hello World!");
      register(INPUT_KEY, myInputField);
      register(DERIVED_KEY, myDerivedField);
      registerValueDeriver(DERIVED_KEY, new ValueDeriver<String>() {
        @Nullable
        @Override
        public Set<Key> getTriggerKeys() {
          Set<Key> filterKeys = new HashSet<Key>(1);
          filterKeys.add(INPUT_KEY);
          return filterKeys;
        }

        @NotNull
        @Override
        public String deriveValue(ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
          return unwrap(state.get(INPUT_KEY)) + "_derived!";
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
      Boolean visible = unwrap(myState.get(VISIBLE_KEY));
      return visible == null || visible;
    }

    @Override
    public boolean validate() {
      String inputString = unwrap(myState.get(INPUT_KEY));
      String derivedString = unwrap(myState.get(DERIVED_KEY));
      Boolean validBool = unwrap(myState.get(VALID_KEY));
      if (inputString != null) {
        return derivedString != null && derivedString.equals(inputString + "_derived!");
      } else {
        return validBool == null || validBool;
      }
    }

    @NotNull
    @Override
    public JComponent getComponent() {
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
  }
}
