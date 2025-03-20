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

import static com.android.tools.idea.run.editor.TestRunParameters.TestRunParametersToken.getModuleForPackageChooser;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_ALL_IN_MODULE;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_CLASS;
import static com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration.TEST_METHOD;

import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.expressions.bool.BooleanExpressions;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.Token;
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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.containers.ContainerUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestRunParameters implements ConfigurationSpecificEditor<AndroidTestRunConfiguration> {
  private JRadioButton myAllInPackageTestButton;
  private JRadioButton myClassTestButton;
  private JRadioButton myMethodTestButton;
  private JRadioButton myAllInModuleTestButton;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myTestPackageComponent;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myTestClassComponent;
  private LabeledComponent<SimpleEditorTextFieldWithBrowseButton> myTestMethodComponent;
  private JPanel myContentPanel;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myInstrumentationClassComponent;
  private LabeledComponent<SimpleEditorTextFieldWithBrowseButton> myInstrumentationArgsComponent;
  private LabeledComponent myTestRegexComponent;

  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;
  private final boolean canSelectInstrumentationRunnerClass;

  private final ContentWrapper myContentWrapper;
  private final BindingsManager myBindingsManager;
  private final SelectedRadioButtonProperty<Integer> mySelectedTestType;
  private final TextProperty myTestPackage;
  private final TextProperty myTestClass;
  private final TextProperty myTestMethod;
  private final TextProperty myTestRegex;
  private final TextProperty myInstrumentationClass;
  private final TextProperty myInstrumentationArgs;

  private String myUserModifiedInstrumentationExtraParams = "";

  public TestRunParameters(Project project, ConfigurationModuleSelector moduleSelector) {
    try {
      setupUI();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    myProject = project;
    myModuleSelector = moduleSelector;
    AndroidProjectSystem projectSystem = ProjectSystemUtil.getProjectSystem(project);
    TestRunParametersToken<AndroidProjectSystem> token =
      TestRunParametersToken.EP_NAME.getExtensionList().stream()
        .filter(it -> it.isApplicable(projectSystem))
        .findFirst()
        .orElse(null);
    canSelectInstrumentationRunnerClass = token == null || token.canSelectInstrumentationRunnerClass(projectSystem);

    myBindingsManager = new BindingsManager();
    mySelectedTestType = new SelectedRadioButtonProperty<>(
      TEST_ALL_IN_MODULE, new Integer[]{TEST_ALL_IN_MODULE, TEST_ALL_IN_PACKAGE, TEST_CLASS, TEST_METHOD},
      myAllInModuleTestButton, myAllInPackageTestButton, myClassTestButton, myMethodTestButton);

    myBindingsManager.bind(new VisibleProperty(myTestPackageComponent), mySelectedTestType.isEqualTo(TEST_ALL_IN_PACKAGE));
    myBindingsManager.bind(new VisibleProperty(myTestClassComponent),
                           BooleanExpressions.any(mySelectedTestType.isEqualTo(TEST_CLASS),
                                                  mySelectedTestType.isEqualTo(TEST_METHOD)));
    myBindingsManager.bind(new VisibleProperty(myTestMethodComponent), mySelectedTestType.isEqualTo(TEST_METHOD));
    myBindingsManager.bind(new VisibleProperty(myTestRegexComponent), mySelectedTestType.isEqualTo(TEST_ALL_IN_MODULE));

    EditorTextFieldWithBrowseButton testPackageEditorText = new EditorTextFieldWithBrowseButton(project, false);
    new BrowseModuleValueActionListener<EditorTextField>(myProject) {
      @Override
      protected String showDialog() {
        Module module = myModuleSelector.getModule();
        if (module == null) {
          Messages.showErrorDialog(myContentPanel, ExecutionBundle.message("module.not.specified.error.text"));
          return null;
        }
        final PackageChooserDialog dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"),
                                                                     getModuleForPackageChooser(module));
        dialog.selectPackage(myTestPackageComponent.getComponent().getText());
        dialog.show();
        final PsiPackage aPackage = dialog.getSelectedPackage();
        return aPackage != null ? aPackage.getQualifiedName() : null;
      }
    }.setField(testPackageEditorText);
    myTestPackage = new TextProperty(testPackageEditorText.getChildComponent());
    myTestPackageComponent.setComponent(testPackageEditorText);

    EditorTextFieldWithBrowseButton testClassEditorText =
      new EditorTextFieldWithBrowseButton(project, true, new AndroidTestClassVisibilityChecker(moduleSelector));
    new AndroidTestClassBrowser<EditorTextField>(project, moduleSelector, AndroidBundle.message("android.browse.test.class.dialog.title"),
                                                 false).setField(testClassEditorText);
    myTestClass = new TextProperty(testClassEditorText.getChildComponent());
    myTestClassComponent.setComponent(testClassEditorText);

    SimpleEditorTextFieldWithBrowseButton testMethodEditorText = new SimpleEditorTextFieldWithBrowseButton();
    new BrowseModuleValueActionListener<EditorTextField>(myProject) {
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
    myTestMethod = new TextProperty(testMethodEditorText.getChildComponent());
    myTestMethodComponent.setComponent(testMethodEditorText);

    EditorTextField testRegexEditorText = new EditorTextField();
    myTestRegex = new TextProperty(testRegexEditorText);
    myTestRegexComponent.setComponent(testRegexEditorText);

    EditorTextFieldWithBrowseButton instrClassEditorText = new EditorTextFieldWithBrowseButton(
      project,
      true,
      new AndroidInheritingClassVisibilityChecker(myProject, moduleSelector, AndroidUtils.INSTRUMENTATION_RUNNER_BASE_CLASS));
    new AndroidInheritingClassBrowser<EditorTextField>(
      project, moduleSelector, AndroidUtils.INSTRUMENTATION_RUNNER_BASE_CLASS,
      AndroidBundle.message("android.browse.instrumentation.class.dialog.title"),
      true).setField(instrClassEditorText);
    // Disable the instrumentation class selector component for Gradle based project because AGP doesn't allow
    // users to define multiple <instrumentation> tag in their manifest. We just show the instrumentation
    // runner specified in their gradle file as FYI.
    instrClassEditorText.setEnabled(canSelectInstrumentationRunnerClass);
    myInstrumentationClass = new TextProperty(instrClassEditorText.getChildComponent());
    myInstrumentationClassComponent.setComponent(instrClassEditorText);

    SimpleEditorTextFieldWithBrowseButton instrArgsTextField = new SimpleEditorTextFieldWithBrowseButton();
    new BrowseModuleValueActionListener<EditorTextField>(myProject) {
      @Nullable
      @Override
      protected String showDialog() {
        Module module = myModuleSelector.getModule();
        if (module == null) {
          Messages.showErrorDialog(myContentPanel, ExecutionBundle.message("module.not.specified.error.text"));
          return null;
        }
        AndroidTestExtraParamsDialog dialog = new AndroidTestExtraParamsDialog(getProject(),
                                                                               AndroidFacet.getInstance(module),
                                                                               myInstrumentationArgs.get());
        if (dialog.showAndGet()) {
          myUserModifiedInstrumentationExtraParams = dialog.getUserModifiedInstrumentationExtraParams();
          myInstrumentationArgs.set(dialog.getInstrumentationExtraParams());
          myContentWrapper.fireStateChanged();
        }
        return null;
      }
    }.setField(instrArgsTextField);
    instrArgsTextField.getChildComponent().setEnabled(false);
    myInstrumentationArgs = new TextProperty(instrArgsTextField.getChildComponent());
    myInstrumentationArgsComponent.setComponent(instrArgsTextField);

    myContentWrapper = new ContentWrapper();
    myContentWrapper.add(myContentPanel);
  }

  @Override
  public void applyTo(AndroidTestRunConfiguration configuration) {
    configuration.TESTING_TYPE = mySelectedTestType.get();
    configuration.PACKAGE_NAME = myTestPackage.get();
    configuration.CLASS_NAME = myTestClass.get();
    configuration.METHOD_NAME = myTestMethod.get();
    configuration.TEST_NAME_REGEX = myTestRegex.get();
    configuration.INSTRUMENTATION_RUNNER_CLASS = canSelectInstrumentationRunnerClass ? myInstrumentationClass.get() : "";
    configuration.EXTRA_OPTIONS = myUserModifiedInstrumentationExtraParams;
  }

  @Override
  public void resetFrom(AndroidTestRunConfiguration configuration) {
    AndroidFacet androidFacet = Optional.ofNullable(myModuleSelector.getModule())
      .map(AndroidFacet::getInstance)
      .orElse(null);

    mySelectedTestType.set(configuration.TESTING_TYPE);
    myTestPackage.set(configuration.PACKAGE_NAME);
    myTestClass.set(configuration.CLASS_NAME);
    myTestMethod.set(configuration.METHOD_NAME);
    myTestRegex.set(configuration.TEST_NAME_REGEX);
    myInstrumentationClass.set(
      canSelectInstrumentationRunnerClass
      ? configuration.INSTRUMENTATION_RUNNER_CLASS
      : AndroidTestRunConfiguration.getDefaultInstrumentationRunner(androidFacet));
    myInstrumentationArgs.set(configuration.getExtraInstrumentationOptions(androidFacet));
    myUserModifiedInstrumentationExtraParams = configuration.EXTRA_OPTIONS;
  }

  @Override
  public Component getComponent() {
    return myContentWrapper;
  }

  @Override
  public void dispose() {
    myBindingsManager.releaseAll();
  }

  private void setupUI() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(7, 6, new Insets(0, 0, 0, 0), -1, -1));
    myAllInPackageTestButton = new JRadioButton();
    myAllInPackageTestButton.setActionCommand(
      getMessageFromBundle("messages/ExecutionBundle", "jnit.configuration.all.tests.in.package.radio"));
    loadButtonText(myAllInPackageTestButton,
                              getMessageFromBundle("messages/AndroidBundle", "android.run.configuration.all.in.package.radio"));
    myContentPanel.add(myAllInPackageTestButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
    myClassTestButton = new JRadioButton();
    myClassTestButton.setActionCommand(getMessageFromBundle("messages/ExecutionBundle", "junit.configuration.test.class.radio"));
    myClassTestButton.setEnabled(true);
    myClassTestButton.setSelected(false);
    loadButtonText(myClassTestButton,
                              getMessageFromBundle("messages/AndroidBundle", "android.run.configuration.class.radio"));
    myContentPanel.add(myClassTestButton, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    myMethodTestButton = new JRadioButton();
    myMethodTestButton.setActionCommand(
      getMessageFromBundle("messages/ExecutionBundle", "junit.configuration.test.method.radio"));
    myMethodTestButton.setSelected(false);
    loadButtonText(myMethodTestButton,
                              getMessageFromBundle("messages/AndroidBundle", "android.run.configuration.method.radio"));
    myContentPanel.add(myMethodTestButton, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setHorizontalAlignment(2);
    jBLabel1.setHorizontalTextPosition(2);
    jBLabel1.setIconTextGap(4);
    loadLabelText(jBLabel1,
                             getMessageFromBundle("messages/ExecutionBundle", "junit.configuration.configure.junit.test.label"));
    myContentPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, 1, null,
                                                    null, null, 0, false));
    myAllInModuleTestButton = new JRadioButton();
    loadButtonText(myAllInModuleTestButton,
                              getMessageFromBundle("messages/AndroidBundle", "android.run.configuration.all.in.module.radio"));
    myContentPanel.add(myAllInModuleTestButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
    myTestPackageComponent = new LabeledComponent();
    myTestPackageComponent.setComponentClass("javax.swing.JPanel");
    myTestPackageComponent.setLabelLocation("West");
    myTestPackageComponent.setText(getMessageFromBundle("messages/AndroidBundle", "android.run.configuration.package.label"));
    myContentPanel.add(myTestPackageComponent,
                       new GridConstraints(1, 0, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTestClassComponent = new LabeledComponent();
    myTestClassComponent.setComponentClass("javax.swing.JPanel");
    myTestClassComponent.setLabelLocation("West");
    myTestClassComponent.setText(getMessageFromBundle("messages/AndroidBundle", "android.run.configuration.class.label"));
    myContentPanel.add(myTestClassComponent, new GridConstraints(2, 0, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
    myTestMethodComponent = new LabeledComponent();
    myTestMethodComponent.setComponentClass("javax.swing.JPanel");
    myTestMethodComponent.setLabelLocation("West");
    myTestMethodComponent.setText(getMessageFromBundle("messages/AndroidBundle", "android.run.configuration.method.label"));
    myContentPanel.add(myTestMethodComponent,
                       new GridConstraints(3, 0, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTestRegexComponent = new LabeledComponent();
    myTestRegexComponent.setComponentClass("javax.swing.JPanel");
    myTestRegexComponent.setLabelLocation("West");
    myTestRegexComponent.setText("Regex");
    myContentPanel.add(myTestRegexComponent, new GridConstraints(4, 0, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
    myInstrumentationClassComponent = new LabeledComponent();
    myInstrumentationClassComponent.setComponentClass("javax.swing.JPanel");
    myInstrumentationClassComponent.setEnabled(true);
    myInstrumentationClassComponent.setLabelLocation("West");
    myInstrumentationClassComponent.setText(
      getMessageFromBundle("messages/AndroidBundle", "android.test.run.configuration.instrumentation.label"));
    myContentPanel.add(myInstrumentationClassComponent,
                       new GridConstraints(5, 0, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myInstrumentationArgsComponent = new LabeledComponent();
    myInstrumentationArgsComponent.setComponentClass("javax.swing.JPanel");
    myInstrumentationArgsComponent.setLabelLocation("West");
    myInstrumentationArgsComponent.setText("Instrumentation arguments");
    myContentPanel.add(myInstrumentationArgsComponent,
                       new GridConstraints(6, 0, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myAllInModuleTestButton);
    buttonGroup.add(myAllInPackageTestButton);
    buttonGroup.add(myClassTestButton);
    buttonGroup.add(myMethodTestButton);
  }

  private static Method cachedGetBundleMethod = null;

  private String getMessageFromBundle(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if (cachedGetBundleMethod == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        cachedGetBundleMethod = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)cachedGetBundleMethod.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  private void loadLabelText(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  private void loadButtonText(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /**
   * Wraps content UI components and provides {@link UserActivityProviderComponent} interface.
   * <p>
   * {@link TestRunParameters} form is designed to be used in {@link SettingsEditor} and the editor
   * traverses its view hierarchy and searches for {@link UserActivityProviderComponent}. In order to notify the editor any changes
   * made in the form manually by {@link #fireStateChanged()}, we need to wrap our content by this class.
   */
  private static class ContentWrapper extends JPanel implements UserActivityProviderComponent {

    private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    private ContentWrapper() {
      super(new BorderLayout());
    }

    @Override
    public void addChangeListener(@NotNull ChangeListener changeListener) {
      myListeners.add(changeListener);
    }

    @Override
    public void removeChangeListener(@NotNull ChangeListener changeListener) {
      myListeners.remove(changeListener);
    }

    /**
     * Notifies the listeners the state has been updated.
     */
    public void fireStateChanged() {
      for (ChangeListener listener : myListeners) {
        listener.stateChanged(new ChangeEvent(this));
      }
    }
  }

  /**
   * Simplified version of {@link EditorTextFieldWithBrowseButton}.
   * <p>
   * A plain {@link EditorTextField} is used as an internal text component.
   */
  private static class SimpleEditorTextFieldWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor {
    SimpleEditorTextFieldWithBrowseButton() {
      super(new EditorTextField(), null);
    }

    @Override
    public void setText(String text) {
      getChildComponent().setText(StringUtil.notNullize(text));
    }

    @NotNull
    @Override
    public String getText() {
      return getChildComponent().getText();
    }
  }

  public interface TestRunParametersToken<P extends AndroidProjectSystem> extends Token {
    ExtensionPointName<TestRunParametersToken<AndroidProjectSystem>> EP_NAME =
      new ExtensionPointName<>("com.android.tools.idea.run.editor.testRunParametersToken");

    boolean canSelectInstrumentationRunnerClass(@NotNull P projectSystem);

    default @NotNull Module getModuleForPackageChooser(@NotNull P projectSystem, @NotNull Module module) {
      return module;
    }

    static @NotNull Module getModuleForPackageChooser(@NotNull Module module) {
      Project project = module.getProject();
      AndroidProjectSystem projectSystem = ProjectSystemUtil.getProjectSystem(project);
      return EP_NAME.getExtensionList().stream()
        .filter(it -> it.isApplicable(projectSystem))
        .findFirst()
        .map(it -> it.getModuleForPackageChooser(projectSystem, module))
        .orElse(module);
    }
  }
}
