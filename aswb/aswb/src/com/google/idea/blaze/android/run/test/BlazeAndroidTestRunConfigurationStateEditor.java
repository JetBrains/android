/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.android.run.test;

import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_ALL_IN_MODULE;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_CLASS;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_METHOD;

import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethodComboEntry;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.EditorTextField;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRadioButton;

/** Editor for android test run configs. */
class BlazeAndroidTestRunConfigurationStateEditor implements RunConfigurationStateEditor {
  private final RunConfigurationStateEditor commonStateEditor;

  private JRadioButton allInPackageButton;
  private JRadioButton classButton;
  private JRadioButton testMethodButton;
  private JRadioButton allInTargetButton;
  private LabeledComponent<EditorTextField> packageComponent;
  private LabeledComponent<EditorTextField> classComponent;
  private LabeledComponent<EditorTextField> methodComponent;
  private Box mainContainer;
  private LabeledComponent<EditorTextField> runnerComponent;
  private JComboBox<AndroidTestLaunchMethodComboEntry> launchMethodComboBox;
  private final JRadioButton[] testingType2RadioButton = new JRadioButton[4];

  private boolean componentEnabled = true;

  BlazeAndroidTestRunConfigurationStateEditor(
      RunConfigurationStateEditor commonStateEditor, Project project) {
    this.commonStateEditor = commonStateEditor;
    doSetupUI(project);

    packageComponent.setComponent(new EditorTextField());

    classComponent.setComponent(new EditorTextField());

    runnerComponent.setComponent(new EditorTextField());

    methodComponent.setComponent(new EditorTextField());

    addTestingType(BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_TARGET, allInTargetButton);
    addTestingType(TEST_ALL_IN_PACKAGE, allInPackageButton);
    addTestingType(TEST_CLASS, classButton);
    addTestingType(TEST_METHOD, testMethodButton);
  }

  private void addTestingType(final int type, JRadioButton button) {
    testingType2RadioButton[type] = button;
    button.addActionListener(e -> updateLabelComponents(type));
  }

  private void updateButtonsAndLabelComponents(int type) {
    allInTargetButton.setSelected(type == TEST_ALL_IN_MODULE);
    allInPackageButton.setSelected(type == TEST_ALL_IN_PACKAGE);
    classButton.setSelected(type == TEST_CLASS);
    testMethodButton.setSelected(type == TEST_METHOD);
    updateLabelComponents(type);
  }

  private void updateLabelComponents(int type) {
    packageComponent.setVisible(type == TEST_ALL_IN_PACKAGE);
    classComponent.setVisible(type == TEST_CLASS || type == TEST_METHOD);
    methodComponent.setVisible(type == TEST_METHOD);
  }

  private void doSetupUI(Project project) throws ClassCastException {
    // Test scope
    allInTargetButton = new JRadioButton();
    allInTargetButton.setText("All in test target");

    allInPackageButton = new JRadioButton();
    allInPackageButton.setText("All in package");

    classButton = new JRadioButton();
    classButton.setText("All in class");

    testMethodButton = new JRadioButton();
    testMethodButton.setText("Single test method");

    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(allInPackageButton);
    buttonGroup.add(classButton);
    buttonGroup.add(testMethodButton);
    buttonGroup.add(allInTargetButton);

    Box testScopeOptionBox =
        UiUtil.createHorizontalBox(
            0,
            allInTargetButton,
            Box.createHorizontalStrut(10),
            allInPackageButton,
            Box.createHorizontalStrut(10),
            classButton,
            Box.createHorizontalStrut(10),
            testMethodButton,
            Box.createHorizontalGlue());

    classComponent = new LabeledComponent<>();
    classComponent.setLabelLocation("West");
    classComponent.setText("Class");

    methodComponent = new LabeledComponent<>();
    methodComponent.setLabelLocation("West");
    methodComponent.setText("Method");

    packageComponent = new LabeledComponent<>();
    packageComponent.setLabelLocation("West");
    packageComponent.setText("Package");

    Box testScopeBox =
        UiUtil.createBox(testScopeOptionBox, packageComponent, classComponent, methodComponent);
    testScopeBox.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Test scope:"));

    // Specific instrumentation test runner (optional)
    runnerComponent = new LabeledComponent<>();
    runnerComponent.setLabelLocation("west");
    runnerComponent.setText("Instrumentation class:");

    // Launch method:
    JLabel launchMethodLabel = new JLabel("Launch method:");
    launchMethodComboBox =
        new ComboBox<>(BlazeAndroidTestLaunchMethodsProvider.getAllLaunchMethods(project));

    mainContainer =
        UiUtil.createBox(
            UiUtil.createHorizontalBox(0, launchMethodLabel, launchMethodComboBox),
            testScopeBox,
            runnerComponent);
  }

  private int getTestingType() {
    for (int i = 0, myTestingType2RadioButtonLength = testingType2RadioButton.length;
        i < myTestingType2RadioButtonLength;
        i++) {
      JRadioButton button = testingType2RadioButton[i];
      if (button.isSelected()) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void applyEditorTo(RunConfigurationState genericState) {
    BlazeAndroidTestRunConfigurationState state =
        (BlazeAndroidTestRunConfigurationState) genericState;
    commonStateEditor.applyEditorTo(state.getCommonState());

    state.setLaunchMethod(
        ((AndroidTestLaunchMethodComboEntry) launchMethodComboBox.getSelectedItem()).launchMethod);

    state.setTestingType(getTestingType());
    state.setClassName(classComponent.getComponent().getText());
    state.setMethodName(methodComponent.getComponent().getText());
    state.setPackageName(packageComponent.getComponent().getText());
    state.setInstrumentationRunnerClass(runnerComponent.getComponent().getText());
  }

  @Override
  public void resetEditorFrom(RunConfigurationState genericState) {
    BlazeAndroidTestRunConfigurationState state =
        (BlazeAndroidTestRunConfigurationState) genericState;
    commonStateEditor.resetEditorFrom(state.getCommonState());

    for (int i = 0; i < launchMethodComboBox.getItemCount(); ++i) {
      if (launchMethodComboBox.getItemAt(i).launchMethod.equals(state.getLaunchMethod())) {
        launchMethodComboBox.setSelectedIndex(i);
        break;
      }
    }
    updateButtonsAndLabelComponents(state.getTestingType());
    packageComponent.getComponent().setText(state.getPackageName());
    classComponent.getComponent().setText(state.getClassName());
    methodComponent.getComponent().setText(state.getMethodName());
    runnerComponent.getComponent().setText(state.getInstrumentationRunnerClass());
  }

  @Override
  public JComponent createComponent() {
    return UiUtil.createBox(commonStateEditor.createComponent(), mainContainer);
  }

  @Override
  public void setComponentEnabled(boolean enabled) {
    componentEnabled = enabled;
    updateEnabledState();
  }

  private void updateEnabledState() {
    commonStateEditor.setComponentEnabled(componentEnabled);
    allInPackageButton.setEnabled(componentEnabled);
    classButton.setEnabled(componentEnabled);
    testMethodButton.setEnabled(componentEnabled);
    allInTargetButton.setEnabled(componentEnabled);
    packageComponent.setEnabled(componentEnabled);
    classComponent.setEnabled(componentEnabled);
    methodComponent.setEnabled(componentEnabled);
    runnerComponent.setEnabled(componentEnabled);
    launchMethodComboBox.setEnabled(componentEnabled);
    for (JComponent button : testingType2RadioButton) {
      if (button != null) {
        button.setEnabled(componentEnabled);
      }
    }
  }
}
