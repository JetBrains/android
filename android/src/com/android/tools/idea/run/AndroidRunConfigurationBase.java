// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.run;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_FEATURE;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_TEST;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerContext;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor;
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutorRunProfileState;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetContext;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.editor.RunConfigurationWithDebugger;
import com.android.tools.idea.run.tasks.AppLaunchTask;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.stats.RunStats;
import com.android.tools.idea.stats.RunStatsService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base {@link com.intellij.execution.configurations.RunConfiguration} for all Android run configs.
 * <p>
 * This class serves as the base model of Android build + run execution data.
 * <p>
 * Note this class inherits from {@link RunConfigurationWithSuppressedDefaultDebugAction} so as to
 * prevent the {@link com.intellij.debugger.impl.GenericDebuggerRunner} from recognizing this run
 * config as runnable. This allows Studio to disable the debug button when this type of run config
 * is selected, but the Debug action doesn't support running on multiple devices.
 */
public abstract class AndroidRunConfigurationBase extends ModuleBasedConfiguration<AndroidRunConfigurationModule, Element>
  implements PreferGradleMake, RunConfigurationWithSuppressedDefaultRunAction, RunConfigurationWithSuppressedDefaultDebugAction,
             RunConfigurationWithDebugger {

  /**
   * Element name used to group the {@link ProfilerState} settings
   */
  private static final String PROFILERS_ELEMENT_NAME = "Profilers";

  public boolean CLEAR_LOGCAT = false;
  public boolean SHOW_LOGCAT_AUTOMATICALLY = false;
  public boolean INSPECTION_WITHOUT_ACTIVITY_RESTART = false; // set global attributes at launch time

  private final ProfilerState myProfilerState;

  private final DeployTargetContext myDeployTargetContext = new DeployTargetContext();
  private final AndroidDebuggerContext myAndroidDebuggerContext = new AndroidDebuggerContext(AndroidJavaDebugger.ID);
  private final boolean myIsTestConfiguration;

  public AndroidRunConfigurationBase(Project project, ConfigurationFactory factory, boolean isTestConfiguration) {
    super(new AndroidRunConfigurationModule(project, isTestConfiguration), factory);

    myIsTestConfiguration = isTestConfiguration;
    myProfilerState = new ProfilerState();
    getOptions().setAllowRunningInParallel(true);

    if (StudioFlags.ATTACH_ON_WAIT_FOR_DEBUGGER.get()) {
      // Start up debugger auto attach.
      AttachOnWaitForDebuggerMonitor.getInstance(project);
    }
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    List<ValidationError> errors = validate(null);
    if (errors.isEmpty()) {
      return;
    }
    // TODO: Do something with the extra error information? Error count?
    ValidationError topError = Ordering.natural().max(errors);
    switch (topError.getSeverity()) {
      case FATAL:
        throw new RuntimeConfigurationError(topError.getMessage(), topError.getQuickfix());
      case WARNING:
        throw new RuntimeConfigurationWarning(topError.getMessage(), topError.getQuickfix());
      case INFO:
      default:
        break;
    }
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a warning.
   * We use a separate method for the collection so the compiler prevents us from accidentally throwing.
   */
  public List<ValidationError> validate(@Nullable Executor executor) {
    List<ValidationError> errors = new ArrayList<>();
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    try {
      configurationModule.checkForWarning();
    }
    catch (RuntimeConfigurationException e) {
      errors.add(ValidationError.fromException(e));
    }
    Module module = configurationModule.getModule();
    if (module == null) {
      // Can't proceed, and fatal error has been caught in ConfigurationModule#checkForWarnings
      return errors;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      // Can't proceed.
      return ImmutableList.of(ValidationError.fatal(AndroidBundle.message("no.facet.error", module.getName())));
    }

    int projectType = facet.getConfiguration().getProjectType();
    switch (projectType) {
      // Supported project types.
      case PROJECT_TYPE_APP:
      case PROJECT_TYPE_INSTANTAPP:
      case PROJECT_TYPE_TEST:
        break;

      // Project types that need further check for the eligibility.
      case PROJECT_TYPE_LIBRARY:
      case PROJECT_TYPE_FEATURE:
      case PROJECT_TYPE_DYNAMIC_FEATURE:
        Pair<Boolean, String> result = supportsRunningLibraryProjects(facet);
        if (!result.getFirst()) {
          errors.add(ValidationError.fatal(result.getSecond()));
        }
        break;

      // Unsupported types.
      default:
        errors.add(ValidationError.fatal(AndroidBundle.message("run.error.apk.not.valid")));
        return errors;
    }

    if (AndroidPlatforms.getInstance(module) == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("select.platform.error")));
    }
    errors.addAll(getDeployTargetContext().getCurrentDeployTargetState().validate(facet));

    // Check that the project system is able to provide a package for this run configuration.
    ApplicationIdProvider applicationIdProvider = getApplicationIdProvider();
    if (applicationIdProvider == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("android.run.configuration.not.supported.applicationid", getName())));
    }
    else {
      try {
        //noinspection unused - we need to "use" getPackageName() to pacify lint's NoOp check.
        String packageName = applicationIdProvider.getPackageName();
      }
      // ApplicationIdProviders will throw ApkProvisionException if they cannot provide a package.
      catch (ApkProvisionException e) {
        errors.add(ValidationError.fatal(AndroidBundle.message("android.run.configuration.not.supported.package", getName())));
      }
    }

    AndroidProjectSystem projectSystem = getProjectSystem(getProject());
    errors.addAll(projectSystem.validateRunConfiguration(this));

    errors.addAll(checkConfiguration(facet));
    AndroidDebuggerState androidDebuggerState = myAndroidDebuggerContext.getAndroidDebuggerState();
    if (androidDebuggerState != null) {
      errors.addAll(androidDebuggerState.validate(facet, executor));
    }

    errors.addAll(myProfilerState.validate());

    return errors;
  }

  /**
   * Returns whether the configuration supports running library projects, and if it doesn't, then an explanation as to why it doesn't.
   */
  protected abstract Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet);

  @NotNull
  protected abstract List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet);

  /**
   * Subclasses should override to adjust the launch options.
   */
  @NotNull
  protected LaunchOptions.Builder getLaunchOptions() {
    return LaunchOptions.builder()
      .setClearLogcatBeforeStart(CLEAR_LOGCAT);
  }

  @Override
  public Collection<Module> getValidModules() {
    List<Module> result = new ArrayList<>();
    // Return list of holder modules with android facets. On the moment of writing (22 Sep 2021) there seem to be no usages
    // of this method. In case it changes in the future please make sure correct modules are returned here.
    for (AndroidFacet facet : ProjectSystemUtil.getAndroidFacets(getProject())) {
      result.add(facet.getModule());
    }
    return result;
  }

  public abstract @NotNull List<DeployTargetProvider> getApplicableDeployTargetProviders();

  protected void validateBeforeRun(@NotNull Executor executor, @NotNull DataContext dataContext) throws ExecutionException {
    List<ValidationError> errors = validate(executor);
    ValidationUtil.promptAndQuickFixErrors(getProject(), dataContext, errors);
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    RunStats stats = RunStatsService.get(getProject()).create();
    try {
      stats.start();
      RunProfileState state = doGetState(executor, env, stats);
      stats.markStateCreated();
      return state;
    }
    catch (Throwable t) {
      stats.abort();
      throw t;
    }
  }

  @Nullable
  public RunProfileState doGetState(@NotNull Executor executor,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull RunStats stats) throws ExecutionException {
    validateBeforeRun(executor, Objects.requireNonNullElse(env.getDataContext(), DataContext.EMPTY_CONTEXT));

    Module module = getConfigurationModule().getModule();
    assert module != null : "Enforced by fatal validation check in checkConfiguration.";
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "Enforced by fatal validation check in checkConfiguration.";

    stats.setDebuggable(LaunchUtils.canDebugApp(facet));
    stats.setExecutor(executor.getId());

    updateExtraRunStats(stats);

    boolean isDebugging = executor instanceof DefaultDebugExecutor;
    DeployTargetContext context = getDeployTargetContext();
    stats.setUserSelectedTarget(context.getCurrentDeployTargetProvider().requiresRuntimePrompt(facet.getModule().getProject()));

    // Figure out deploy target, prompt user if needed (ignore completely if user chose to hotswap).
    DeployTarget deployTarget = getDeployTarget();
    if (deployTarget == null) { // if user doesn't select a deploy target from the dialog
      throw new ExecutionException(AndroidBundle.message("deployment.target.not.found"));
    }

    DeployTargetState deployTargetState = context.getCurrentDeployTargetState();
    if (deployTarget.hasCustomRunProfileState(executor)) {
      return deployTarget.getRunProfileState(executor, env, deployTargetState);
    }

    DeviceFutures deviceFutures = deployTarget.getDevices(getProject());

    // Record stat if we launched a device.
    stats.setLaunchedDevices(deviceFutures.getDevices().stream().anyMatch(device -> device instanceof LaunchableAndroidDevice));

    if (deviceFutures.get().isEmpty()) {
      throw new ExecutionException(AndroidBundle.message("deployment.target.not.found"));
    }

    if (isDebugging) {
      String error = canDebug(deviceFutures, facet, module.getName());
      if (error != null) {
        throw new ExecutionException(error);
      }
    }

    // Store the chosen target on the execution environment so before-run tasks can access it.
    env.putCopyableUserData(DeviceFutures.KEY, deviceFutures);

    // Save the stats so that before-run task can access it
    env.putUserData(RunStats.KEY, stats);

    AndroidConfigurationExecutor configurationExecutor = getExecutor(env, facet, deviceFutures);
    return new AndroidConfigurationExecutorRunProfileState(configurationExecutor);
  }

  protected abstract AndroidConfigurationExecutor getExecutor(ExecutionEnvironment env, AndroidFacet facet, DeviceFutures deployFutures)
    throws ExecutionException;

  private static String canDebug(@NotNull DeviceFutures deviceFutures, @NotNull AndroidFacet facet, @NotNull String moduleName) {
    // If we are debugging on a device, then the app needs to be debuggable
    for (AndroidDevice androidDevice : deviceFutures.getDevices()) {
      if (!androidDevice.isDebuggable() && !LaunchUtils.canDebugApp(facet)) {
        String deviceName;
        if (!androidDevice.getLaunchedDevice().isDone()) {
          deviceName = androidDevice.getName();
        }
        else {
          IDevice device = Futures.getUnchecked(androidDevice.getLaunchedDevice());
          deviceName = device.getName();
        }
        return AndroidBundle.message("android.cannot.debug.noDebugPermissions", moduleName, deviceName);
      }
    }

    return null;
  }

  @Nullable
  protected DeployTarget getDeployTarget() {
    DeployTargetProvider currentTargetProvider = getDeployTargetContext().getCurrentDeployTargetProvider();
    Project project = getProject();

    return currentTargetProvider.requiresRuntimePrompt(project) ?
           currentTargetProvider.showPrompt(project) : currentTargetProvider.getDeployTarget(project);
  }

  @Nullable
  public ApplicationIdProvider getApplicationIdProvider() {
    return getProjectSystem(getProject()).getApplicationIdProvider(this);
  }

  @Nullable
  public final ApkProvider getApkProvider() {
    return getProjectSystem(getProject()).getApkProvider(this);
  }

  public boolean isTestConfiguration() {
    return myIsTestConfiguration;
  }


  @Nullable
  protected abstract AppLaunchTask getApplicationLaunchTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                                            @NotNull AndroidFacet facet,
                                                            @NotNull String contributorsAmStartOptions,
                                                            boolean waitForDebugger,
                                                            @NotNull ApkProvider apkProvider,
                                                            @NotNull IDevice device) throws ExecutionException;

  public void updateExtraRunStats(RunStats runStats) {

  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);

    myDeployTargetContext.readExternal(element);
    myAndroidDebuggerContext.readExternal(element);

    Element profilersElement = element.getChild(PROFILERS_ELEMENT_NAME);
    if (profilersElement != null) {
      myProfilerState.readExternal(profilersElement);
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);

    myDeployTargetContext.writeExternal(element);
    myAndroidDebuggerContext.writeExternal(element);

    Element profilersElement = new Element(PROFILERS_ELEMENT_NAME);
    element.addContent(profilersElement);
    myProfilerState.writeExternal(profilersElement);
  }

  public boolean isNativeLaunch() {
    AndroidDebugger<?> androidDebugger = myAndroidDebuggerContext.getAndroidDebugger();
    if (androidDebugger == null) {
      return false;
    }
    return !androidDebugger.getId().equals(AndroidJavaDebugger.ID);
  }

  @NotNull
  public DeployTargetContext getDeployTargetContext() {
    return myDeployTargetContext;
  }

  @NotNull
  @Override
  public AndroidDebuggerContext getAndroidDebuggerContext() {
    return myAndroidDebuggerContext;
  }

  /**
   * Returns the current {@link ProfilerState} for this configuration.
   */
  @NotNull
  public ProfilerState getProfilerState() {
    return myProfilerState;
  }
}
