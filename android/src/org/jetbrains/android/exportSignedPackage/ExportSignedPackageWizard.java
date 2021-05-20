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

import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.openapi.util.text.StringUtil.decapitalize;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardClosed;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardGradleSigning;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardGradleSigningFailed;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardIntellijSigning;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardIntellijSigningFailed;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardOkAction;
import static org.jetbrains.android.exportSignedPackage.SigningWizardUsageTrackerUtilsKt.trackWizardOpen;

import com.android.builder.model.AndroidProject;
import com.android.sdklib.BuildToolInfo;
import com.android.tools.idea.gradle.actions.GoToApkLocationTask;
import com.android.tools.idea.gradle.actions.GoToBundleLocationTask;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.SigningWizardEvent;
import com.google.wireless.android.vending.developer.signing.tools.extern.export.ExportEncryptedPrivateKeyTool;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import org.jetbrains.android.AndroidCommonBundle;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExportSignedPackageWizard extends AbstractWizard<ExportSignedPackageWizardStep> {
  public static final String BUNDLE = "bundle";
  public static final String APK = "apk";
  private static final String ENCRYPTED_PRIVATE_KEY_FILE = "private_key.pepk";
  private static final String GOOGLE_PUBLIC_KEY =
    "eb10fe8f7c7c9df715022017b00c6471f8ba8170b13049a11e6c09ffe3056a104a3bbe4ac5a955f4ba4fe93fc8cef27558a3eb9d2a529a2092761fb833b656cd48b9de6a";

  private static Logger getLog() {
    return Logger.getInstance(ExportSignedPackageWizard.class);
  }

  @NotNull private final Project myProject;

  private AndroidFacet myFacet;
  private PrivateKey myPrivateKey;
  private X509Certificate myCertificate;

  private boolean mySigned;
  private CompileScope myCompileScope;
  private String myApkPath;
  private String myExportKeyPath;
  @NotNull private String myTargetType = APK;

  // build variants and gradle signing info are valid only for Gradle projects
  @NotNull private ExportEncryptedPrivateKeyTool myEncryptionTool;
  private boolean myExportPrivateKey;
  private List<String> myBuildVariants;
  private GradleSigningInfo myGradleSigningInfo;


  public ExportSignedPackageWizard(@NotNull Project project,
                                   @NotNull List<AndroidFacet> facets,
                                   boolean signed,
                                   Boolean showBundle,
                                   @NotNull ExportEncryptedPrivateKeyTool encryptionTool) {
    super(AndroidBundle.message(showBundle ? "android.export.package.wizard.bundle.title" : "android.export.package.wizard.title"),
          project);

    myProject = project;
    mySigned = signed;
    myEncryptionTool = encryptionTool;
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
    if (AndroidModel.isRequired(myFacet)) {
      buildAndSignGradleProject();
    }
    else {
      buildAndSignIntellijProject();
    }
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

  private void buildAndSignIntellijProject() {
    CompilerManager.getInstance(myProject).make(myCompileScope, (aborted, errors, warnings, compileContext) -> {
      if (aborted) {
        trackWizardGradleSigningFailed(myProject, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_COMPILE_ABORTED);
        return;
      }
      if (errors != 0) {
        trackWizardGradleSigningFailed(myProject, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_COMPILE_ERRORS);
        return;
      }

      String title = AndroidBundle.message("android.extract.package.task.title");
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, true, null) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          createAndAlignApk(myApkPath);
        }
      });
    });
  }

  private void buildAndSignGradleProject() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Generating Signed APKs", false, null) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GradleFacet gradleFacet = GradleFacet.getInstance(myFacet.getModule());
        if (gradleFacet == null) {
          getLog().error("Unable to get gradle project information for module: " + myFacet.getModule().getName());
          trackWizardGradleSigningFailed(myProject, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_NO_MODULE_FACET);
          return;
        }
        String gradleProjectPath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(myFacet.getModule());
        if (StringUtil.isEmpty(rootProjectPath)) {
          getLog().error("Unable to get gradle root project path for module: " + myFacet.getModule().getName());
          trackWizardGradleSigningFailed(myProject, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_NO_MODULE_ROOT_PATH);
          return;
        }

        // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
        AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
        if (androidModel == null) {
          getLog().error("Unable to obtain Android project model. Did the last Gradle sync complete successfully?");
          trackWizardGradleSigningFailed(myProject, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_NO_ANDROID_MODEL);
          return;
        }

        // should have been set by previous steps
        if (myBuildVariants == null) {
          getLog().error("Unable to find required information. Please check the previous steps are completed.");
          trackWizardGradleSigningFailed(myProject, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_NO_VARIANTS_SELECTED);
          return;
        }
        List<String> gradleTasks = getGradleTasks(gradleProjectPath, androidModel, myBuildVariants, myTargetType);
        List<String> projectProperties = Lists.newArrayList();
        projectProperties.add(createProperty(AndroidProject.PROPERTY_SIGNING_STORE_FILE, myGradleSigningInfo.keyStoreFilePath));
        projectProperties
          .add(createProperty(AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD, new String(myGradleSigningInfo.keyStorePassword)));
        projectProperties.add(createProperty(AndroidProject.PROPERTY_SIGNING_KEY_ALIAS, myGradleSigningInfo.keyAlias));
        projectProperties.add(createProperty(AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD, new String(myGradleSigningInfo.keyPassword)));
        projectProperties.add(createProperty(AndroidProject.PROPERTY_APK_LOCATION, myApkPath));

        assert myProject != null;

        GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(myProject);
        List<Module> modules = ImmutableList.of(myFacet.getModule());
        SigningWizardEvent.SigningTargetType targetType;
        boolean isKeyExported = false;
        if (myTargetType.equals(BUNDLE)) {
          targetType = SigningWizardEvent.SigningTargetType.TARGET_TYPE_BUNDLE;
          File exportedKeyFile = null;
          if (myExportPrivateKey) {
            isKeyExported = true;
            exportedKeyFile = generatePrivateKeyPath();
            try {
              myEncryptionTool.run(myGradleSigningInfo.keyStoreFilePath,
                                   myGradleSigningInfo.keyAlias,
                                   GOOGLE_PUBLIC_KEY,
                                   exportedKeyFile.getPath(),
                                   myGradleSigningInfo.keyStorePassword,
                                   myGradleSigningInfo.keyPassword
              );

              final GenerateSignedApkSettings settings = GenerateSignedApkSettings.getInstance(myProject);
              //We want to only export the private key once. Anymore would be redundant.
              settings.EXPORT_PRIVATE_KEY = false;
            }
            catch (Exception e) {
              getLog().error("Something went wrong with the encryption tool", e);
              trackWizardGradleSigningFailed(myProject, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_ENCRYPTION_ERROR);
              return;
            }
          }
          gradleBuildInvoker.add(new GoToBundleLocationTask(myProject,
                                                            modules,
                                                            "Generate Signed Bundle",
                                                            myBuildVariants, exportedKeyFile, myApkPath));
        }
        else {
          targetType = SigningWizardEvent.SigningTargetType.TARGET_TYPE_APK;
          gradleBuildInvoker.add(new GoToApkLocationTask(myProject, modules, "Generate Signed APK", myBuildVariants, myApkPath));
        }
        final File file = new File(rootProjectPath);
        gradleBuildInvoker.executeAssembleTasks(
          modules.toArray(new Module[0]),
          ImmutableList.of(
            GradleBuildInvoker.Request.builder(gradleBuildInvoker.getProject(), file, gradleTasks)
              .setCommandLineArguments(projectProperties)
              .build())
        );
        trackWizardGradleSigning(myProject, targetType, modules.size(), myBuildVariants.size(), isKeyExported);

        getLog().info("Export " + StringUtil.toUpperCase(myTargetType) + " command: " +
                      Joiner.on(',').join(gradleTasks) +
                      ", destination: " +
                      createProperty(AndroidProject.PROPERTY_APK_LOCATION, myApkPath));
      }

      private String createProperty(@NotNull String name, @NotNull String value) {
        return AndroidGradleSettings.createProjectProperty(name, value);
      }
    });
  }

  @VisibleForTesting
  @NotNull
  static File getApkLocation(@NotNull String apkFolderPath, @NotNull String buildType) {
    return new File(apkFolderPath, buildType);
  }

  @VisibleForTesting
  @NotNull
  static List<String> getGradleTasks(@NotNull String gradleProjectPath,
                                     @NotNull AndroidModuleModel androidModuleModel,
                                     @NotNull List<String> buildVariants,
                                     @NotNull String targetType) {
    List<String> taskNames;
    if (androidModuleModel.getFeatures().isBuildOutputFileSupported()) {
      taskNames = getTaskNamesFromBuildInformation(androidModuleModel, buildVariants, targetType);
    }
    else {
      IdeVariant selectedVariant = androidModuleModel.getSelectedVariant();
      String selectedTaskName = getTaskName(selectedVariant, targetType);
      if (selectedTaskName == null) {
        getLog().warn("Could not get tasks for target " + targetType + " on variant " + selectedVariant.getName());
        return Collections.emptyList();
      }
      taskNames = getTaskNamesFromSelectedVariant(buildVariants, selectedVariant.getName(), selectedTaskName);
    }
    return ContainerUtil.map(taskNames, name -> GradleUtil.createFullTaskName(gradleProjectPath, name));
  }

  @NotNull
  private static List<String> getTaskNamesFromBuildInformation(@NotNull AndroidModuleModel androidModuleModel,
                                                               @NotNull List<String> buildVariants,
                                                               @NotNull String targetType) {
    List<String> taskNames = Lists.newArrayListWithExpectedSize(buildVariants.size());
    Map<String, IdeVariantBuildInformation> buildInformationByVariantName =
      androidModuleModel.getAndroidProject().getVariantsBuildInformation().stream()
        .collect(Collectors.toMap(x -> x.getVariantName(), x -> x));

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
    getFinishButton().setEnabled(currentStep.canFinish());

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

  public void setFacet(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  public void setPrivateKey(@NotNull PrivateKey privateKey) {
    myPrivateKey = privateKey;
  }

  public void setCertificate(@NotNull X509Certificate certificate) {
    myCertificate = certificate;
  }

  public PrivateKey getPrivateKey() {
    return myPrivateKey;
  }

  public X509Certificate getCertificate() {
    return myCertificate;
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

  public void setTargetType(@NotNull String targetType) {
    myTargetType = targetType;
  }

  @NotNull
  public String getTargetType() {
    return myTargetType;
  }

  private void createAndAlignApk(final String apkPath) {
    AndroidPlatform platform = AndroidPlatform.getInstance(getFacet().getModule());
    assert platform != null;
    BuildToolInfo buildTool = platform.getTarget().getBuildToolInfo();
    String zipAlignPath = buildTool.getPath(BuildToolInfo.PathId.ZIP_ALIGN);
    File zipalign = new File(zipAlignPath);

    final boolean runZipAlign = zipalign.isFile();
    File destFile = null;
    try {
      destFile = runZipAlign ? FileUtil.createTempFile("android", ".apk") : new File(apkPath);
      createApk(destFile);
    }
    catch (Exception e) {
      showErrorInDispatchThread(e.getMessage());
    }
    if (destFile == null) {
      trackWizardIntellijSigningFailed(myProject, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_CANNOT_CREATE_APK);
      return;
    }

    if (runZipAlign) {
      File realDestFile = new File(apkPath);
      String message = AndroidBuildCommonUtils.executeZipAlign(zipAlignPath, destFile, realDestFile);
      if (message != null) {
        showErrorInDispatchThread(message);
        trackWizardIntellijSigningFailed(myProject, SigningWizardEvent.SigningWizardFailureCause.FAILURE_CAUSE_ZIP_ALIGN_ERROR);
        return;
      }
    }
    GuiUtils.invokeLaterIfNeeded(() -> {
      String title = AndroidBundle.message("android.export.package.wizard.title");
      Project project = getProject();
      File apkFile = new File(apkPath);

      VirtualFile vApkFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(apkFile);
      if (vApkFile != null) {
        vApkFile.refresh(true, false);
      }

      if (!runZipAlign) {
        Messages.showWarningDialog(project, AndroidCommonBundle.message(
          "android.artifact.building.cannot.find.zip.align.error"), title);
      }

      trackWizardIntellijSigning(myProject);
      if (RevealFileAction.isSupported()) {
        if (Messages.showOkCancelDialog(project, AndroidBundle.message("android.export.package.success.message", apkFile.getName()),
                                        title, RevealFileAction.getActionName(), IdeBundle.message("action.close"),
                                        Messages.getInformationIcon()) == Messages.OK) {
          RevealFileAction.openFile(apkFile);
        }
      }
      else {
        Messages.showInfoMessage(project, AndroidBundle.message("android.export.package.success.message", apkFile), title);
      }
    }, ModalityState.defaultModalityState());
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private void createApk(@NotNull File destFile) throws IOException, GeneralSecurityException {
    String srcApkPath = AndroidCompileUtil.getUnsignedApkPath(getFacet());
    assert srcApkPath != null;
    File srcApk = new File(FileUtil.toSystemDependentName(srcApkPath));

    if (isSigned()) {
      AndroidBuildCommonUtils.signApk(srcApk, destFile, getPrivateKey(), getCertificate());
    }
    else {
      FileUtil.copy(srcApk, destFile);
    }
  }

  @NotNull
  private File generatePrivateKeyPath() {
    return new File(myExportKeyPath, ENCRYPTED_PRIVATE_KEY_FILE);
  }

  private void showErrorInDispatchThread(@NotNull final String message) {
    invokeLaterIfNeeded(() -> Messages.showErrorDialog(getProject(), "Error: " + message, CommonBundle.getErrorTitle()));
  }

  public void setGradleSigningInfo(GradleSigningInfo gradleSigningInfo) {
    myGradleSigningInfo = gradleSigningInfo;
  }

  public void setExportPrivateKey(boolean exportPrivateKey) {
    myExportPrivateKey = exportPrivateKey;
  }

  public void setExportKeyPath(@NotNull String exportKeyPath) {
    myExportKeyPath = exportKeyPath;
  }
}
