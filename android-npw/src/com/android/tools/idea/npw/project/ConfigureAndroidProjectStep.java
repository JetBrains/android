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
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.npw.model.AgpVersionSelector;
import com.android.tools.idea.npw.model.TemplateRendererStrategy;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.model.NewProjectModuleModel;
import com.android.tools.idea.npw.module.ConfigureModuleStepKt;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem;
import com.android.tools.idea.npw.template.components.LanguageComboProvider;
import com.android.tools.idea.npw.validator.ProjectNameValidator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.validation.validators.StringPathValidator;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject;
import com.android.tools.idea.wizard.template.DslLanguage;
import com.android.tools.idea.wizard.template.FormFactor;
import com.android.tools.idea.wizard.template.Language;
import com.android.tools.idea.wizard.template.Template;
import com.android.tools.idea.wizard.template.TemplateConstraint;
import com.android.tools.idea.wizard.template.TemplateFlag;
import com.android.tools.idea.wizard.ui.StudioWizardLayout;
import com.android.tools.idea.wizard.ui.WizardUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import kotlin.Pair;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * First page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends ModelWizardStep<NewProjectModuleModel> {
  public static final String ANDROID_AUTOMOTIVE_OS_ONLY = "Android Automotive OS only";
  public static final String ANDROID_AUTOMOTIVE_OS_AND_ANDROID_AUTO = "Android Automotive OS and Android Auto";

  private final NewProjectModel myProjectModel;

  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final List<UpdatablePackage> myInstallRequests = new ArrayList<>();
  private final List<RemotePackage> myInstallLicenseRequests = new ArrayList<>();
  private final LicenseAgreementStep myLicenseAgreementStep;

  private final @NotNull JBScrollPane myRootPanel;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;

  private JTextField myPackageName;
  private JComboBox<Language> myProjectLanguage;
  private JBLabel myProjectLanguageLabel;
  private JBCheckBox myWearCheck;
  private JBCheckBox myTvCheck;
  private JComboBox<String> myCarPlatformCombo;
  private JBLabel myCarPlatformComboLabel;
  private JBLabel myTemplateTitle;
  private JBLabel myTemplateDetail;
  private HyperlinkLabel myDocumentationLink;
  private JPanel myFormFactorSdkControlsPanel;
  private JComboBox myMinSdkCombo;
  private JComboBox<Object> myBuildConfigurationLanguageCombo;
  private ContextHelpLabel myBuildConfigurationLanguageLabel;
  private JBCheckBox myLaunchFirebaseCheckbox;
  private JPanel myLaunchFirebasePanel;

  private ContextHelpLabel myAndroidGradlePluginLabel;
  private JComboBox<AgpVersions.NewProjectWizardAgpVersion> myAndroidGradlePluginCombo;
  private FormFactorSdkControls myFormFactorSdkControls;

  public ConfigureAndroidProjectStep(@NotNull NewProjectModuleModel newProjectModuleModel, @NotNull NewProjectModel projectModel) {
    super(newProjectModuleModel, message("android.wizard.project.new.configure"));

    myProjectModel = projectModel;
    setupUI();
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myRootPanel = wrapWithVScroll(myValidatorPanel);

    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);

    myLicenseAgreementStep = new LicenseAgreementStep(new LicenseAgreementModel(getSdkManagerLocalPath()), () -> myInstallLicenseRequests,
                                                      StudioWizardLayout.DEFAULT_BORDER_INSETS);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep<?>> createDependentSteps() {
    InstallSelectedPackagesStep installPackagesStep =
      new InstallSelectedPackagesStep(myInstallRequests, new HashSet<>(), () -> AndroidSdks.getInstance().tryToChooseSdkHandler(), false,
                                      StudioWizardLayout.DEFAULT_BORDER_INSETS);

    return Lists.newArrayList(myLicenseAgreementStep, installPackagesStep);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
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
    myBindings.bindTwoWay(new SelectedProperty(myLaunchFirebaseCheckbox), myProjectModel.getLaunchFirebaseWizard());

      myBuildConfigurationLanguageCombo.addItem(BuildConfigurationLanguageForNewProject.KTS);

      if (StudioFlags.NPW_SHOW_DCL.get()) {
        myBuildConfigurationLanguageCombo.addItem(BuildConfigurationLanguageForNewProject.DCL);
      }

      Object initialSelection;
      if (myProjectModel.getTemplateRendererStrategy().getValueOrNull() != null) {
        initialSelection = myProjectModel.getTemplateRendererStrategy().getValueOrNull();
        myBuildConfigurationLanguageCombo.addItem(initialSelection);
      } else {
        initialSelection = switch (myProjectModel.getDslLanguage().get()) {
          case KTS, GROOVY -> BuildConfigurationLanguageForNewProject.KTS;
          case DCL -> BuildConfigurationLanguageForNewProject.DCL;
        };
      }
      myBuildConfigurationLanguageCombo.setSelectedItem(initialSelection);

      myBuildConfigurationLanguageCombo.setRenderer(new com.intellij.ui.SimpleListCellRenderer<>() {
        @Override
        public void customize(javax.swing.JList<? extends Object> list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof BuildConfigurationLanguageForNewProject) {
            setText(((BuildConfigurationLanguageForNewProject)value).getDescription());
          }
          else if (value instanceof TemplateRendererStrategy) {
            setText(((TemplateRendererStrategy)value).getDisplayName());
          }
        }
      });

      myBindings.bind(myProjectModel.getDslLanguage(), new SelectedItemProperty<>(myBuildConfigurationLanguageCombo).transform(
        selection -> selection.map(value -> {
          if (value instanceof BuildConfigurationLanguageForNewProject) {
            return switch ((BuildConfigurationLanguageForNewProject) value) {
              case KTS -> DslLanguage.KTS;
              case DCL -> DslLanguage.DCL;
            };
          }
          return DslLanguage.KTS;
        }).orElse(DslLanguage.KTS)
      ));

      myBindings.bind(myProjectModel.getTemplateRendererStrategy(), new SelectedItemProperty<>(myBuildConfigurationLanguageCombo).transform(
        selection -> {
          if (selection.isPresent() && selection.get() instanceof TemplateRendererStrategy) {
            //noinspection unchecked: Generic use
            return Optional.of((TemplateRendererStrategy<TemplateRendererStrategy.AdditionalUserSettings>) selection.get());
          }
          return Optional.empty();
        }
      ));
      myProjectModel.getTemplateRenderStrategyAdditionalUserSettings().forEach((renderStrategy, settings) -> {
        ObservableValue<Boolean> display =
          new SelectedItemProperty<>(myBuildConfigurationLanguageCombo)
            .transform(selection -> selection.isPresent() && selection.get() == renderStrategy);
        for (Pair<@NotNull JBLabel, @NotNull JComponent> labeledComponent : settings.getLabeledComponents()) {
          myBindings.bind(new VisibleProperty(labeledComponent.getFirst()), display);
          myBindings.bind(new VisibleProperty(labeledComponent.getSecond()), display);
        }

      });

    TemplateRenderStrategyInitializer initializer = new TemplateRenderStrategyInitializer();
    myBuildConfigurationLanguageCombo.addActionListener(initializer);
    initializer.ensureInitialInitialized();;

    if ((StudioFlags.NPW_SHOW_AGP_VERSION_COMBO_BOX.get() && !ApplicationManager.getApplication().isUnitTestMode()) ||
        (StudioFlags.NPW_SHOW_AGP_VERSION_COMBO_BOX_EXPERIMENTAL_SETTING.get() &&
         GradleExperimentalSettings.getInstance().SHOW_ANDROID_GRADLE_PLUGIN_VERSION_COMBO_BOX_IN_NEW_PROJECT_WIZARD)) {
      AgpVersions.NewProjectWizardAgpVersion
        placeholderCurrentVersion = new AgpVersions.NewProjectWizardAgpVersion(
        /* Resolve as if IdeGoogleMavenRepository.getAgpVersions() is not available as a placeholder */
        myProjectModel.getAgpVersionSelector().get().resolveVersion(ImmutableSet::of),
        ImmutableList.of(),
        "");
      myAndroidGradlePluginCombo.addItem(placeholderCurrentVersion);
      myBindings.bind(myProjectModel.getAgpVersionSelector(), ObjectProperty.wrap(new SelectedItemProperty<>(myAndroidGradlePluginCombo)).transform(it -> new AgpVersionSelector.FixedVersion(((AgpVersions.NewProjectWizardAgpVersion)it).getVersion())));
      myBindings.bind(myProjectModel.getAdditionalMavenRepos(), ObjectProperty.wrap(new SelectedItemProperty<>(myAndroidGradlePluginCombo)).transform(it -> ((AgpVersions.NewProjectWizardAgpVersion)it).getAdditionalMavenRepositoryUrls()));

      BackgroundTaskUtil.executeOnPooledThread(this, () -> {
        List<AgpVersions.NewProjectWizardAgpVersion> suggestedAgpVersions = AgpVersions.INSTANCE.getNewProjectWizardVersions();
        AgpVersion newProjectDefault = myProjectModel.getAgpVersionSelector().get()
          // Use new project wizard versions to resolve here (rather than just AgpVersions.getAvailableVersions()) to avoid having to
          // consider the corner case of skew between getNewProjectWizardVersions() and getAvailableVersions().
          .resolveVersion(() -> suggestedAgpVersions.stream().map(AgpVersions.NewProjectWizardAgpVersion::getVersion).collect(Collectors.toSet()));

        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
          boolean foundCurrent = false;
          for (AgpVersions.NewProjectWizardAgpVersion version : suggestedAgpVersions) {
            if (!foundCurrent && version.getVersion().equals(newProjectDefault) && version.getAdditionalMavenRepositoryUrls().isEmpty()) {
              foundCurrent = true;
              AgpVersions.NewProjectWizardAgpVersion newProjectWizardVersion = new AgpVersions.NewProjectWizardAgpVersion(version.getVersion(), ImmutableList.of(), "New project default");
              myAndroidGradlePluginCombo.addItem(newProjectWizardVersion);
              myAndroidGradlePluginCombo.setSelectedItem(newProjectWizardVersion);
              myAndroidGradlePluginCombo.removeItem(placeholderCurrentVersion);
            } else {
              myAndroidGradlePluginCombo.addItem(version);
            }
          }
        });
      });
      ConfigureModuleStepKt.registerKtsAgpVersionValidation(myValidatorPanel, myProjectModel);
      myBindings.bind(new VisibleProperty(myAndroidGradlePluginCombo), myProjectModel.getTemplateRendererStrategy().transform(
        Optional::isEmpty));
      myBindings.bind(new VisibleProperty(myAndroidGradlePluginLabel), myProjectModel.getTemplateRendererStrategy().transform(
        Optional::isEmpty));
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

    myProjectLocation.addBrowseFolderListener(null, createSingleFolderDescriptor());

    myListeners.listenAndFire(getModel().formFactor, () -> {
      FormFactor formFactor = getModel().formFactor.get();

      myFormFactorSdkControls.showStatsPanel(formFactor == FormFactor.Mobile);
      myTvCheck.setVisible(formFactor == FormFactor.Tv);
      boolean isCar = formFactor == FormFactor.Car;
      myCarPlatformCombo.setVisible(isCar);
      myCarPlatformComboLabel.setVisible(isCar);
      if (isCar) {
        myCarPlatformCombo.setSelectedItem(ANDROID_AUTOMOTIVE_OS_AND_ANDROID_AUTO);
      }
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
    boolean isWatchFace = newTemplate.getFlags().contains(TemplateFlag.WatchFace);

    myProjectLanguage.setVisible(!isKotlinOnly && !isWatchFace);
    myProjectLanguageLabel.setVisible(!isKotlinOnly && !isWatchFace);
    if (isKotlinOnly) {
      myProjectModel.getLanguage().setValue(Language.Kotlin);
    }
    myWearCheck.setVisible(formFactor == FormFactor.Wear && !isWatchFace);

    updateCustomSystems(newTemplate);

    boolean isFirebaseTemplate = newTemplate.getFlags().contains(TemplateFlag.FirebaseAi);
    myProjectModel.getLaunchFirebaseWizard().set(isFirebaseTemplate);
    myLaunchFirebasePanel.setVisible(isFirebaseTemplate);

    myBuildConfigurationLanguageCombo.setVisible(true);
    myBuildConfigurationLanguageLabel.setVisible(true);

    if (isWatchFace) {
      myProjectModel.getApplicationName().set("My Watch Face");
    }
  }

  @Override
  protected void onProceeding() {
    getModel().hasCompanionApp.set(
      (myWearCheck.isVisible() && myWearCheck.isSelected()) ||
      (myTvCheck.isVisible() && myTvCheck.isSelected()) ||
      (myCarPlatformCombo.isVisible() &&
       ANDROID_AUTOMOTIVE_OS_AND_ANDROID_AUTO.equals(myCarPlatformCombo.getSelectedItem()))
    );

    myInstallRequests.clear();
    myInstallLicenseRequests.clear();

    myInstallRequests.addAll(myFormFactorSdkControls.getSdkInstallPackageList());
    myInstallLicenseRequests.addAll(ContainerUtil.map(myInstallRequests, UpdatablePackage::getRemote));

    myLicenseAgreementStep.reload();
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

  private void setupUI() {
    createUIComponents();

    var builder = new FormBuilder() {
      @Override
      protected int getFill(JComponent component) {
        if (component instanceof JComboBox<?>) {
          return GridBagConstraints.HORIZONTAL;
        }
        return super.getFill(component);
      }
    };
    builder.setVerticalGap(8)
      .addVerticalGap(16)
      .addComponent(myTemplateTitle)
      .addVerticalGap(10)
      .addComponent(createDocumentationLinkPanel())
      .addVerticalGap(10)
      .addLabeledComponent("&Name",  myAppName)
      .addLabeledComponent("&Package name", myPackageName)
      .addLabeledComponent("&Save location", myProjectLocation)
      .addLabeledComponent(myProjectLanguageLabel, myProjectLanguage)
      .addLabeledComponent("Minimum SDK", myMinSdkCombo)
      .addComponentToRightColumn(myFormFactorSdkControlsPanel)
      .addComponentToRightColumn(myWearCheck)
      .addComponentToRightColumn(myTvCheck)
      .addLabeledComponent(myCarPlatformComboLabel, myCarPlatformCombo)
      .addLabeledComponent(myBuildConfigurationLanguageLabel, myBuildConfigurationLanguageCombo)
      .addLabeledComponent(myAndroidGradlePluginLabel, myAndroidGradlePluginCombo);

    for (TemplateRendererStrategy.AdditionalUserSettings additionalSettings : myProjectModel.getTemplateRenderStrategyAdditionalUserSettings()
      .values()) {
      for (Pair<JBLabel, JComponent> component : additionalSettings.getLabeledComponents()) {
        builder.addLabeledComponent(component.getFirst(), component.getSecond());
      }
    }

    builder
      .addComponent(myLaunchFirebasePanel)
      .addComponentFillVertically(new Spacer(), 0);

    myPanel = builder.getPanel();
  }

  private @NotNull JPanel createDocumentationLinkPanel() {
    JPanel documentationLinkPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    documentationLinkPanel.add(myTemplateDetail, c);
    c = new GridBagConstraints();
    c.gridx = 1;
    c.gridy = 0;
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    documentationLinkPanel.add(myDocumentationLink, c);
    return documentationLinkPanel;
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

  private static boolean hasValidSdkComposeVersion(VersionItem sdkItem, @Nullable Template renderTemplate) {
    return renderTemplate == null ||
           !renderTemplate.getConstraints().contains(TemplateConstraint.Compose) ||
           sdkItem.getCompileSdk().isAtLeast(AndroidVersion.VersionCodes.S);
  }

  private void createUIComponents() {
    myProjectLanguage = new LanguageComboProvider().createComponent();
    myProjectLanguageLabel = new JBLabel(UIUtil.replaceMnemonicAmpersand("&Language"));
    myFormFactorSdkControls = new FormFactorSdkControls();
    myFormFactorSdkControlsPanel = myFormFactorSdkControls.getRoot();
    myMinSdkCombo = myFormFactorSdkControls.getMinSdkComboBox();
    myBuildConfigurationLanguageLabel = ContextHelpLabel.createWithLink(
      null,
      AndroidBundle.message("android.wizard.project.help.buildconfigurationlanguage.description"),
      "Learn more",
      () -> BrowserUtil.browse(KOTLIN_DSL_LINK));
    myBuildConfigurationLanguageLabel.setText("Build configuration language");
    myBuildConfigurationLanguageLabel.setHorizontalTextPosition(SwingConstants.LEFT);
    myAndroidGradlePluginLabel = ContextHelpLabel.create(
      "This is only shown for development builds of Android Studio",
      "It can be hidden while idea.is.internal=true by setting Studio flag " +
      StudioFlags.NPW_SHOW_AGP_VERSION_COMBO_BOX.getId() +
      " to false");
    myAndroidGradlePluginLabel.setText("Android Gradle plugin (internal option)");
    myAndroidGradlePluginLabel.setHorizontalTextPosition(SwingConstants.LEFT);

    myAppName = new JTextField();
    myAppName.setToolTipText("The name that will be shown in the Android launcher for this application");

    myPackageName = new JTextField();
    myProjectLocation = new TextFieldWithBrowseButton();
    myProjectLanguage = new LanguageComboProvider().createComponent();
    myWearCheck = new JBCheckBox("Pair with Empty Phone app");
    myTvCheck = new JBCheckBox("Pair with companion Phone app");
    myCarPlatformCombo = new ComboBox<>(new String[] { ANDROID_AUTOMOTIVE_OS_ONLY, ANDROID_AUTOMOTIVE_OS_AND_ANDROID_AUTO });
    myCarPlatformComboLabel = new JBLabel("Platform");
    myTemplateTitle = new JBLabel("Template title");
    myTemplateTitle.setFont(myTemplateTitle.getFont().deriveFont(Font.BOLD));
    myTemplateDetail = new JBLabel("Template detail");
    myDocumentationLink = new HyperlinkLabel();
    myBuildConfigurationLanguageCombo = new JComboBox<>();
    myAndroidGradlePluginCombo = new JComboBox<>();
    myLaunchFirebaseCheckbox = new JBCheckBox("Launch the Gemini API setup");
    myLaunchFirebasePanel = createCheckboxPanel(
      myLaunchFirebaseCheckbox, "<html>" + message("android.wizard.project.new.launch.gemini.setup") + "</html>");
  }

  private void updateCustomSystems(@NotNull Template template) {
    Object currentSelection = myBuildConfigurationLanguageCombo.getSelectedItem();
    if (currentSelection == null) {
      currentSelection = myProjectModel.getTemplateRendererStrategy().getValueOrNull();
    }

    // Remove existing custom systems
    for (int i = myBuildConfigurationLanguageCombo.getItemCount() - 1; i >= 0; i--) {
      if (myBuildConfigurationLanguageCombo.getItemAt(i) instanceof TemplateRendererStrategy) {
        myBuildConfigurationLanguageCombo.removeItemAt(i);
      }
    }

    // Add applicable ones
    for (TemplateRendererStrategy system : TemplateRendererStrategy.EP_NAME.getExtensions()) {
      if (system.isTemplateApplicable(template)) {
        myBuildConfigurationLanguageCombo.addItem(system);
      }
    }

    // Restore selection
    if (currentSelection instanceof TemplateRendererStrategy) {
      String id = ((TemplateRendererStrategy)currentSelection).getId();
      for (int i = 0; i < myBuildConfigurationLanguageCombo.getItemCount(); i++) {
        Object item = myBuildConfigurationLanguageCombo.getItemAt(i);
        if (item instanceof TemplateRendererStrategy && ((TemplateRendererStrategy)item).getId().equals(id)) {
          myBuildConfigurationLanguageCombo.setSelectedItem(item);
          return;
        }
      }
      // Not applicable to this template, fall back to KTS
      myBuildConfigurationLanguageCombo.setSelectedItem(BuildConfigurationLanguageForNewProject.KTS);
    }
  }

  private static JPanel createCheckboxPanel(JBCheckBox checkbox, String description) {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
    panel.add(checkbox, c);

    c.gridy = 1;
    c.insets = JBUI.insets(4, calculateHorizontalIndent(), 0, 0);
    JBLabel descriptionLabel = new JBLabel(description);
    descriptionLabel.setForeground(UIUtil.getContextHelpForeground());
    panel.add(descriptionLabel, c);
    return panel;
  }

  private static int calculateHorizontalIndent() {
    int iconSize = JBUI.getInt("CheckBox.iconSize", 18);
    int textIconGap = JBUI.getInt("CheckBox.textIconGap", 5);
    return iconSize + textIconGap;
  }

  /**
   * Listener to defer TemplateRendererStrategy.AdditionalUserSettings initialization,
   * while ensuring that those initializations only happen once.
   */
  private class TemplateRenderStrategyInitializer implements ActionListener {
    private final Set<TemplateRendererStrategy<?>> alreadyTriggeredInitialize = new HashSet<>();

    @Override
    public void actionPerformed(ActionEvent event) {
      Object newValue = myBuildConfigurationLanguageCombo.getSelectedItem();
      if (newValue instanceof TemplateRendererStrategy<?> ) {
        ensureInitialized((TemplateRendererStrategy<?>)newValue);
      }
    }

    public void ensureInitialInitialized() {
      TemplateRendererStrategy<TemplateRendererStrategy.AdditionalUserSettings> initialStrategy =
        myProjectModel.getTemplateRendererStrategy().getValueOrNull();
      if (initialStrategy == null) return;
      ensureInitialized(initialStrategy);
    }

    private void ensureInitialized(TemplateRendererStrategy<?> strategy) {
      if (!alreadyTriggeredInitialize.add(strategy)) return;
      BackgroundTaskUtil.executeOnPooledThread(ConfigureAndroidProjectStep.this, () -> {
        myProjectModel.getTemplateRenderStrategyAdditionalUserSettings().get(strategy).backgroundInitialize();
      });
    }
  }
}
