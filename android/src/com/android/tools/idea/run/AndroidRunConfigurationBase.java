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

package com.android.tools.idea.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.FastDeployManager;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.run.cloud.*;
import com.android.tools.idea.run.fd.PatchDeployState;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.util.Projects.requiredAndroidModelMissing;

public abstract class AndroidRunConfigurationBase extends ModuleBasedConfiguration<JavaRunConfigurationModule> {
  private static final Logger LOG = Logger.getInstance(AndroidRunConfigurationBase.class);

  private static final String GRADLE_SYNC_FAILED_ERR_MSG = "Gradle project sync failed. Please fix your project and try again.";

  /**
   * A map from launch configuration name to the state of devices at the time of the launch.
   * We want this list of devices persisted across launches, but not across invocations of studio, so we use a static variable.
   */
  private static Map<String, DeviceStateAtLaunch> ourLastUsedDevices = ContainerUtil.newConcurrentMap();

  /** The key used to store the selected deploy target as copyable user data on each execution environment. */
  public static final Key<DeployTarget> DEPLOY_TARGET_KEY = Key.create("android.deploy.target");
  public static final Key<Collection<IDevice>> DEPLOY_DEVICES = Key.create("android.deploy.devices");
  public static final Key<Boolean> FAST_DEPLOY = Key.create("android.fast.deploy");

  public String TARGET_SELECTION_MODE = TargetSelectionMode.EMULATOR.name();
  public boolean USE_LAST_SELECTED_DEVICE = false;
  public String PREFERRED_AVD = "";

  public boolean CLEAR_LOGCAT = false;
  public boolean SHOW_LOGCAT_AUTOMATICALLY = true;
  public boolean SKIP_NOOP_APK_INSTALLATIONS = true; // skip installation if the APK hasn't hasn't changed
  public boolean FORCE_STOP_RUNNING_APP = true; // if no new apk is being installed, then stop the app before launching it again

  public int SELECTED_CLOUD_MATRIX_CONFIGURATION_ID = 0;
  @NotNull
  public String SELECTED_CLOUD_MATRIX_PROJECT_ID = "";
  public int SELECTED_CLOUD_DEVICE_CONFIGURATION_ID = 0;
  @NotNull
  public String SELECTED_CLOUD_DEVICE_PROJECT_ID = "";
  public String CLOUD_DEVICE_SERIAL_NUMBER = "";

  public AndroidRunConfigurationBase(final Project project, final ConfigurationFactory factory) {
    super(new JavaRunConfigurationModule(project, false), factory);
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    List<ValidationError> errors = validate();
    if (errors.isEmpty()) {
      return;
    }
    // TODO: Do something with the extra error information? Error count?
    ValidationError topError = Ordering.natural().max(errors);
    if (topError.isFatal()) {
      throw new RuntimeConfigurationError(topError.getMessage(), topError.getQuickfix());
    }
    throw new RuntimeConfigurationWarning(topError.getMessage(), topError.getQuickfix());
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a warning.
   * We use a separate method for the collection so the compiler prevents us from accidentally throwing.
   */
  private List<ValidationError> validate() {
    List<ValidationError> errors = Lists.newArrayList();
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    try {
      configurationModule.checkForWarning();
    }
    catch (RuntimeConfigurationException e) {
      errors.add(ValidationError.fromException(e));
    }
    final Module module = configurationModule.getModule();
    if (module == null) {
      // Can't proceed, and fatal error has been caught in ConfigurationModule#checkForWarnings
      return errors;
    }

    final Project project = module.getProject();
    if (requiredAndroidModelMissing(project)) {
      errors.add(ValidationError.fatal(GRADLE_SYNC_FAILED_ERR_MSG));
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      // Can't proceed.
      return ImmutableList.of(ValidationError.fatal(AndroidBundle.message("no.facet.error", module.getName())));
    }
    if (facet.isLibraryProject()) {
      Pair<Boolean, String> result = supportsRunningLibraryProjects(facet);
      if (!result.getFirst()) {
        errors.add(ValidationError.fatal(result.getSecond()));
      }
    }
    if (facet.getConfiguration().getAndroidPlatform() == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("select.platform.error")));
    }
    if (facet.getManifest() == null) {
      errors.add(ValidationError.fatal(AndroidBundle.message("android.manifest.not.found.error")));
    }
    errors.addAll(getTargetChooser(facet).validate());
    errors.addAll(getApkProvider(facet).validate());

    errors.addAll(checkConfiguration(facet));

    return errors;
  }

  /** Returns whether the configuration supports running library projects, and if it doesn't, then an explanation as to why it doesn't. */
  protected abstract Pair<Boolean,String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet);

  @NotNull
  protected abstract List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet);

  /** Subclasses should override to adjust the launch options passed to AndroidRunningState. */
  @NotNull
  protected LaunchOptions.Builder getLaunchOptions() {
    return LaunchOptions.builder()
      .setClearLogcatBeforeStart(CLEAR_LOGCAT)
      .setSkipNoopApkInstallations(SKIP_NOOP_APK_INSTALLATIONS)
      .setForceStopRunningApp(FORCE_STOP_RUNNING_APP);
  }

  @Override
  public Collection<Module> getValidModules() {
    final List<Module> result = new ArrayList<Module>();
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    for (Module module : modules) {
      if (AndroidFacet.getInstance(module) != null) {
        result.add(module);
      }
    }
    return result;
  }

  @NotNull
  public TargetSelectionMode getTargetSelectionMode() {
    try {
      return TargetSelectionMode.valueOf(TARGET_SELECTION_MODE);
    }
    catch (IllegalArgumentException e) {
      LOG.info(e);
      return TargetSelectionMode.EMULATOR;
    }
  }

  public void setTargetSelectionMode(@NotNull TargetSelectionMode mode) {
    TARGET_SELECTION_MODE = mode.name();
  }

  public void setDevicesUsedInLaunch(@NotNull Set<IDevice> usedDevices, @NotNull Set<IDevice> availableDevices) {
    ourLastUsedDevices.put(getName(), new DeviceStateAtLaunch(usedDevices, availableDevices));
  }

  @Nullable
  public DeviceStateAtLaunch getDevicesUsedInLastLaunch() {
    return ourLastUsedDevices.get(getName());
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final Module module = getConfigurationModule().getModule();
    assert module != null : "Enforced by fatal validation check in checkConfiguration.";
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "Enforced by fatal validation check in checkConfiguration.";

    Project project = env.getProject();

    boolean debug = false;
    if (executor instanceof DefaultDebugExecutor) {
      if (!AndroidSdkUtils.activateDdmsIfNecessary(facet.getModule().getProject())) {
        return null;
      }
      debug = true;
    }

    if (AndroidSdkUtils.getDebugBridge(getProject()) == null) {
      return null;
    }

    if (FastDeployManager.isPatchableApp(module)) {
      // Normally, all files are saved when Gradle runs (in GradleInvoker#executeTasks). However,
      // we need to save the files a bit earlier than that here (turning the Gradle file save into
      // a no-op) because we need to check whether the manifest file has been edited since an
      // edited manifest changes what the incremental run build has to do.
      GradleInvoker.saveAllFilesSafely();

      // For incremental run, we don't want to show any dialogs and just redeploy directly to the last used devices
      Collection<IDevice> fastDeployDevices = getFastDeployDevices(module);
      if (!(executor instanceof DefaultDebugExecutor) && !fastDeployDevices.isEmpty()) {
        if (FastDeployManager.isRebuildRequired(fastDeployDevices, module)) {
          LOG.info("Cannot patch update since a full rebuild is required (typically because the manifest has changed)");
        } else {
          if (FastDeployManager.DISPLAY_STATISTICS) {
            FastDeployManager.notifyBegin();
          }

          env.putCopyableUserData(FAST_DEPLOY, Boolean.TRUE);
          env.putCopyableUserData(DEPLOY_DEVICES, fastDeployDevices);
          return new PatchDeployState(facet, fastDeployDevices);
        }
      }
    }

    ProcessHandlerConsolePrinter printer = new ProcessHandlerConsolePrinter(null);
    TargetChooser targetChooser = getTargetChooser(facet);

    // If there is a session that we will embed to, we need to re-use the devices from that session.
    DeployTarget deployTarget = getOldSessionTarget(project, executor, targetChooser);
    if (deployTarget == null) {
      deployTarget = targetChooser.getTarget(printer, getDeviceCount(debug), debug);
      if (deployTarget == null) {
        // The user deliberately canceled, or some error was encountered and exposed by the chooser. Quietly exit.
        return null;
      }
    }

    // Store the chosen target on the execution environment so before-run tasks can access it.
    env.putCopyableUserData(DEPLOY_TARGET_KEY, deployTarget);

    if (deployTarget instanceof CloudMatrixTarget) {
      return new CloudMatrixTestRunningState(env, facet, this, (CloudMatrixTarget)deployTarget);
    }
    else if (deployTarget instanceof CloudDeviceLaunchTarget) {
      return new CloudDeviceLaunchRunningState(facet, (CloudDeviceLaunchTarget)deployTarget);
    }

    assert deployTarget instanceof DeviceTarget : "Unknown target type: " + deployTarget.getClass().getCanonicalName();
    DeviceTarget deviceTarget = (DeviceTarget)deployTarget;
    if (deviceTarget.getDeviceFutures().isEmpty()) {
      throw new ExecutionException(AndroidBundle.message("deployment.target.not.found"));
    }

    LaunchOptions launchOptions = getLaunchOptions()
      .setDebug(debug)
      .build();

    return new AndroidRunningState(env, facet, getApkProvider(facet), deviceTarget, printer, getApplicationLauncher(facet),
                                   launchOptions, this);
  }

  private Collection<IDevice> getFastDeployDevices(@NotNull Module module) {
    // TODO: this may not be set properly the first time an emulator is launched
    // TODO: eventually, this should look at the currently active configurations, and determine the devices from there
    DeviceStateAtLaunch deviceStateAtLaunch = getDevicesUsedInLastLaunch();
    if (deviceStateAtLaunch == null) {
      LOG.info("Cannot patch update since we don't know the devices used in last launch");
      return Collections.emptyList();
    }

    // Note: we assume adb has been properly setup since this can only happen after a launch has taken place
    List<IDevice> devices = Lists.newArrayList(AndroidDebugBridge.getBridge().getDevices());
    if (!deviceStateAtLaunch.matchesCurrentAvailableDevices(devices)) {
      LOG.info("Cannot patch update since the list of devices has changed since the last launch");
      return Collections.emptyList();
    }

    Collection<IDevice> usedDevices = deviceStateAtLaunch.filterByUsed(devices);
    if (usedDevices.isEmpty()) {
      LOG.info("Cannot patch update since the none of the devices from previous launch are online");
      return Collections.emptyList();
    }

    for (IDevice device : usedDevices) {
      // TODO: we may eventually support a push to device even if the app isn't running
      if (!FastDeployManager.isAppRunning(device, module)) {
        LOG.info("Cannot patch update since the app is not running on device: " + device.getName());
        return Collections.emptyList();
      }
    }

    return usedDevices;
  }

  @Nullable
  private DeviceTarget getOldSessionTarget(@NotNull Project project,
                                           @NotNull Executor executor,
                                           @NotNull TargetChooser targetChooser) {
    AndroidSessionInfo sessionInfo = AndroidSessionManager.findOldSession(project, executor, this);
    if (sessionInfo != null) {
      if (sessionInfo.isEmbeddable()) {
        Collection<IDevice> oldDevices = sessionInfo.getState().getDevices();
        Collection<IDevice> currentDevices = DeviceSelectionUtils.getAllCompatibleDevices(new TargetDeviceFilter(targetChooser));
        if (currentDevices.equals(oldDevices)) {
          return DeviceTarget.forDevices(oldDevices);
        }
      }
    }
    return null;
  }

  @NotNull
  protected TargetChooser getTargetChooser(@NotNull AndroidFacet facet) {
    switch (getTargetSelectionMode()) {
      case SHOW_DIALOG:
        return new ManualTargetChooser(this, facet);
      case EMULATOR:
        return new EmulatorTargetChooser(facet, Strings.emptyToNull(PREFERRED_AVD));
      case USB_DEVICE:
        return new UsbDeviceTargetChooser(facet);
      case CLOUD_DEVICE_DEBUGGING:
        return new CloudDebuggingTargetChooser(CLOUD_DEVICE_SERIAL_NUMBER);
      case CLOUD_MATRIX_TEST:
        ManualTargetChooser fallback = new ManualTargetChooser(this, facet);
        return new CloudMatrixTargetChooser(facet, SELECTED_CLOUD_MATRIX_CONFIGURATION_ID, SELECTED_CLOUD_MATRIX_PROJECT_ID, fallback);
      case CLOUD_DEVICE_LAUNCH:
        return new CloudDeviceTargetChooser(facet, SELECTED_CLOUD_DEVICE_CONFIGURATION_ID, SELECTED_CLOUD_DEVICE_PROJECT_ID);
      default:
        throw new IllegalStateException("Unknown target selection mode " + TARGET_SELECTION_MODE);
    }
  }

  @NotNull
  protected abstract ApkProvider getApkProvider(@NotNull AndroidFacet facet);

  @NotNull
  protected abstract ConsoleView attachConsole(AndroidRunningState state, Executor executor) throws ExecutionException;

  @NotNull
  protected abstract AndroidApplicationLauncher getApplicationLauncher(AndroidFacet facet);

  @NotNull
  public final DeviceCount getDeviceCount(boolean debug) {
    return DeviceCount.fromBoolean(supportMultipleDevices() && !debug);
  }

  /** @return true iff this run configuration supports deploying to multiple devices. */
  protected abstract boolean supportMultipleDevices();

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    readModule(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean usesSimpleLauncher() {
    return true;
  }
}
