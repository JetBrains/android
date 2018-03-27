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

import com.android.SdkConstants;
import com.android.ide.common.sdk.LoadStatus;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.model.NewProjectModuleModel;
import com.android.tools.idea.npw.model.RenderTemplateModel;
import com.android.tools.idea.npw.module.FormFactorApiComboBox;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.npw.template.components.LanguageComboProvider;
import com.android.tools.idea.npw.ui.ActivityGallery;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.stats.DistributionService;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.npw.model.NewProjectModel.toPackagePart;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor;
import static java.lang.String.format;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * First page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends ModelWizardStep<NewProjectModuleModel> {
  private final NewProjectModel myProjectModel;

  private final JBScrollPane myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final AndroidVersionsInfo myAndroidVersionsInfo = new AndroidVersionsInfo();
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final List<UpdatablePackage> myInstallRequests = new ArrayList<>();
  private final List<RemotePackage> myInstallLicenseRequests = new ArrayList<>();

  private JPanel myPanel;
  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;
  private JTextField myPackageName;
  private JComboBox<Language> myProjectLanguage;
  private JCheckBox myNavigationControllerCheck;
  private JCheckBox myInstantAppCheck;
  private JCheckBox myWearCheck;
  private JCheckBox myTvCheck;
  private JLabel myTemplateIconTitle;
  private JLabel myTemplateIconDetail;
  private FormFactorApiComboBox myMinSdkCombobox;
  private JBLabel myApiPercentIcon;
  private JBLabel myApiPercentLabel;
  private HyperlinkLabel myLearnMoreLink;
  private JPanel myStatsPanel;
  private JPanel myLoadingDataPanel;
  private AsyncProcessIcon myLoadingDataIcon;
  private JLabel myLoadingDataLabel;
  private LoadStatus mySdkDataLoadingStatus;
  private LoadStatus myStatsDataLoadingStatus;

  public ConfigureAndroidProjectStep(@NotNull NewProjectModuleModel newProjectModuleModel, @NotNull NewProjectModel projectModel) {
    super(newProjectModuleModel, message("android.wizard.project.new.configure"));

    myProjectModel = projectModel;
    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myRootPanel = StudioWizardStepPanel.wrappedWithVScroll(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    LicenseAgreementStep licenseAgreementStep =
      new LicenseAgreementStep(new LicenseAgreementModel(AndroidVersionsInfo.getSdkManagerLocalPath()), myInstallLicenseRequests);

    InstallSelectedPackagesStep installPackagesStep =
      new InstallSelectedPackagesStep(myInstallRequests, new HashSet<>(), AndroidSdks.getInstance().tryToChooseSdkHandler(), false);

    return Lists.newArrayList(licenseAgreementStep, installPackagesStep);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myBindings.bindTwoWay(new TextProperty(myAppName), myProjectModel.applicationName());

    // TODO: http://b/76205038 - The new UI no longer ask the user for the company domain. We should stop using it, and save the package
    // instead. Keep in mind that we need to remove the last segment, that is specific to the application name.
    StringProperty companyDomain = new StringValueProperty(NewProjectModel.getInitialDomain(false));
    String basePackage = new DomainToPackageExpression(companyDomain, new StringValueProperty("")).get();

    Expression<String> computedPackageName = myProjectModel.applicationName()
      .transform(appName -> format("%s.%s", basePackage, toPackagePart(appName)));
    TextProperty packageNameText = new TextProperty(myPackageName);
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(myProjectModel.packageName(), packageNameText);
    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myListeners.receive(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    Expression<String> computedLocation = myProjectModel.applicationName().transform(ConfigureAndroidProjectStep::findProjectLocation);
    TextProperty locationText = new TextProperty(myProjectLocation.getTextField());
    BoolProperty isLocationSynced = new BoolValueProperty(true);
    myBindings.bind(locationText, computedLocation, isLocationSynced);
    myBindings.bind(myProjectModel.projectLocation(), locationText);
    myListeners.receive(locationText, value -> isLocationSynced.set(value.equals(computedLocation.get())));

    RenderTemplateModel renderModel = getModel().getNewRenderTemplateModel();
    myBindings.bind(renderModel.androidSdkInfo(), new SelectedItemProperty<>(myMinSdkCombobox));
    myListeners.receive(renderModel.androidSdkInfo(), value ->
      value.ifPresent(item -> myApiPercentLabel.setText(getApiHelpText(item.getMinApiLevel())))
    );

    myBindings.bindTwoWay(new SelectedProperty(myInstantAppCheck), getModel().getNewModuleModel().instantApp());

    myValidatorPanel.registerValidator(myProjectModel.applicationName(), value -> {
      if (value.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.validate.empty.application.name"));
      }
      else if (!Character.isUpperCase(value.charAt(0))) {
        return new Validator.Result(Validator.Severity.INFO, message("android.wizard.validate.lowercase.application.name"));
      }
      return Validator.Result.OK;
    });

    Expression<File> locationFile = myProjectModel.projectLocation().transform(File::new);
    myValidatorPanel.registerValidator(locationFile, PathValidator.createDefault("project location"));

    myValidatorPanel.registerValidator(myProjectModel.packageName(),
                                       value -> Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value)));

    myValidatorPanel.registerValidator(renderModel.androidSdkInfo(), value ->
      value.isPresent() ? Validator.Result.OK : new Validator.Result(Validator.Severity.ERROR, message("select.target.dialog.text")));

    myProjectLocation.addBrowseFolderListener(null, null, null, createSingleFolderDescriptor());

    myLoadingDataLabel.setForeground(JBColor.GRAY);
    myApiPercentIcon.setIcon(AllIcons.General.BalloonInformation);

    myLearnMoreLink.setHyperlinkText(message("android.wizard.module.help.choose"));
    myLearnMoreLink.setHyperlinkTarget("https://developer.android.com/about/dashboards/index.html");

    myProjectLanguage.setSelectedItem(myProjectModel.enableKotlinSupport().get() ? Language.KOTLIN : Language.JAVA);

    myListeners.receiveAndFire(getModel().formFactor(), formFactor -> {
      myNavigationControllerCheck.setVisible(formFactor == FormFactor.MOBILE);
      myInstantAppCheck.setVisible(formFactor == FormFactor.MOBILE);
      myStatsPanel.setVisible(formFactor == FormFactor.MOBILE);
      myWearCheck.setVisible(formFactor == FormFactor.WEAR);
      myTvCheck.setVisible(formFactor == FormFactor.TV);
    });
  }

  @Override
  protected void onEntering() {
    FormFactor formFactor = getModel().formFactor().get();
    TemplateHandle templateHandle = getModel().getNewRenderTemplateModel().getTemplateHandle();
    int minSdk = templateHandle == null ? formFactor.getMinOfflineApiLevel() : templateHandle.getMetadata().getMinSdk();

    myApiPercentLabel.setText(getApiHelpText(minSdk));
    startDataLoading(formFactor, minSdk);
    setTemplateThumbnail(templateHandle);
  }

  @Override
  protected void onProceeding() {
    myProjectModel.enableKotlinSupport().set(myProjectLanguage.getSelectedItem() == Language.KOTLIN);

    NewModuleModel moduleModel = getModel().getNewModuleModel();
    RenderTemplateModel renderModel = getModel().getNewRenderTemplateModel();

    moduleModel.moduleName().set(SdkConstants.APP_PREFIX);

    File moduleRoot = new File(myProjectModel.projectLocation().get(), moduleModel.moduleName().get());
    renderModel.getTemplate().set(GradleAndroidModuleTemplate.createDefaultTemplateAt(moduleRoot));

    Project project = moduleModel.getProject().getValueOrNull();
    if (renderModel.getTemplateHandle() == null) { // "Add No Activity" selected
      moduleModel.setDefaultRenderTemplateValues(renderModel, project);
    }
    else {
      moduleModel.getRenderTemplateValues().setValue(renderModel.getTemplateValues());
    }

    new TemplateValueInjector(moduleModel.getTemplateValues())
      .setProjectDefaults(project, moduleModel.applicationName().get(), moduleModel.instantApp().get());

    Set<NewModuleModel> newModuleModels = myProjectModel.getNewModuleModels();
    newModuleModels.clear();

    newModuleModels.add(moduleModel);

    myInstallRequests.clear();
    myInstallLicenseRequests.clear();

    AndroidVersionsInfo.VersionItem androidVersion = getModel().getNewRenderTemplateModel().androidSdkInfo().getValue();
    myInstallRequests.addAll(myAndroidVersionsInfo.loadInstallPackageList(Collections.singletonList(androidVersion)));
    myInstallLicenseRequests.addAll(myInstallRequests.stream().map(UpdatablePackage::getRemote).collect(Collectors.toList()));
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
      projectDirectory = new File(baseDirectory, format("%s%d", applicationName, counter++));
    }

    return projectDirectory.getPath();
  }

  private void startDataLoading(FormFactor formFactor, int minSdk) {
    mySdkDataLoadingStatus = LoadStatus.LOADING;
    myStatsDataLoadingStatus = myStatsPanel.isVisible() ? LoadStatus.LOADING : LoadStatus.LOADED;
    updateLoadingProgress();

    myAndroidVersionsInfo.load();
    myAndroidVersionsInfo.loadTargetVersions(formFactor, minSdk, items -> {
      myMinSdkCombobox.init(formFactor, items);
      mySdkDataLoadingStatus = LoadStatus.LOADED;
      updateLoadingProgress();
    });

    if (myStatsPanel.isVisible()) {
      DistributionService.getInstance().refresh(
        () -> ApplicationManager.getApplication().invokeLater(() -> {
          myStatsDataLoadingStatus = LoadStatus.LOADED;
          updateLoadingProgress();
        }),
        () -> ApplicationManager.getApplication().invokeLater(() -> {
          myStatsDataLoadingStatus = LoadStatus.FAILED;
          updateLoadingProgress();
        }));
    }
  }

  private void updateLoadingProgress() {
    myLoadingDataPanel.setVisible(mySdkDataLoadingStatus != LoadStatus.LOADED || myStatsDataLoadingStatus != LoadStatus.LOADED);
    myLoadingDataIcon.setVisible(mySdkDataLoadingStatus == LoadStatus.LOADING || myStatsDataLoadingStatus == LoadStatus.LOADING);

    if (mySdkDataLoadingStatus == LoadStatus.LOADING) {
      myLoadingDataLabel.setText(message("android.wizard.project.loading.sdks"));
    }
    else if (myStatsDataLoadingStatus == LoadStatus.LOADING) {
      myLoadingDataLabel.setText(message("android.wizard.module.help.refreshing"));
    }
    else if (myStatsDataLoadingStatus == LoadStatus.FAILED) {
      myLoadingDataLabel.setText(message("android.wizard.project.loading.stats.fail"));
    }
  }

  private static String getApiHelpText(int selectedApi) {
    double percentage = DistributionService.getInstance().getSupportedDistributionForApiLevel(selectedApi) * 100;
    String percentageStr = percentage < 1 ? "<b>&lt; 1%</b>" :
                           format("approximately <b>" + (percentage >= 10 ? "%.3g%%" : "%.2g%%") + "</b>", percentage);
    return format("<html>Your app will run on %1$s of devices.</html>", percentageStr);
  }

  private void setTemplateThumbnail(@Nullable TemplateHandle templateHandle) {
    Image image = ActivityGallery.getTemplateImage(templateHandle);
    if (image != null) {
      // Template Icons have an invisible pixel border that stops them from aligning top and right, as specified by the design
      BufferedImage buffImg = ImageUtil.toBufferedImage(image);
      Rectangle imageExtents = ImageUtils.getCropBounds(buffImg, (img, x, y) -> (img.getRGB(x, y) & 0xFF000000) == 0, null);

      if (imageExtents != null) {
        // Crop away empty space to left and right of the image.
        buffImg = buffImg.getSubimage(imageExtents.x, 0, imageExtents.width, buffImg.getHeight());
      }
      Icon icon = new ImageIcon(buffImg.getScaledInstance((256 * buffImg.getWidth()) / buffImg.getHeight(), 256, Image.SCALE_SMOOTH));

      myTemplateIconTitle.setIcon(icon);
      myTemplateIconTitle.setText(ActivityGallery.getTemplateImageLabel(templateHandle));
      myTemplateIconDetail.setText("<html>" + ActivityGallery.getTemplateDescription(templateHandle) + "</html>");
    }
    myTemplateIconTitle.setVisible(image != null);
    myTemplateIconDetail.setVisible(image != null);
  }

  private void createUIComponents() {
    myProjectLanguage = new LanguageComboProvider().createComponent();
    myLoadingDataIcon = new AsyncProcessIcon(message("android.wizard.module.help.loading"));
  }
}
