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
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.run.ConfigurationSpecificEditor;
import com.android.tools.idea.testartifacts.instrumented.AndroidInheritingClassBrowser;
import com.android.tools.idea.testartifacts.instrumented.AndroidInheritingClassVisibilityChecker;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestClassBrowser;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestClassVisibilityChecker;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testartifacts.instrumented.EnableRetention;
import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.MethodListDlg;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
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
  private static final String RETENTION_ENABLE_TOOLTIP = "Enabling this feature instructs virtual devices to capture an Emulator " +
                                                        "snapshot when a test encounters a Java assertion failure. Snapshots are then " +
                                                        "available for you to load onto the device from the test results panel.";
  private static final String RETENTION_ENABLE_URL = "https://developer.android.com/studio/preview/features#automated-test-snapshots";
  private JRadioButton myAllInPackageTestButton;
  private JRadioButton myClassTestButton;
  private JRadioButton myMethodTestButton;
  private JRadioButton myAllInModuleTestButton;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myTestPackageComponent;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myTestClassComponent;
  private LabeledComponent<SimpleEditorTextFieldWithBrowseButton> myTestMethodComponent;
  private JPanel myContentPanel;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myInstrumentationClassComponent;
  private JBLabel myLabelTest;
  private LabeledComponent<SimpleEditorTextFieldWithBrowseButton> myInstrumentationArgsComponent;
  private LabeledComponent myMaxSnapshotsComponent;
  private JCheckBox myCompressSnapshotsCheckBox;
  private TitledSeparator myTestExecutionOptionsSeparator;
  private LabeledComponent myEnableEmulatorSnapshotsComponent;
  private JBLabel myEmulatorSnapshotsForTestFailuresHelper;
  private ComboBox<EnableRetention> myEnableEmulatorSnapshotItemsComboBox;

  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;
  private final boolean isBuildWithGradle;

  private final ContentWrapper myContentWrapper;
  private final BindingsManager myBindingsManager;
  private final SelectedRadioButtonProperty<Integer> mySelectedTestType;
  private final TextProperty myTestPackage;
  private final TextProperty myTestClass;
  private final TextProperty myTestMethod;
  private final TextProperty myInstrumentationClass;
  private final TextProperty myInstrumentationArgs;
  private final SelectedItemProperty<EnableRetention> myEnableRetention;
  private final TextProperty myMaxSnapshots;
  private final SelectedProperty myCompressSnapshots;

  private boolean myIncludeGradleExtraParams = true;
  private String myUserModifiedInstrumentationExtraParams = "";

  private void createUIComponents() {
    myEmulatorSnapshotsForTestFailuresHelper = ContextHelpLabel.createWithLink(null, RETENTION_ENABLE_TOOLTIP, "Learn more",
                                                                               () -> BrowserUtil.browse(RETENTION_ENABLE_URL));
  }

  public TestRunParameters(Project project, ConfigurationModuleSelector moduleSelector) {
    myProject = project;
    myModuleSelector = moduleSelector;
    isBuildWithGradle = GradleProjectInfo.getInstance(myProject).isBuildWithGradle();

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
          Messages.showErrorDialog(myContentPanel, ExecutionBundle.message("module.not.specified.error.text"));
          return null;
        }
        final PackageChooserDialog dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"), module);
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
    instrClassEditorText.setEnabled(!isBuildWithGradle);
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
                                                                               myInstrumentationArgs.get(),
                                                                               myIncludeGradleExtraParams);
        if (dialog.showAndGet()) {
          myIncludeGradleExtraParams = dialog.getIncludeGradleExtraParams();
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

    if (AndroidTestConfiguration.getInstance().getRUN_ANDROID_TEST_USING_GRADLE()) {
      NumberFormat maxSnapshotsFormat = NumberFormat.getIntegerInstance();
      maxSnapshotsFormat.setMinimumIntegerDigits(0);
      JFormattedTextField maxSnapshotsTextField = new JFormattedTextField(maxSnapshotsFormat);
      myMaxSnapshotsComponent.setComponent(maxSnapshotsTextField);
      myMaxSnapshots = new TextProperty(maxSnapshotsTextField);
      myCompressSnapshots = new SelectedProperty(myCompressSnapshotsCheckBox);
      myEnableEmulatorSnapshotItemsComboBox = new ComboBox<>();
      myEnableEmulatorSnapshotItemsComboBox.addItem(EnableRetention.YES);
      myEnableEmulatorSnapshotItemsComboBox.addItem(EnableRetention.NO);
      myEnableEmulatorSnapshotItemsComboBox.addItem(EnableRetention.USE_GRADLE);
      myEnableEmulatorSnapshotItemsComboBox.addItemListener(e -> {
        EnableRetention selectedItem = (EnableRetention)e.getItem();
        boolean enabled = selectedItem == EnableRetention.YES;
        myMaxSnapshotsComponent.setEnabled(enabled);
        myCompressSnapshotsCheckBox.setEnabled(enabled);
      });
      myEnableRetention = new SelectedItemProperty<>(myEnableEmulatorSnapshotItemsComboBox);
      myEnableEmulatorSnapshotItemsComboBox.setSelectedItem(EnableRetention.NO);
      myEnableEmulatorSnapshotsComponent.setComponent(myEnableEmulatorSnapshotItemsComboBox);
    } else {
      myEnableRetention = null;
      myMaxSnapshots = null;
      myCompressSnapshots = null;
      myTestExecutionOptionsSeparator.setVisible(false);
      myMaxSnapshotsComponent.setVisible(false);
      myCompressSnapshotsCheckBox.setVisible(false);
      myEnableEmulatorSnapshotsComponent.setVisible(false);
    }
  }

  @Override
  public void applyTo(AndroidTestRunConfiguration configuration) {
    configuration.TESTING_TYPE = mySelectedTestType.get();
    configuration.PACKAGE_NAME = myTestPackage.get();
    configuration.CLASS_NAME = myTestClass.get();
    configuration.METHOD_NAME = myTestMethod.get();
    configuration.INSTRUMENTATION_RUNNER_CLASS = isBuildWithGradle ? "" : myInstrumentationClass.get();
    configuration.EXTRA_OPTIONS = myUserModifiedInstrumentationExtraParams;
    configuration.INCLUDE_GRADLE_EXTRA_OPTIONS = myIncludeGradleExtraParams;
    if (AndroidTestConfiguration.getInstance().getRUN_ANDROID_TEST_USING_GRADLE()) {
      configuration.RETENTION_ENABLED = myEnableEmulatorSnapshotItemsComboBox.getItem();
      configuration.RETENTION_MAX_SNAPSHOTS = Integer.parseInt(myMaxSnapshots.get());
      configuration.RETENTION_COMPRESS_SNAPSHOTS = myCompressSnapshots.get();
    }
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
    myInstrumentationClass.set(
      isBuildWithGradle
      ? AndroidTestRunConfiguration.getDefaultInstrumentationRunner(androidFacet)
      : configuration.INSTRUMENTATION_RUNNER_CLASS);
    myInstrumentationArgs.set(configuration.getExtraInstrumentationOptions(androidFacet));
    myIncludeGradleExtraParams = configuration.INCLUDE_GRADLE_EXTRA_OPTIONS;
    if (AndroidTestConfiguration.getInstance().getRUN_ANDROID_TEST_USING_GRADLE()) {
      myEnableRetention.setValue(configuration.RETENTION_ENABLED);
      myMaxSnapshots.set(Integer.toString(configuration.RETENTION_MAX_SNAPSHOTS));
      myCompressSnapshots.set(configuration.RETENTION_COMPRESS_SNAPSHOTS);
    }
  }

  @Override
  public Component getComponent() {
    return myContentWrapper;
  }

  @Override
  public void dispose() {
    myBindingsManager.releaseAll();
  }

  /**
   * Wraps content UI components and provides {@link UserActivityProviderComponent} interface.
   * <p>
   * {@link TestRunParameters} form is designed to be used in {@link com.intellij.openapi.options.SettingsEditor} and the editor
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
}
