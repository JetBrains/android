/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import static com.android.tools.adtui.validation.Validator.Result.OK;
import static com.android.tools.adtui.validation.Validator.Severity.ERROR;
import static com.android.tools.adtui.validation.Validator.Severity.WARNING;
import static com.android.tools.idea.npw.FormFactorUtilKt.toWizardFormFactor;
import static com.android.tools.idea.npw.model.NewProjectModel.nameToJavaPackage;
import static com.android.tools.idea.npw.module.AndroidApiLevelComboBoxKt.ensureDefaultApiLevelAtLeastRecommended;
import static com.android.tools.idea.npw.platform.AndroidVersionsInfoKt.getSdkManagerLocalPath;
import static com.android.tools.idea.wizard.template.TemplateDataKt.KOTLIN_DSL_LINK;
import static com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor;
import static java.lang.String.format;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.ide.common.repository.AgpVersion;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.AgpVersions;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.model.NewProjectModuleModel;
import com.android.tools.idea.npw.module.ConfigureModuleStepKt;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem;
import com.android.tools.idea.npw.template.components.LanguageComboProvider;
import com.android.tools.idea.npw.validator.ProjectNameValidator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.expressions.value.TransformOptionalExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.validation.validators.StringPathValidator;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject;
import com.android.tools.idea.wizard.template.FormFactor;
import com.android.tools.idea.wizard.template.Language;
import com.android.tools.idea.wizard.template.Template;
import com.android.tools.idea.wizard.template.TemplateConstraint;
import com.android.tools.idea.wizard.ui.WizardUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * First page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends ModelWizardStep<NewProjectModuleModel> {
  private final NewProjectModel myProjectModel;

  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final List<UpdatablePackage> myInstallRequests = new ArrayList<>();
  private final List<RemotePackage> myInstallLicenseRequests = new ArrayList<>();

  private final @NotNull JBScrollPane myRootPanel;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;
  private JTextField myPackageName;
  private JComboBox<Language> myProjectLanguage;
  private JBLabel myProjectLanguageLabel;
  private JBCheckBox myWearCheck;
  private JBCheckBox myTvCheck;
  private JBLabel myTemplateTitle;
  private JBLabel myTemplateDetail;
  private HyperlinkLabel myDocumentationLink;
  private JPanel myFormFactorSdkControlsPanel;
  private JComboBox myMinSdkCombo;
  private JComboBox<BuildConfigurationLanguageForNewProject> myBuildConfigurationLanguageCombo;
  private ContextHelpLabel myBuildConfigurationLanguageLabel;

  private ContextHelpLabel myAndroidGradlePluginLabel;
  private JComboBox<AgpVersions.NewProjectWizardAgpVersion> myAndroidGradlePluginCombo;
  private FormFactorSdkControls myFormFactorSdkControls;

  public ConfigureAndroidProjectStep(@NotNull NewProjectModuleModel newProjectModuleModel, @NotNull NewProjectModel projectModel) {
    super(newProjectModuleModel, message("android.wizard.project.new.configure"));

    myProjectModel = projectModel;
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myRootPanel = wrapWithVScroll(myValidatorPanel);

    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep<?>> createDependentSteps() {
    LicenseAgreementStep licenseAgreementStep =
      new LicenseAgreementStep(new LicenseAgreementModel(getSdkManagerLocalPath()), myInstallLicenseRequests);

    InstallSelectedPackagesStep installPackagesStep =
      new InstallSelectedPackagesStep(myInstallRequests, new HashSet<>(), AndroidSdks.getInstance().tryToChooseSdkHandler(), false);

    return Lists.newArrayList(licenseAgreementStep, installPackagesStep);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    ((GridLayoutManager)myPanel.getLayout()).setVGap(2);

    myBindings.bindTwoWay(new TextProperty(myAppName), myProjectModel.getApplicationName());

    String basePackage = NewProjectModel.getSuggestedProjectPackage();

    Expression<String> computedPackageName = myProjectModel.getApplicationName()
      .transform(appName -> format("%s.%s", basePackage, nameToJavaPackage(appName)));
    TextProperty packageNameText = new TextProperty(myPackageName);
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(myProjectModel.getPackageName(), packageNameText);

    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myListeners.listen(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    Expression<String> computedLocation = myProjectModel.getApplicationName().transform(ConfigureAndroidProjectStep::findProjectLocation);
    TextProperty locationText = new TextProperty(myProjectLocation.getTextField());
    BoolProperty isLocationSynced = new BoolValueProperty(true);
    myBindings.bind(locationText, computedLocation, isLocationSynced);
    myBindings.bind(myProjectModel.getProjectLocation(), locationText.trim());
    myListeners.listen(locationText, value -> isLocationSynced.set(value.equals(computedLocation.get())));

    OptionalProperty<VersionItem> androidSdkInfo = getModel().androidSdkInfo();
    myFormFactorSdkControls.init(androidSdkInfo, this);

    myBindings.bindTwoWay(new SelectedItemProperty<>(myProjectLanguage), myProjectModel.getLanguage());

    if (StudioFlags.NPW_SHOW_KTS_GRADLE_COMBO_BOX.get()) {
      myBuildConfigurationLanguageCombo.addItem(BuildConfigurationLanguageForNewProject.KTS);
      myBuildConfigurationLanguageCombo.addItem(BuildConfigurationLanguageForNewProject.Groovy);
      myBindings.bind(myProjectModel.getUseGradleKts(), new TransformOptionalExpression<BuildConfigurationLanguageForNewProject, Boolean>(true, new SelectedItemProperty<>(myBuildConfigurationLanguageCombo)) {
        @NotNull
        @Override
        protected Boolean transform(@NotNull BuildConfigurationLanguageForNewProject value) {
          return value.getUseKts();
        }
      });
    } else {
      myBuildConfigurationLanguageLabel.setVisible(false);
      myBuildConfigurationLanguageCombo.setVisible(false);
    }

    if (StudioFlags.NPW_SHOW_AGP_VERSION_COMBO_BOX.get()) {
      AgpVersions.NewProjectWizardAgpVersion
        placeholderCurrentVersion = new AgpVersions.NewProjectWizardAgpVersion(myProjectModel.getAgpVersion().get(), ImmutableList.of(), "");
      myAndroidGradlePluginCombo.addItem(placeholderCurrentVersion);
      myBindings.bind(myProjectModel.getAgpVersion(), ObjectProperty.wrap(new SelectedItemProperty<>(myAndroidGradlePluginCombo)).transform(it -> ((AgpVersions.NewProjectWizardAgpVersion)it).getVersion()));
      myBindings.bind(myProjectModel.getAdditionalMavenRepos(), ObjectProperty.wrap(new SelectedItemProperty<>(myAndroidGradlePluginCombo)).transform(it -> ((AgpVersions.NewProjectWizardAgpVersion)it).getAdditionalMavenRepositoryUrls()));

      BackgroundTaskUtil.executeOnPooledThread(this, () -> {
        List<AgpVersions.NewProjectWizardAgpVersion> suggestedAgpVersions = AgpVersions.INSTANCE.getNewProjectWizardVersions();
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
          AgpVersion current = myProjectModel.getAgpVersion().get();
          boolean foundCurrent = false;
          for (AgpVersions.NewProjectWizardAgpVersion version : suggestedAgpVersions) {
            myAndroidGradlePluginCombo.addItem(version);
            if (!foundCurrent && version.getVersion().equals(current)) {
              foundCurrent = true;
              myAndroidGradlePluginCombo.setSelectedItem(version);
              myAndroidGradlePluginCombo.removeItem(placeholderCurrentVersion); // Delete the placeholder current version that was first
            }
          }
        });
      });
      ConfigureModuleStepKt.registerKtsAgpVersionValidation(myValidatorPanel, myProjectModel);
    } else {
      myAndroidGradlePluginLabel.setVisible(false);
      myAndroidGradlePluginCombo.setVisible(false);
    }

    myValidatorPanel.registerValidator(myProjectModel.getApplicationName(), new ProjectNameValidator());

    myValidatorPanel.registerValidator(myProjectModel.getProjectLocation(),
                                       new StringPathValidator(PathValidator.createDefault("Save location")));

    myValidatorPanel.registerValidator(myProjectModel.getPackageName(),
                                       value -> Validator.Result.fromNullableMessage(AndroidUtils.validatePackageName(value)));

    myValidatorPanel.registerValidator(myProjectModel.getLanguage(), value ->
      value.isPresent() ? OK : new Validator.Result(ERROR, message("android.wizard.validate.select.language")));

    myValidatorPanel.registerValidator(androidSdkInfo, value ->
      value.isPresent() ? OK : new Validator.Result(ERROR, message("select.target.dialog.text")));

    myValidatorPanel.registerValidator(
      androidSdkInfo,
      value -> !value.isPresent() || hasValidSdkComposeVersion(value.get(), getModel().newRenderTemplate.getValueOrNull()) ? OK :
               new Validator.Result(WARNING, message("android.wizard.validate.select.compose.sdk")),
      getModel().newRenderTemplate
    );

    myProjectLocation.addBrowseFolderListener(null, null, null, createSingleFolderDescriptor());

    myListeners.listenAndFire(getModel().formFactor, () -> {
      FormFactor formFactor = getModel().formFactor.get();

      myFormFactorSdkControls.showStatsPanel(formFactor == FormFactor.Mobile);
      myWearCheck.setVisible(formFactor == FormFactor.Wear);
      myTvCheck.setVisible(formFactor == FormFactor.Tv);
    });
  }

  @Override
  protected void onEntering() {
    FormFactor formFactor = getModel().formFactor.get();
    Template newTemplate = getModel().newRenderTemplate.getValue();

    int minSdk = newTemplate.getMinSdk();

    ensureDefaultApiLevelAtLeastRecommended();
    myFormFactorSdkControls.startDataLoading(toWizardFormFactor(formFactor), minSdk);
    setTemplateThumbnail(newTemplate);
    boolean isKotlinOnly = newTemplate.getConstraints().contains(TemplateConstraint.Kotlin);

    myProjectLanguage.setVisible(!isKotlinOnly);
    myProjectLanguageLabel.setVisible(!isKotlinOnly);
    if (isKotlinOnly) {
      myProjectModel.getLanguage().setValue(Language.Kotlin);
    }
  }

  @Override
  protected void onProceeding() {
    getModel().hasCompanionApp.set(
      (myWearCheck.isVisible() && myWearCheck.isSelected()) ||
      (myTvCheck.isVisible() && myTvCheck.isSelected()) ||
      getModel().formFactor.get() == FormFactor.Automotive // Automotive projects include a mobile module for Android Auto by default
    );

    myInstallRequests.clear();
    myInstallLicenseRequests.clear();

    myInstallRequests.addAll(myFormFactorSdkControls.getSdkInstallPackageList());
    myInstallLicenseRequests.addAll(ContainerUtil.map(myInstallRequests, UpdatablePackage::getRemote));
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAppName;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @NotNull
  private static String findProjectLocation(@NotNull String applicationName) {
    applicationName = NewProjectModel.sanitizeApplicationName(applicationName);
    File baseDirectory = WizardUtils.getProjectLocationParent();
    File projectDirectory = new File(baseDirectory, applicationName);

    // Try appName, appName2, appName3, ...
    int counter = 2;
    while (projectDirectory.exists()) {
      projectDirectory = new File(baseDirectory, format(Locale.US, "%s%d", applicationName, counter++));
    }

    return projectDirectory.getPath();
  }

  private void setTemplateThumbnail(@NotNull Template template) {
    myTemplateTitle.setText(template.getName());
    myTemplateDetail.setText("<html>" + template.getDescription() + "</html>");

    String documentationUrl = template.getDocumentationUrl();
    if (documentationUrl != null) {
      myDocumentationLink.setHyperlinkText(message("android.wizard.activity.add.cpp.docslinktext"));
      myDocumentationLink.setHyperlinkTarget(documentationUrl);
    }
    myDocumentationLink.setVisible(documentationUrl != null);
  }

  private static boolean hasValidSdkComposeVersion(VersionItem skdItem, @Nullable Template renderTemplate) {
    return renderTemplate == null ||
           !renderTemplate.getConstraints().contains(TemplateConstraint.Compose) ||
           skdItem.getTargetApiLevel() >= AndroidVersion.VersionCodes.S;
  }

  private void createUIComponents() {
    myProjectLanguage = new LanguageComboProvider().createComponent();
    myFormFactorSdkControls = new FormFactorSdkControls();
    myFormFactorSdkControlsPanel = myFormFactorSdkControls.getRoot();
    myMinSdkCombo = myFormFactorSdkControls.getMinSdkComboBox();
    myBuildConfigurationLanguageLabel = ContextHelpLabel.createWithLink(
      null,
      AndroidBundle.message("android.wizard.project.help.buildconfigurationlanguage.description"),
      "Learn more",
      () -> BrowserUtil.browse(KOTLIN_DSL_LINK));
    myBuildConfigurationLanguageLabel.setHorizontalTextPosition(SwingConstants.LEFT);
    myAndroidGradlePluginLabel = ContextHelpLabel.create(
      "This is only shown for development builds of Android Studio",
      "It can be hidden while idea.is.internal=true by setting Studio flag " + StudioFlags.NPW_SHOW_AGP_VERSION_COMBO_BOX.getId() + " to false");
    myAndroidGradlePluginLabel.setHorizontalTextPosition(SwingConstants.LEFT);
  }
}
