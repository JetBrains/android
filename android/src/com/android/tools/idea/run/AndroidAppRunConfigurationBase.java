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
package com.android.tools.idea.run;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.run.activity.DefaultStartActivityFlagsProvider;
import com.android.tools.idea.run.activity.InstantAppStartActivityFlagsProvider;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor;
import com.android.tools.idea.run.editor.ApplicationRunParameters;
import com.android.tools.idea.run.editor.DeepLinkLaunch;
import com.android.tools.idea.run.editor.DefaultActivityLaunch;
import com.android.tools.idea.run.editor.LaunchOption;
import com.android.tools.idea.run.editor.LaunchOptionState;
import com.android.tools.idea.run.editor.NoLaunch;
import com.android.tools.idea.run.editor.SpecificActivityLaunch;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.ui.BaseAction;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.stats.RunStats;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.RunnerIconProvider;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.Icon;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AndroidAppRunConfigurationBase extends AndroidRunConfigurationBase implements RefactoringListenerProvider, RunnerIconProvider {
  @NonNls private static final String FEATURE_LIST_SEPARATOR = ",";

  @NonNls public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  @NonNls public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  @NonNls public static final String DO_NOTHING = "do_nothing";
  @NonNls public static final String LAUNCH_DEEP_LINK = "launch_deep_link";

  public static final List<? extends LaunchOption> LAUNCH_OPTIONS =
    Arrays.asList(NoLaunch.INSTANCE, DefaultActivityLaunch.INSTANCE, SpecificActivityLaunch.INSTANCE, DeepLinkLaunch.INSTANCE);

  // Deploy options
  public boolean DEPLOY = true;
  public boolean DEPLOY_APK_FROM_BUNDLE = false;
  public boolean DEPLOY_AS_INSTANT = false;
  public String ARTIFACT_NAME = "";
  public String PM_INSTALL_OPTIONS = "";
  public String DYNAMIC_FEATURES_DISABLED_LIST = "";

  // Launch options
  public String ACTIVITY_EXTRA_FLAGS = "";
  public String MODE = LAUNCH_DEFAULT_ACTIVITY;

  private final Map<String, LaunchOptionState> myLaunchOptionStates = Maps.newHashMap();

  public AndroidAppRunConfigurationBase(Project project, ConfigurationFactory factory) {
    super(project, factory, false);

    for (LaunchOption option : LAUNCH_OPTIONS) {
      myLaunchOptionStates.put(option.getId(), option.createState());
    }

    putUserData(BaseAction.SHOW_APPLY_CHANGES_UI, true);
  }

  @Override
  protected Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet) {
    return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
  }

  @NotNull
  @Override
  protected List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
    List<ValidationError> errors = new ArrayList<>();

    LaunchOptionState launchOptionState = getLaunchOptionState(MODE);
    if (launchOptionState != null) {
      errors.addAll(launchOptionState.checkConfiguration(facet));
    }
    errors.addAll(checkDeployConfiguration(facet));
    return errors;
  }

  @NotNull
  protected List<ValidationError> checkDeployConfiguration(@NotNull AndroidFacet facet) {
    List<ValidationError> errors = new ArrayList<>();
    if (DEPLOY && DEPLOY_APK_FROM_BUNDLE) {
      if (!DynamicAppUtils.supportsBundleTask(facet.getModule())) {
        ValidationError error = ValidationError.fatal("This option requires a newer version of the Android Gradle Plugin",
                                                      () -> DynamicAppUtils.promptUserForGradleUpdate(getProject()));
        errors.add(error);
      }
    }
    return errors;
  }

  @NotNull
  @Override
  protected LaunchOptions.Builder getLaunchOptions() {
    return super.getLaunchOptions()
      .setDeploy(DEPLOY)
      .setPmInstallOptions(PM_INSTALL_OPTIONS)
      .setDisabledDynamicFeatures(getDisabledDynamicFeatures())
      .setOpenLogcatAutomatically(SHOW_LOGCAT_AUTOMATICALLY)
      .setDeployAsInstant(DEPLOY_AS_INSTANT);
  }

  @NotNull
  public List<String> getDisabledDynamicFeatures() {
    if (StringUtil.isEmpty(DYNAMIC_FEATURES_DISABLED_LIST)) {
      return ImmutableList.of();
    }
    return StringUtil.split(DYNAMIC_FEATURES_DISABLED_LIST, FEATURE_LIST_SEPARATOR);
  }

  public void setDisabledDynamicFeatures(@NotNull List<String> features) {
    // Remove duplicates and sort to ensure deterministic behavior, as the value
    // is stored on disk (run configuration parameters).
    List<String> sortedFeatures = features.stream().distinct().sorted().collect(Collectors.toList());
    DYNAMIC_FEATURES_DISABLED_LIST = StringUtil.join(sortedFeatures, FEATURE_LIST_SEPARATOR);
  }

  @Override
  @NotNull
  protected ApkProvider getApkProvider(@NotNull AndroidFacet facet,
                                       @NotNull ApplicationIdProvider applicationIdProvider,
                                       @NotNull List<AndroidDevice> targetDevices) {
    if (AndroidModel.get(facet) != null && AndroidModel.get(facet) instanceof AndroidModuleModel) {
      return createGradleApkProvider(facet, applicationIdProvider, false, targetDevices);
    }
    ApkFacet apkFacet = ApkFacet.getInstance(facet.getModule());
    if (apkFacet != null) {
      return new FileSystemApkProvider(apkFacet.getModule(), new File(apkFacet.getConfiguration().APK_PATH));
    }
    return new NonGradleApkProvider(facet, applicationIdProvider, ARTIFACT_NAME);
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new AndroidRunConfigurationEditor<>(
      getProject(),
      facet -> false,
      this,
      true,
      moduleSelector -> new ApplicationRunParameters<>(getProject(), moduleSelector));
  }

  @Override
  @Nullable
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    // TODO: This is a bit of a hack: Currently, refactoring only affects the specific activity launch, so we directly peek into it and
    // change its state. The correct way of implementing this would be to delegate to all of the LaunchOptions and put the results into
    // a RefactoringElementListenerComposite
    final SpecificActivityLaunch.State state = (SpecificActivityLaunch.State)getLaunchOptionState(LAUNCH_SPECIFIC_ACTIVITY);
    assert state != null;
    return RefactoringListeners.getClassOrPackageListener(element, new RefactoringListeners.Accessor<PsiClass>() {
      @Override
      public void setName(String qualifiedName) {
        state.ACTIVITY_CLASS = qualifiedName;
      }

      @Nullable
      @Override
      public PsiClass getPsiElement() {
        return getConfigurationModule().findClass(state.ACTIVITY_CLASS);
      }

      @Override
      public void setPsiElement(PsiClass psiClass) {
        state.ACTIVITY_CLASS = JavaExecutionUtil.getRuntimeQualifiedName(psiClass);
      }
    });
  }

  @NotNull
  @Override
  protected ConsoleProvider getConsoleProvider() {
    return new ConsoleProvider() {
      @NotNull
      @Override
      public ConsoleView createAndAttach(@NotNull Disposable parent,
                                         @NotNull ProcessHandler handler,
                                         @NotNull Executor executor) throws ExecutionException {
        Project project = getConfigurationModule().getProject();
        final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
        ConsoleView console = builder.getConsole();
        console.attachToProcess(handler);
        return console;
      }
    };
  }

  @Override
  protected boolean supportMultipleDevices() {
    return true;
  }

  @Nullable
  @Override
  protected LaunchTask getApplicationLaunchTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                                @NotNull AndroidFacet facet,
                                                @NotNull String contributorsAmStartOptions,
                                                boolean waitForDebugger,
                                                @NotNull LaunchStatus launchStatus) {
    LaunchOptionState state = getLaunchOptionState(MODE);
    assert state != null;

    String extraFlags = ACTIVITY_EXTRA_FLAGS;
    if (!contributorsAmStartOptions.isEmpty()) {
      extraFlags += (extraFlags.isEmpty() ? "" : " ") + contributorsAmStartOptions;
    }

    StartActivityFlagsProvider startActivityFlagsProvider;
    if (facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      startActivityFlagsProvider = new InstantAppStartActivityFlagsProvider();
    }
    else {
      startActivityFlagsProvider = new DefaultStartActivityFlagsProvider(
        getAndroidDebuggerContext().getAndroidDebugger(),
        getAndroidDebuggerContext().getAndroidDebuggerState(),
        getProfilerState(),
        getProject(),
        waitForDebugger,
        extraFlags);
    }

    try {
      return state.getLaunchTask(applicationIdProvider.getPackageName(), facet, startActivityFlagsProvider, getProfilerState());
    }
    catch (ApkProvisionException e) {
      Logger.getInstance(AndroidAppRunConfigurationBase.class).error(e);
      launchStatus.terminateLaunch("Unable to identify application id", true);
      return null;
    }
  }

  public void setLaunchActivity(@NotNull String activityName) {
    MODE = LAUNCH_SPECIFIC_ACTIVITY;

    // TODO: we probably need a better way to do this rather than peeking into the option state
    // Possibly something like setLaunch(LAUNCH_SPECIFIC_ACTIVITY, SpecificLaunchActivity.state(className))
    LaunchOptionState state = getLaunchOptionState(LAUNCH_SPECIFIC_ACTIVITY);
    assert state instanceof SpecificActivityLaunch.State;
    ((SpecificActivityLaunch.State)state).ACTIVITY_CLASS = activityName;
  }

  public void setLaunchUrl(@NotNull String url) {
    MODE = LAUNCH_DEEP_LINK;

    final LaunchOptionState state = getLaunchOptionState(LAUNCH_DEEP_LINK);
    assert state instanceof DeepLinkLaunch.State;
    ((DeepLinkLaunch.State)state).DEEP_LINK = url;
  }

  public boolean isLaunchingActivity(@Nullable String activityName) {
    if (!StringUtil.equals(MODE, LAUNCH_SPECIFIC_ACTIVITY)) {
      return false;
    }

    // TODO: we probably need a better way to do this rather than peeking into the option state, possibly just delegate equals to the option
    LaunchOptionState state = getLaunchOptionState(LAUNCH_SPECIFIC_ACTIVITY);
    assert state instanceof SpecificActivityLaunch.State;
    return StringUtil.equals(((SpecificActivityLaunch.State)state).ACTIVITY_CLASS, activityName);
  }

  @Nullable
  public LaunchOptionState getLaunchOptionState(@NotNull String launchOptionId) {
    return myLaunchOptionStates.get(launchOptionId);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);

    for (LaunchOptionState state : myLaunchOptionStates.values()) {
      DefaultJDOMExternalizer.readExternal(state, element);
    }

    // Ensure invariant in case persisted state is manually edited or corrupted for some reason
    if (DEPLOY_APK_FROM_BUNDLE) {
      DEPLOY=true;
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);

    for (LaunchOptionState state : myLaunchOptionStates.values()) {
      DefaultJDOMExternalizer.writeExternal(state, element);
    }
  }

  @Nullable
  @Override
  public Icon getExecutorIcon(@NotNull RunConfiguration configuration, @NotNull Executor executor) {
    // Customize the executor icon for the DeviceAndSnapshotComboBoxAction such that it's tied to the device in addition to the
    // RunConfiguration.
    Project project = configuration.getProject();
    final ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(project);

    // This code is lifted out of ExecutionRegistryImpl:getInformativeIcon to maintain the same functionality.
    // I *believe* this code is attempting to find all running content (as in, Run/Debug tool window's tabs' contents)
    // that are still running and have not been detached from the Executor or the ContentManager.
    List<RunContentDescriptor> runningDescriptors =
      executionManager.getRunningDescriptors(s -> s != null && s.getConfiguration() == configuration);
    runningDescriptors = runningDescriptors.stream().filter(descriptor -> {
      RunContentDescriptor contentDescriptor =
        executionManager.getContentManager().findContentDescriptor(executor, descriptor.getProcessHandler());
      return contentDescriptor != null && executionManager.getExecutors(contentDescriptor).contains(executor);
    }).collect(Collectors.toList());

    ExecutionTarget executionTarget = ExecutionTargetManager.getInstance(project).getActiveTarget();
    ApplicationIdProvider applicationIdProvider = getApplicationIdProvider();
    String applicationId = null;
    try {
      applicationId = applicationIdProvider == null ? null : applicationIdProvider.getPackageName();
    }
    catch (ApkProvisionException ignored) {
    }
    boolean isRunning =
      executionTarget instanceof AndroidExecutionTarget &&
      applicationId != null &&
      ((AndroidExecutionTarget)executionTarget).isApplicationRunning(applicationId);

    if (DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId()) && !runningDescriptors.isEmpty() && isRunning) {
      // Use the system's restart icon for the default run executor.
      return AllIcons.Actions.Restart;
    }

    // Defer to the executor for the icon, since this RunConfiguration class doesn't provide its own icon.
    Icon executorIcon = executor instanceof ExecutorIconProvider ?
                        ((ExecutorIconProvider)executor).getExecutorIcon(getProject(), executor) :
                        executor.getIcon();
    if (runningDescriptors.isEmpty() || !isRunning) {
      return executorIcon;
    }
    else {
      return ExecutionUtil.getLiveIndicator(executorIcon);
    }
  }

  @Override
  public void updateExtraRunStats(RunStats runStats) {
    runStats.setDeployedAsInstant(DEPLOY_AS_INSTANT);
    runStats.setDeployedFromBundle(DEPLOY_APK_FROM_BUNDLE);
  }
}
