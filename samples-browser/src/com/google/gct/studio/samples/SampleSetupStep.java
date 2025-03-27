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
package com.google.gct.studio.samples;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.ObservableString;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.expressions.string.StringExpression;
import com.android.tools.idea.observable.expressions.value.TransformOptionalExpression;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.ui.WizardUtils;
import com.android.tools.idea.wizard.ui.deprecated.StudioWizardStepPanel;
import com.appspot.gsamplesindex.samplesindex.model.Sample;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * SampleSetupStep is the second/final page in the Sample Import wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class SampleSetupStep extends ModelWizardStep<SampleModel> {

  private static final String DEFAULT_SAMPLE_NAME = SamplesBrowserBundle.message("sample.default.name");

  private final StudioWizardStepPanel myStudioPanel;
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final StringProperty myProjectName;
  private final StringProperty myDir;
  private final ObservableString myNameToPathExpression;

  private JTextField myProjectNameField;
  private HyperlinkLabel myUrlField;
  private TextFieldWithBrowseButton myProjectLocationField;
  private JPanel myPanel;

  @NotNull private String myUrl = "";

  public SampleSetupStep(@NotNull SampleModel model) {
    super(model, SamplesBrowserBundle.message("sample.setup.title"));

    setupUI();
    myProjectName = new TextProperty(myProjectNameField);
    myDir = new TextProperty(myProjectLocationField.getTextField());

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myStudioPanel = new StudioWizardStepPanel(myValidatorPanel, SamplesBrowserBundle.message("sample.setup.description"));

    myNameToPathExpression = new StringExpression(myProjectName) {
      @NotNull
      @Override
      public String get() {
        return getFileLocation(myProjectName.get()).getAbsolutePath();
      }
    };
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    myProjectNameField = new JTextField();
    myPanel.add(myProjectNameField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                        new Dimension(150, -1), null, 0, false));
    myUrlField = new HyperlinkLabel();
    myPanel.add(myUrlField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                new Dimension(150, -1), null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setHorizontalAlignment(10);
    jBLabel1.setText("GitHub URL:");
    myPanel.add(jBLabel1,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Project location:");
    myPanel.add(jBLabel2,
                new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel3 = new JBLabel();
    jBLabel3.setText("Application name:");
    myPanel.add(jBLabel3,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myProjectLocationField = new TextFieldWithBrowseButton();
    myPanel.add(myProjectLocationField, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                            null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    jBLabel2.setLabelFor(myProjectLocationField);
    jBLabel3.setLabelFor(myProjectNameField);
  }

  private static String getUniqueName(String projectName) {
    File file = getFileLocation(projectName);
    String name = projectName;
    int i = 0;
    while (file.exists()) {
      i++;
      name = projectName + i;
      file = getFileLocation(name);
    }
    return name;
  }

  private static File getFileLocation(String projectName) {
    return new File(WizardUtils.getProjectLocationParent(), projectName.replaceAll("[^a-zA-Z0-9_\\-.]", ""));
  }

  /**
   * Set the URL used by this step, or the empty string if no URL should be shown. This method
   * should be called before the step is shown.
   */
  public void setUrl(@NotNull String url) {
    myUrl = url;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myProjectNameField;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myProjectLocationField.addBrowseFolderListener(
      null,
      FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(SamplesBrowserBundle.message("select.project.location")));

    myBindings.bind(myProjectName, new TransformOptionalExpression<>("", getModel().sample()) {
      @Override
      @NotNull
      protected String transform(@NotNull Sample sample) {
        String name = sample.getTitle();
        return getUniqueName(StringUtil.isEmpty(name) ? DEFAULT_SAMPLE_NAME : name);
      }
    });

    final BoolProperty isNameDirSynced = new BoolValueProperty(true);
    myDir.addListener(() -> isNameDirSynced.set(myDir.get().equals(myNameToPathExpression.get())));
    myBindings.bind(myDir, myNameToPathExpression, isNameDirSynced);

    myValidatorPanel.registerValidator(myProjectName, projectName -> (StringUtil.isEmptyOrSpaces(projectName)
                                                                      ? new Validator.Result(Validator.Severity.ERROR,
                                                                                             SamplesBrowserBundle.message(
                                                                                               "application.name.not.set"))
                                                                      : Validator.Result.OK));

    PathValidator pathValidator = PathValidator.createDefault("sample location");
    Expression<Path> myDirFile = new Expression<>(myDir) {
      @NotNull
      @Override
      public Path get() {
        return Paths.get(myDir.get());
      }
    };
    myValidatorPanel.registerValidator(myDirFile, pathValidator);
  }

  @Override
  protected void onEntering() {
    myUrlField.setHyperlinkTarget(myUrl);
    myUrlField.setHyperlinkText(myUrl);

    myDir.set(myNameToPathExpression.get());
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    // We could have bound to these properties directly, but that spawns a lot of unnecessary
    // intermediate objects (Optionals and Files), so we just set directly here instead.
    getModel().projectName().setValue(myProjectName.get());
    getModel().dir().setValue(new File(myDir.get()));
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}
