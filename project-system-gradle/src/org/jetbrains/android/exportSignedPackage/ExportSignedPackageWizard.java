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

package org.jetbrains.android.exportSignedPackage;

import static com.android.builder.model.InjectedProperties.PROPERTY_APK_LOCATION;
import static com.android.builder.model.InjectedProperties.PROPERTY_SIGNING_KEY_ALIAS;
import static com.android.builder.model.InjectedProperties.PROPERTY_SIGNING_KEY_PASSWORD;
import static com.android.builder.model.InjectedProperties.PROPERTY_SIGNING_STORE_FILE;
import static com.android.builder.model.InjectedProperties.PROPERTY_SIGNING_STORE_PASSWORD;
import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.openapi.util.text.StringUtil.decapitalize;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardClosed;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardGradleSigning;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardGradleSigningFailed;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardOkAction;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardOpen;

import com.android.tools.idea.gradle.actions.GoToApkLocationTask;
import com.android.tools.idea.gradle.actions.GoToBundleLocationTask;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation;
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPathKt;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.SigningWizardEvent;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExportSignedPackageWizard extends AbstractWizard<ExportSignedPackageWizardStep> {
  public static final String BUNDLE = "bundle";
  public static final String APK = "apk";
  @NotNull private final Project myProject;
  private final boolean mySigned;
  // build variants and gradle signing info are valid only for Gradle projects
  private AndroidFacet myFacet;
  private PrivateKey myPrivateKey;
  private X509Certificate myCertificate;
  private CompileScope myCompileScope;
  private String myApkPath;
  @NotNull private String myTargetType = APK;
  private List<String> myBuildVariants;
  private GradleSigningInfo myGradleSigningInfo;

  public ExportSignedPackageWizard(@NotNull Project project,
                                   @NotNull List<AndroidFacet> facets,
                                   boolean signed,
                                   Boolean showBundle) {
    super(AndroidBundle.message(showBundle ? "android.export.package.wizard.bundle.title" : "android.export.package.wizard.title"),
          project);

    myProject = project;
    mySigned = signed;
    assert !facets.isEmpty();
    myFacet = facets.get(0);
    if (showBundle) {
      addStep(new ChooseBundleOrApkStep(this));
    }
    boolean useGradleToSign = AndroidModel.isRequired(myFacet);

    if (signed) {
      addStep(new KeystoreStep(this, useGradleToSign, facets));
    }

    if (useGradleToSign) {
      addStep(new GradleSignStep(this));
    }
    else {
      addStep(new ApkStep(this));
    }
    init();
  }

  public boolean isSigned() {
    return mySigned;
  }

  @Override
  protected void doOKAction() {
    if (!commitCurrentStep()) {
      return;
    }
    trackWizardOkAction(myProject);
    super.doOKAction();

    assert myFacet != null;
    assert AndroidModel.isRequired(myFacet);
    buildAndSignGradleProject();
  }

  @Override
  public void doCancelAction() {
    trackWizardClosed(myProject);
    super.doCancelAction();
  }

  @Override
  public void show() {
    trackWizardOpen(myProject);
    super.show();
  }

  private void buildAndSignGradleProject() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Generating Signed APKs", false, null) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        List<Module> modules = ImmutableList.of(myFacet.getMainModule());
        Consumer<ListenableFuture<AssembleInvocationResult>> buildHandler = prepareBuildResultHandler(modules);
        if (buildHandler == null) {
          // Nothing to do, there was an error detected while generating the result handler (and was already logged)
          return;
        }
        doBuildAndSignGradleProject(myProject, myFacet, myBuildVariants, modules, myGradleSigningInfo, myApkPath, myTargetType,
                                    buildHandler);
        trackWizardGradleSigning(myProject, toSigningTargetType(myTargetType), modules.size(), myBuildVariants.size());
      }
    });
  }

  private Consumer<ListenableFuture<AssembleInvocationResult>> prepareBuildResultHandler(@NotNull List<Module> modules) {
    if (myTargetType.equals(BUNDLE)) {
      return new GoToBundleLocationTask(myProject, modules, "Generate Signed Bundle", myBuildVariants)::executeWhenBuildFinished;
    }
    else {
      return new GoToApkLocationTask(myProject, modules, "Generate Signed APK", myBuildVariants)::executeWhenBuildFinished;
    }
  }

  @Override
  protected void doNextAction() {
    if (!commitCurrentStep()) return;
    super.doNextAction();
  }

  private boolean commitCurrentStep() {
    try {
      mySteps.get(myCurrentStep).commitForNext();
    }
    catch (CommitStepException e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage());
      return false;
    }
    return true;
  }

  @Override
  protected int getNextStep(int stepIndex) {
    int result = super.getNextStep(stepIndex);
    if (result != myCurrentStep) {
      mySteps.get(result).setPreviousStepIndex(myCurrentStep);
    }
    return result;
  }

  @Override
  protected int getPreviousStep(int stepIndex) {
    ExportSignedPackageWizardStep step = mySteps.get(stepIndex);
    int prevStepIndex = step.getPreviousStepIndex();
    assert prevStepIndex >= 0;
    return prevStepIndex;
  }

  @Override
  protected void updateStep() {
    int step = getCurrentStep();
    final ExportSignedPackageWizardStep currentStep = mySteps.get(step);

    super.updateStep();

    invokeLaterIfNeeded(() -> {
      getRootPane().setDefaultButton(getNextButton());
      JComponent component = currentStep.getPreferredFocusedComponent();
      if (component != null) {
        component.requestFocus();
      }
    });
  }

  @Override
  protected String getHelpID() {
    ExportSignedPackageWizardStep step = getCurrentStepObject();
    if (step != null) {
      return step.getHelpId();
    }
    return null;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  public PrivateKey getPrivateKey() {
    return myPrivateKey;
  }

  public void setPrivateKey(@NotNull PrivateKey privateKey) {
    myPrivateKey = privateKey;
  }

  public X509Certificate getCertificate() {
    return myCertificate;
  }

  public void setCertificate(@NotNull X509Certificate certificate) {
    myCertificate = certificate;
  }

  public void setCompileScope(@NotNull CompileScope compileScope) {
    myCompileScope = compileScope;
  }

  public void setApkPath(@NotNull String apkPath) {
    myApkPath = apkPath;
  }

  public void setGradleOptions(@NotNull List<String> buildVariants) {
    myBuildVariants = buildVariants;
  }

  @NotNull
  public String getTargetType() {
    return myTargetType;
  }

  public void setTargetType(@NotNull String targetType) {
    myTargetType = targetType;
  }

  public void setGradleSigningInfo(GradleSigningInfo gradleSigningInfo) {
    myGradleSigningInfo = gradleSigningInfo;
  }

  private static Logger getLog() {
    return Logger.getInstance(ExportSignedPackageWizard.class);
  }

  static private SigningWizardEvent.SigningTargetType toSigningTargetType(@NotNull String targetType) {
    if (targetType.equals(BUNDLE)) {
      return SigningWizardEvent.SigningTargetType.TARGET_TYPE_BUNDLE;
    }
    if (targetType.equals(APK)) {
      return SigningWizardEvent.SigningTargetType.TARGET_TYPE_APK;
    }
    return SigningWizardEvent.SigningTargetType.TARGET_TYPE_UNKNOWN;
  }

  @VisibleForTesting
  static void doBuildAndSignGradleProject(Project project,
                                          AndroidFacet facet,
                                          List<String> variants,
                                          List<Module> modules,
                                          GradleSigningInfo signInfo,
                                          String apkPath,
                                          String targetType,
                                          Consumer<ListenableFuture<AssembleInvocationResult>> buildResultHandler) {
    assert project != null;
    @NotNull Module facetModule = facet.getModule();
    GradleFacet gradleFacet = GradleFacet.getInstance(facetModule);
    if (gradleFacet == null) {
      getLog().error("Unable to get gradle project information for module: " + facetModule.getName());
      trackWizardGradleSigningFailed(project, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_NO_MODULE_FACET);
      return;
    }

    GradleProjectPath gradleProjectPath = GradleProjectPathKt.getGradleProjectPath(facetModule);
    if (gradleProjectPath == null) {
      getLog().error("Unable to get gradle project information for module: " + facetModule.getName());
      trackWizardGradleSigningFailed(project, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_NO_MODULE_FACET);
      return;
    }

    String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(facetModule);
    if (StringUtil.isEmpty(rootProjectPath)) {
      getLog().error("Unable to get gradle root project path for module: " + facetModule.getName());
      trackWizardGradleSigningFailed(project, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_NO_MODULE_ROOT_PATH);
      return;
    }

    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    GradleAndroidModel androidModel = GradleAndroidModel.get(facet);
    if (androidModel == null) {
      getLog().error("Unable to obtain Android project model. Did the last Gradle sync complete successfully?");
      trackWizardGradleSigningFailed(project, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_NO_ANDROID_MODEL);
      return;
    }

    // should have been set by previous steps
    if (variants == null) {
      getLog().error("Unable to find required information. Please check the previous steps are completed.");
      trackWizardGradleSigningFailed(project, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_NO_VARIANTS_SELECTED);
      return;
    }

    List<String> signingProperties = prepareSigningProperties(signInfo, apkPath);
    final File rootProjectFile = new File(rootProjectPath);

    GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);
    List<String> gradleTasks = getGradleTasks(gradleProjectPath.getPath(), androidModel, variants, targetType);
    buildResultHandler.consume(
      gradleBuildInvoker.executeAssembleTasks(
        modules.toArray(new Module[0]),
        ImmutableList.of(
          GradleBuildInvoker.Request.builder(gradleBuildInvoker.getProject(), rootProjectFile, gradleTasks, null)
            .setCommandLineArguments(signingProperties)
            .setMode(getBuildModeFromTarget(targetType))
            .build()))
    );
    getLog().info("Export " + StringUtil.toUpperCase(targetType) + " command: " +
                  Joiner.on(',').join(gradleTasks) +
                  ", destination: " +
                  createProperty(PROPERTY_APK_LOCATION, apkPath));
  }

  @NotNull
  @VisibleForTesting
  static List<String> prepareSigningProperties(@NotNull GradleSigningInfo signingInfo, @NotNull String apkPath) {
    List<String> projectProperties = new ArrayList<>();
    projectProperties.add(createProperty(PROPERTY_SIGNING_STORE_FILE, signingInfo.keyStoreFilePath));
    projectProperties
      .add(createProperty(PROPERTY_SIGNING_STORE_PASSWORD, new String(signingInfo.keyStorePassword)));
    projectProperties.add(createProperty(PROPERTY_SIGNING_KEY_ALIAS, signingInfo.keyAlias));
    projectProperties.add(createProperty(PROPERTY_SIGNING_KEY_PASSWORD, new String(signingInfo.keyPassword)));
    projectProperties.add(createProperty(PROPERTY_APK_LOCATION, apkPath));
    return projectProperties;
  }

  private static String createProperty(@NotNull String name, @NotNull String value) {
    return AndroidGradleSettings.createProjectProperty(name, value);
  }

  @VisibleForTesting
  @Nullable
  static BuildMode getBuildModeFromTarget(@NotNull String targetType) {
    if (targetType.equals(APK)) {
      return BuildMode.ASSEMBLE;
    }
    if (targetType.equals(BUNDLE)) {
      return BuildMode.BUNDLE;
    }
    return null;
  }

  @VisibleForTesting
  @NotNull
  static File getApkLocation(@NotNull String apkFolderPath, @NotNull String buildType) {
    return new File(apkFolderPath, buildType);
  }

  @VisibleForTesting
  @NotNull
  static List<String> getGradleTasks(@NotNull String gradleProjectPath,
                                     @NotNull GradleAndroidModel GradleAndroidModel,
                                     @NotNull List<String> buildVariants,
                                     @NotNull String targetType) {
    List<String> taskNames;
    if (GradleAndroidModel.getFeatures().isBuildOutputFileSupported()) {
      taskNames = getTaskNamesFromBuildInformation(GradleAndroidModel, buildVariants, targetType);
    }
    else {
      IdeVariant selectedVariant = GradleAndroidModel.getSelectedVariant();
      String selectedTaskName = getTaskName(selectedVariant, targetType);
      if (selectedTaskName == null) {
        getLog().warn("Could not get tasks for target " + targetType + " on variant " + selectedVariant.getName());
        return Collections.emptyList();
      }
      taskNames = getTaskNamesFromSelectedVariant(buildVariants, selectedVariant.getName(), selectedTaskName);
    }
    return ContainerUtil.map(taskNames, name -> GradleProjectSystemUtil.createFullTaskName(gradleProjectPath, name));
  }

  @NotNull
  private static List<String> getTaskNamesFromBuildInformation(@NotNull GradleAndroidModel GradleAndroidModel,
                                                               @NotNull List<String> buildVariants,
                                                               @NotNull String targetType) {
    List<String> taskNames = Lists.newArrayListWithExpectedSize(buildVariants.size());
    Map<String, IdeVariantBuildInformation> buildInformationByVariantName =
      GradleAndroidModel.getAndroidProject().getVariantsBuildInformation().stream()
        .collect(Collectors.toMap(IdeVariantBuildInformation::getVariantName, x -> x));

    for (String variantName : buildVariants) {
      IdeVariantBuildInformation buildInformation = buildInformationByVariantName.get(variantName);
      if (buildInformation == null) {
        getLog().warn("Could not get tasks for target " + targetType + " on variant " + variantName);
      }
      else {
        String taskName = targetType.equals(BUNDLE)
                          ? buildInformation.getBuildInformation().getBundleTaskName()
                          : buildInformation.getBuildInformation().getAssembleTaskName();
        taskNames.add(taskName);
      }
    }
    return taskNames;
  }

  @VisibleForTesting
  @NotNull
  static List<String> getTaskNamesFromSelectedVariant(@NotNull List<String> buildVariants,
                                                      @NotNull String selectedVariantName,
                                                      @NotNull String selectedTaskName) {
    List<String> gradleTasks = Lists.newArrayListWithExpectedSize(buildVariants.size());
    for (String variantName : buildVariants) {
      String taskName = replaceVariantFromTask(selectedTaskName, selectedVariantName, variantName);
      if (taskName != null) {
        gradleTasks.add(taskName);
      }
      else {
        getLog().warn("Could not replace variant " + selectedVariantName + " with " + variantName + " for task " + selectedTaskName + ".");
      }
    }
    return gradleTasks;
  }

  @VisibleForTesting
  @Nullable
  static String replaceVariantFromTask(@NotNull String task, @NotNull String oldVariant, @NotNull String newVariant) {
    oldVariant = decapitalize(oldVariant);
    if (task.indexOf(oldVariant) == 1) {
      // it has the pattern ":variantName[suffix]".
      newVariant = decapitalize(newVariant);
      return task.replaceFirst(oldVariant, newVariant);
    }
    oldVariant = capitalize(oldVariant);
    if (task.contains(oldVariant)) {
      // it has the pattern ":prefixVariantName[suffix]".
      newVariant = capitalize(newVariant);
      return task.replaceFirst(oldVariant, newVariant);
    }
    // Variant name could not be found capitalized as expected.
    return null;
  }

  private static String getTaskName(IdeVariant v, String targetType) {
    if (targetType.equals(BUNDLE)) {
      return v.getMainArtifact().getBuildInformation().getBundleTaskName();
    }
    else {
      return v.getMainArtifact().getBuildInformation().getAssembleTaskName();
    }
  }
}
