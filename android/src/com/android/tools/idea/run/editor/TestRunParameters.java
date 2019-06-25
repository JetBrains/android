/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run.editor;

import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_ALL_IN_MODULE;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_CLASS;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_METHOD;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.expressions.bool.BooleanExpressions;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.run.ConfigurationSpecificEditor;
import com.android.tools.idea.testartifacts.instrumented.AndroidInheritingClassBrowser;
import com.android.tools.idea.testartifacts.instrumented.AndroidInheritingClassVisibilityChecker;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestClassBrowser;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestClassVisibilityChecker;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.MethodListDlg;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import java.awt.Component;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

public class TestRunParameters implements ConfigurationSpecificEditor<AndroidTestRunConfiguration> {
  private JRadioButton myAllInPackageTestButton;
  private JRadioButton myClassTestButton;
  private JRadioButton myMethodTestButton;
  private JRadioButton myAllInModuleTestButton;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myTestPackageComponent;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myTestClassComponent;
  private LabeledComponent<TextFieldWithBrowseButton> myTestMethodComponent;
  private JPanel myPanel;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myInstrumentationClassComponent;
  private JBLabel myLabelTest;
  private LabeledComponent<EditorTextField> myInstrumentationArgsComponent;

  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;

  private final BindingsManager myBindingsManager;
  private final SelectedRadioButtonProperty<Integer> mySelectedTestType;

  public TestRunParameters(Project project, ConfigurationModuleSelector moduleSelector) {
    myProject = project;
    myModuleSelector = moduleSelector;

    myBindingsManager = new BindingsManager();
    mySelectedTestType = new SelectedRadioButtonProperty<>(
      TEST_ALL_IN_MODULE, new Integer[]{TEST_ALL_IN_MODULE, TEST_ALL_IN_PACKAGE, TEST_CLASS, TEST_METHOD},
      myAllInModuleTestButton, myAllInPackageTestButton, myClassTestButton, myMethodTestButton);

    myBindingsManager.bind(new VisibleProperty(myTestPackageComponent), mySelectedTestType.isEqualTo(TEST_ALL_IN_PACKAGE));
    myBindingsManager.bind(new VisibleProperty(myTestClassComponent),
                           BooleanExpressions.any(mySelectedTestType.isEqualTo(TEST_CLASS),
                                                  mySelectedTestType.isEqualTo(TEST_METHOD)));
    myBindingsManager.bind(new VisibleProperty(myTestMethodComponent), mySelectedTestType.isEqualTo(TEST_METHOD));

    EditorTextFieldWithBrowseButton testPackageEditorText = new EditorTextFieldWithBrowseButton(project, false);
    new BrowseModuleValueActionListener<EditorTextField>(myProject) {
      @Override
      protected String showDialog() {
        Module module = myModuleSelector.getModule();
        if (module == null) {
          Messages.showErrorDialog(myPanel, ExecutionBundle.message("module.not.specified.error.text"));
          return null;
        }
        final PackageChooserDialog dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"), module);
        dialog.selectPackage(myTestPackageComponent.getComponent().getText());
        dialog.show();
        final PsiPackage aPackage = dialog.getSelectedPackage();
        return aPackage != null ? aPackage.getQualifiedName() : null;
      }
    }.setField(testPackageEditorText);
    myTestPackageComponent.setComponent(testPackageEditorText);

    EditorTextFieldWithBrowseButton testClassEditorText =
      new EditorTextFieldWithBrowseButton(project, true, new AndroidTestClassVisibilityChecker(moduleSelector));
    new AndroidTestClassBrowser<EditorTextField>(project, moduleSelector, AndroidBundle.message("android.browse.test.class.dialog.title"), false).setField(testClassEditorText);
    myTestClassComponent.setComponent(testClassEditorText);

    TextFieldWithBrowseButton testMethodEditorText = new TextFieldWithBrowseButton();
    new BrowseModuleValueActionListener<JTextField>(myProject) {
      @Override
      protected String showDialog() {
        final String className = myTestClassComponent.getComponent().getText();
        if (className.trim().isEmpty()) {
          Messages.showMessageDialog(getField(), ExecutionBundle.message("set.class.name.message"),
                                     ExecutionBundle.message("cannot.browse.method.dialog.title"), Messages.getInformationIcon());
          return null;
        }
        final PsiClass testClass = myModuleSelector.findClass(className);
        if (testClass == null) {
          Messages.showMessageDialog(getField(), ExecutionBundle.message("class.does.not.exists.error.message", className),
                                     ExecutionBundle.message("cannot.browse.method.dialog.title"), Messages.getInformationIcon());
          return null;
        }
        final MethodListDlg dialog = new MethodListDlg(testClass, new JUnitUtil.TestMethodFilter(testClass), getField());
        if (dialog.showAndGet()) {
          final PsiMethod method = dialog.getSelected();
          if (method != null) {
            return method.getName();
          }
        }
        return null;
      }
    }.setField(testMethodEditorText);
    myTestMethodComponent.setComponent(testMethodEditorText);

    EditorTextFieldWithBrowseButton instrClassEditorText =
      new EditorTextFieldWithBrowseButton(
        project,
        true,
        new AndroidInheritingClassVisibilityChecker(
          myProject, moduleSelector, AndroidUtils.INSTRUMENTATION_RUNNER_BASE_CLASS));
    new AndroidInheritingClassBrowser<EditorTextField>(
      project, moduleSelector, AndroidUtils.INSTRUMENTATION_RUNNER_BASE_CLASS,
      AndroidBundle.message("android.browse.instrumentation.class.dialog.title"),
      true).setField(instrClassEditorText);
    myInstrumentationClassComponent.setComponent(instrClassEditorText);

    myInstrumentationArgsComponent.setComponent(new EditorTextField());

    // TODO(b/37132226): Revive instrumentation class runner and args in gradle project with revised UI.
    myInstrumentationClassComponent.setVisible(!GradleProjectInfo.getInstance(myProject).isBuildWithGradle());
    myInstrumentationArgsComponent.setVisible(!GradleProjectInfo.getInstance(myProject).isBuildWithGradle());
  }

  @Override
  public void applyTo(AndroidTestRunConfiguration configuration) {
    configuration.TESTING_TYPE = mySelectedTestType.get();
    configuration.CLASS_NAME = myTestClassComponent.getComponent().getText();
    configuration.METHOD_NAME = myTestMethodComponent.getComponent().getText();
    configuration.PACKAGE_NAME = myTestPackageComponent.getComponent().getText();
    configuration.INSTRUMENTATION_RUNNER_CLASS = myInstrumentationClassComponent.getComponent().getText();
    configuration.EXTRA_OPTIONS = myInstrumentationArgsComponent.getComponent().getText().trim();
  }

  @Override
  public void resetFrom(AndroidTestRunConfiguration configuration) {
    mySelectedTestType.set(configuration.TESTING_TYPE);
    myTestPackageComponent.getComponent().setText(configuration.PACKAGE_NAME);
    myTestClassComponent.getComponent().setText(configuration.CLASS_NAME);
    myTestMethodComponent.getComponent().setText(configuration.METHOD_NAME);
    myInstrumentationClassComponent.getComponent().setText(configuration.INSTRUMENTATION_RUNNER_CLASS);
    myInstrumentationArgsComponent.getComponent().setText(configuration.EXTRA_OPTIONS);
  }

  @Override
  public Component getComponent() {
    return myPanel;
  }

  @Override
  public void dispose() {
    myBindingsManager.releaseAll();
  }
}
