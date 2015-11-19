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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.FastDeployManager;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.run.editor.*;
import com.google.common.collect.*;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerIconProvider;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import icons.AndroidIcons;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.android.tools.idea.gradle.util.Projects.requiredAndroidModelMissing;

public abstract class AndroidRunConfigurationBase extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements
                                                                                                               RunnerIconProvider {
  private static final Logger LOG = Logger.getInstance(AndroidRunConfigurationBase.class);

  private static final String GRADLE_SYNC_FAILED_ERR_MSG = "Gradle project sync failed. Please fix your project and try again.";

  /** The key used to store the selected device target as copyable user data on each execution environment. */
  public static final Key<DeviceFutures> DEVICE_FUTURES_KEY = Key.create("android.device.futures");

  public static final Key<Boolean> FAST_DEPLOY = Key.create("android.fast.deploy");
  public static final Key<Boolean> DEXSWAP = Key.create("android.fast.deploy.dexswap");

  private static final DialogWrapper.DoNotAskOption ourKillLaunchOption = new MyDoNotPromptOption();

  public String TARGET_SELECTION_MODE = TargetSelectionMode.SHOW_DIALOG.name();
  public String PREFERRED_AVD = "";

  public boolean CLEAR_LOGCAT = false;
  public boolean SHOW_LOGCAT_AUTOMATICALLY = true;
  public boolean SKIP_NOOP_APK_INSTALLATIONS = true; // skip installation if the APK hasn't hasn't changed
  public boolean FORCE_STOP_RUNNING_APP = true; // if no new apk is being installed, then stop the app before launching it again

  private final List<DeployTargetProvider> myDeployTargetProviders; // all available deploy targets
  private final Map<String, DeployTargetState> myDeployTargetStates;

  private final boolean myAndroidTests;

  public String DEBUGGER_TYPE;
  private final Map<String, AndroidDebuggerState> myAndroidDebuggerStates = Maps.newHashMap();

  public AndroidRunConfigurationBase(final Project project, final ConfigurationFactory factory, boolean androidTests) {
    super(new JavaRunConfigurationModule(project, false), factory);

    myDeployTargetProviders = DeployTargetProvider.getProviders();
    myAndroidTests = androidTests;

    ImmutableMap.Builder<String, DeployTargetState> builder = ImmutableMap.builder();
    for (DeployTargetProvider provider : myDeployTargetProviders) {
      builder.put(provider.getId(), provider.createState());
    }
    myDeployTargetStates = builder.build();
    DEBUGGER_TYPE = getDefaultAndroidDebuggerType();
    for (AndroidDebugger androidDebugger: getAndroidDebuggers()) {
      myAndroidDebuggerStates.put(androidDebugger.getId(), androidDebugger.createState());
    }
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
    errors.addAll(getCurrentDeployTargetState().validate(facet));

    errors.addAll(getApkProvider(facet).validate());

    errors.addAll(checkConfiguration(facet));
    AndroidDebuggerState androidDebuggerState = getAndroidDebuggerState(DEBUGGER_TYPE);
    if (androidDebuggerState != null) {
      errors.addAll(androidDebuggerState.validate(facet));
    }

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

  @NotNull
  public List<DeployTargetProvider> getApplicableDeployTargetProviders() {
    List<DeployTargetProvider> targets = Lists.newArrayList();

    for (DeployTargetProvider target : myDeployTargetProviders) {
      if (target.isApplicable(myAndroidTests)) {
        targets.add(target);
      }
    }

    return targets;
  }

  @NotNull
  public DeployTargetProvider getCurrentDeployTargetProvider() {
    DeployTargetProvider target = getDeployTargetProvider(TARGET_SELECTION_MODE);
    if (target == null) {
      target = getDeployTargetProvider(TargetSelectionMode.SHOW_DIALOG.name());
    }

    assert target != null;
    return target;
  }

  @Nullable
  private DeployTargetProvider getDeployTargetProvider(@NotNull String id) {
    for (DeployTargetProvider target : myDeployTargetProviders) {
      if (target.getId().equals(id)) {
        return target;
      }
    }

    return null;
  }

  @NotNull
  private DeployTargetState getCurrentDeployTargetState() {
    DeployTargetProvider currentTarget = getCurrentDeployTargetProvider();
    return myDeployTargetStates.get(currentTarget.getId());
  }

  @NotNull
  public DeployTargetState getDeployTargetState(@NotNull DeployTargetProvider target) {
    return myDeployTargetStates.get(target.getId());
  }

  public void setTargetSelectionMode(@NotNull TargetSelectionMode mode) {
    TARGET_SELECTION_MODE = mode.name();
  }

  public void setTargetSelectionMode(@NotNull DeployTargetProvider target) {
    TARGET_SELECTION_MODE = target.getId();
  }

  @Nullable
  @Override
  public Icon getExecutorIcon(@NotNull RunConfiguration configuration, @NotNull Executor executor) {
    Module module = getConfigurationModule().getModule();
    if (module == null) {
      return null;
    }

    if (!FastDeployManager.isPatchableApp(module)) {
      return null;
    }

    AndroidSessionInfo info = AndroidSessionManager.findOldSession(getProject(), getName());
    if (info == null) {
      return null;
    }

    if (info.getExecutorId().equals(executor.getId())) {
      return executor instanceof DefaultRunExecutor ? AndroidIcons.RunIcons.Replay : AndroidIcons.RunIcons.DebugReattach;
    }

    return null;
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
        throw new ExecutionException("Unable to obtain debug bridge. Please check if there is a different tool using adb that is active.");
      }
      debug = true;
    }

    DeployTargetProvider currentTargetProvider = getCurrentDeployTargetProvider();
    DeployTargetState deployTargetState = getCurrentDeployTargetState();
    ProcessHandlerConsolePrinter printer = new ProcessHandlerConsolePrinter(null);

    AndroidSessionInfo info = AndroidSessionManager.findOldSession(project, getName());
    if (info != null) {
      if (!info.getExecutorId().equals(executor.getId())) {
        // only run or debug of a config can be active at a time
        String msg = "You currently have an active " + info.getExecutorId() + " session of the same launch configuration.\n" +
                     "Do you want to kill that session and proceed with the current launch?";
        if (ourKillLaunchOption.isToBeShown() && Messages.NO ==
            Messages.showYesNoDialog(project, msg, "Launching " + getName(), AllIcons.General.QuestionDialog, ourKillLaunchOption)) {
          return null;
        }

        info.getProcessHandler().detachProcess();
        LOG.info("Disconnecting existing session with a different executor: " + info.getExecutorId());
      } else {
        if (FastDeployManager.isPatchableApp(module)) {
          // Normally, all files are saved when Gradle runs (in GradleInvoker#executeTasks). However,
          // we need to save the files a bit earlier than that here (turning the Gradle file save into
          // a no-op) because we need to check whether the manifest file has been edited since an
          // edited manifest changes what the incremental run build has to do.
          GradleInvoker.saveAllFilesSafely();

          // For incremental run, we don't want to show any dialogs and just redeploy directly to the last used devices
          Collection<IDevice> devices = info.getState().getDevices();
          if (devices != null && canFastDeploy(module, devices)) {
            if (FastDeployManager.isRebuildRequired(devices, module)) {
              LOG.info("Cannot patch update since a full rebuild is required (typically because the manifest has changed)");
            } else {
              if (FastDeployManager.DISPLAY_STATISTICS) {
                FastDeployManager.notifyBegin();
              }

              env.putCopyableUserData(FAST_DEPLOY, Boolean.TRUE);
              env.putCopyableUserData(DEVICE_FUTURES_KEY, DeviceFutures.forDevices(devices));

              return new PatchDeployState(info.getDescriptor(), facet, devices);
            }
          }
          LOG.info("Skipping fast deploy, devices from last run: " + (devices == null ? "none" : devices.size()));
        }
      }
    }

    // When performing a full launch, always terminate the previous sessions. Otherwise, we might end up with 2 active sessions of the same
    // launch, esp. if we first think we can do a fast deploy, then one of the conditionals above fails and we end up doing a full launch.
    // TODO: this probably means that AndroidDebugRunner will never need to embed to an existing session?
    if (info != null) {
      info.getProcessHandler().detachProcess();
    }

    DeployTarget deployTarget;
    if (currentTargetProvider.requiresRuntimePrompt()) {
      deployTarget = currentTargetProvider
        .showPrompt(executor, env, facet, getDeviceCount(debug), myAndroidTests, myDeployTargetStates, getUniqueID(), printer);
      if (deployTarget == null) {
        return null;
      }
    }
    else {
      deployTarget = currentTargetProvider.getDeployTarget();
    }

    if (deployTarget.hasCustomRunProfileState(executor)) {
      return deployTarget.getRunProfileState(executor, env, deployTargetState);
    }

    // If there is a session that we will embed to, we need to re-use the devices from that session.
    // TODO: this means that if the deployment target is changed between sessions, we still use the one from the old session?
    DeviceFutures deviceFutures = getOldSessionTarget(project, executor);
    if (deviceFutures == null) {
      deviceFutures = deployTarget.getDevices(deployTargetState, facet, getDeviceCount(debug), debug, getUniqueID(), printer);
      if (deviceFutures == null) {
        // The user deliberately canceled, or some error was encountered and exposed by the chooser. Quietly exit.
        return null;
      }
    }

    // Store the chosen target on the execution environment so before-run tasks can access it.
    env.putCopyableUserData(DEVICE_FUTURES_KEY, deviceFutures);

    if (deviceFutures.get().isEmpty()) {
      throw new ExecutionException(AndroidBundle.message("deployment.target.not.found"));
    }

    boolean dexSwap = false;
    Collection<IDevice> devices = deviceFutures.getIfReady();
    if (devices != null && !devices.isEmpty()) {
      // At this point, we've determined that we can't do a hot or cold swap.
      // But we do have a bunch of devices, and rather than installing the full apk, we could potentially do a dex swap
      if (FastDeployManager.isPatchableApp(module)) {
        if (FastDeployManager.canDexSwap(module, devices)) {
          env.putCopyableUserData(DEXSWAP, Boolean.TRUE);
          dexSwap = true;
        }
      }
    }

    LaunchOptions launchOptions = getLaunchOptions()
      .setDebug(debug)
      .setDexSwap(dexSwap)
      .build();

    AndroidApplicationLauncher appLauncher = getApplicationLauncher(facet);
    AndroidDebuggerState androidDebuggerState = getAndroidDebuggerState(DEBUGGER_TYPE);
    if (androidDebuggerState != null) {
      appLauncher = androidDebuggerState.getApplicationLauncher(appLauncher);
    }

    return new AndroidRunningState(
      env,
      facet,
      getApkProvider(facet),
      deviceFutures,
      printer,
      appLauncher,
      launchOptions,
      getUniqueID(),
      getType().getId(),
      getConsoleProvider(),
      this
    );
  }

  private static boolean canFastDeploy(@NotNull Module module, @NotNull Collection<IDevice> usedDevices) {
    for (IDevice device : usedDevices) {
      // TODO: we may eventually support a push to device even if the app isn't running
      if (!FastDeployManager.isAppRunning(device, module)) {
        LOG.info("Cannot patch update since the app is not running on device: " + device.getName());
        return false;
      }
      if (!FastDeployManager.buildIdsMatch(device, module)) {
        LOG.info("Local Gradle build id doesn't match what's installed on the device; full build required");
        return false;
      }
    }

    return true;
  }

  @Nullable
  private DeviceFutures getOldSessionTarget(@NotNull Project project, @NotNull Executor executor) {
    AndroidSessionInfo sessionInfo = AndroidSessionManager.findOldSession(project, executor, getUniqueID());
    if (sessionInfo != null) {
      if (sessionInfo.isEmbeddable()) {
        Collection<IDevice> oldDevices = sessionInfo.getState().getDevices();
        Collection<IDevice> online = DeviceSelectionUtils.getOnlineDevices(oldDevices);
        if (!online.isEmpty()) {
          return DeviceFutures.forDevices(online);
        }
      }
    }
    return null;
  }

  @NotNull
  protected abstract ApkProvider getApkProvider(@NotNull AndroidFacet facet);

  @NotNull
  protected abstract ConsoleProvider getConsoleProvider();

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

    for (DeployTargetState state : myDeployTargetStates.values()) {
      DefaultJDOMExternalizer.readExternal(state, element);
    }

    for (Map.Entry<String, AndroidDebuggerState> entry: myAndroidDebuggerStates.entrySet()) {
      Element optionElement = element.getChild(entry.getKey());
      if (optionElement != null) {
        entry.getValue().readExternal(optionElement);
      }
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    DefaultJDOMExternalizer.writeExternal(this, element);

    for (DeployTargetState state : myDeployTargetStates.values()) {
      DefaultJDOMExternalizer.writeExternal(state, element);
    }

    for (Map.Entry<String, AndroidDebuggerState> entry: myAndroidDebuggerStates.entrySet()) {
      Element optionElement = new Element(entry.getKey());
      element.addContent(optionElement);
      entry.getValue().writeExternal(optionElement);
    }
  }

  public boolean usesSimpleLauncher() {
    AndroidDebugger<?> androidDebugger = getAndroidDebugger();
    if (androidDebugger == null) {
      return true;
    }
    return androidDebugger.getId().equals(AndroidJavaDebugger.ID);
  }

  @NotNull
  protected String getDefaultAndroidDebuggerType() {
    return AndroidJavaDebugger.ID;
  }

  @NotNull
  public List<AndroidDebugger> getAndroidDebuggers() {
    return Arrays.asList(AndroidDebugger.EP_NAME.getExtensions());
  }

  @Nullable
  public AndroidDebugger getAndroidDebugger() {
    for (AndroidDebugger androidDebugger: getAndroidDebuggers()) {
      if (androidDebugger.getId().equals(DEBUGGER_TYPE)) {
        return androidDebugger;
      }
    }
    return null;
  }

  @Nullable
  public <T extends AndroidDebuggerState> T getAndroidDebuggerState(@NotNull String androidDebuggerId) {
    AndroidDebuggerState state = myAndroidDebuggerStates.get(androidDebuggerId);
    return (state != null) ? (T)state : null;
  }

  @Nullable
  public <T extends AndroidDebuggerState> T getAndroidDebuggerState() {
    return getAndroidDebuggerState(DEBUGGER_TYPE);
  }

  private static class MyDoNotPromptOption implements DialogWrapper.DoNotAskOption {
    private boolean myShow;

    @Override
    public boolean isToBeShown() {
      return !myShow;
    }

    @Override
    public void setToBeShown(boolean toBeShown, int exitCode) {
      myShow = !toBeShown;
    }

    @Override
    public boolean canBeHidden() {
      return true;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return true;
    }

    @NotNull
    @Override
    public String getDoNotShowMessage() {
      return "Do not ask again";
    }
  }
}
