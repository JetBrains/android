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

import com.android.builder.model.AndroidArtifactOutput;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.cloud.*;
import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
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

  public String TARGET_SELECTION_MODE = TargetSelectionMode.EMULATOR.name();
  public boolean USE_LAST_SELECTED_DEVICE = false;
  public String PREFERRED_AVD = "";
  public boolean USE_COMMAND_LINE = true;
  public String COMMAND_LINE = "";
  public boolean WIPE_USER_DATA = false;
  public boolean DISABLE_BOOT_ANIMATION = false;
  public String NETWORK_SPEED = "full";
  public String NETWORK_LATENCY = "none";
  public boolean CLEAR_LOGCAT = false;
  public boolean SHOW_LOGCAT_AUTOMATICALLY = true;
  public boolean SKIP_NOOP_APK_INSTALLATIONS = true; // skip installation if the APK hasn't hasn't changed
  public boolean FORCE_STOP_RUNNING_APP = true; // if no new apk is being installed, then stop the app before launching it again

  public int SELECTED_CLOUD_MATRIX_CONFIGURATION_ID = 0;
  public String SELECTED_CLOUD_MATRIX_PROJECT_ID = "";
  public int SELECTED_CLOUD_DEVICE_CONFIGURATION_ID = 0;
  public String SELECTED_CLOUD_DEVICE_PROJECT_ID = "";
  public boolean IS_VALID_CLOUD_MATRIX_SELECTION = false; // indicates whether the selected matrix config + project combo is valid
  public String INVALID_CLOUD_MATRIX_SELECTION_ERROR = ""; // specifies the error if the matrix config + project combo is invalid
  public boolean IS_VALID_CLOUD_DEVICE_SELECTION = false; // indicates whether the selected cloud device config + project combo is valid
  public String INVALID_CLOUD_DEVICE_SELECTION_ERROR = ""; // specifies the error if the cloud device config + project combo is invalid
  public String CLOUD_DEVICE_SERIAL_NUMBER = "";

  public AndroidRunConfigurationBase(final Project project, final ConfigurationFactory factory) {
    super(new JavaRunConfigurationModule(project, false), factory);
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    configurationModule.checkForWarning();
    final Module module = configurationModule.getModule();

    if (module == null) {
      return;
    }

    final Project project = module.getProject();
    if (requiredAndroidModelMissing(project)) {
      // This only shows an error message on the "Run Configuration" dialog, but does not prevent user from running app.
      throw new RuntimeConfigurationException(GRADLE_SYNC_FAILED_ERR_MSG);
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      throw new RuntimeConfigurationError(AndroidBundle.message("android.no.facet.error"));
    }
    if (facet.isLibraryProject()) {
      Pair<Boolean, String> result = supportsRunningLibraryProjects(facet);
      if (!result.getFirst()) {
        throw new RuntimeConfigurationError(result.getSecond());
      }
    }
    if (facet.getConfiguration().getAndroidPlatform() == null) {
      throw new RuntimeConfigurationError(AndroidBundle.message("select.platform.error"));
    }
    if (facet.getManifest() == null) {
      throw new RuntimeConfigurationError(AndroidBundle.message("android.manifest.not.found.error"));
    }
    if (PREFERRED_AVD.length() > 0) {
      AvdManager avdManager = facet.getAvdManagerSilently();
      if (avdManager == null) {
        throw new RuntimeConfigurationError(AndroidBundle.message("avd.cannot.be.loaded.error"));
      }
      AvdInfo avdInfo = avdManager.getAvd(PREFERRED_AVD, false);
      if (avdInfo == null) {
        throw new RuntimeConfigurationError(AndroidBundle.message("avd.not.found.error", PREFERRED_AVD));
      }
      if (avdInfo.getStatus() != AvdInfo.AvdStatus.OK) {
        String message = avdInfo.getErrorMessage();
        message = AndroidBundle.message("avd.not.valid.error", PREFERRED_AVD) +
                  (message != null ? ": " + message: "") + ". Try to repair it through AVD manager";
        throw new RuntimeConfigurationError(message);
      }
    }

    validateApkSigning(facet);

    checkConfiguration(facet);
  }

  private static void validateApkSigning(@NotNull final AndroidFacet facet) throws RuntimeConfigurationError {
    AndroidGradleModel androidGradleModel = AndroidGradleModel.get(facet);
    if (androidGradleModel == null) {
      return;
    }

    if (androidGradleModel.getMainArtifact().isSigned()) {
      return;
    }

    AndroidArtifactOutput output = GradleUtil.getOutput(androidGradleModel.getMainArtifact());
    final String message = AndroidBundle.message("run.error.apk.not.signed", output.getMainOutputFile().getOutputFile().getName(),
                                                 androidGradleModel.getSelectedVariant().getDisplayName());

    Runnable quickFix = new Runnable() {
      @Override
      public void run() {
        Module module = facet.getModule();
        ProjectSettingsService service = ProjectSettingsService.getInstance(module.getProject());
        if (service instanceof AndroidProjectSettingsService) {
          ((AndroidProjectSettingsService)service).openSigningConfiguration(module);
        }
        else {
          service.openModuleSettings(module);
        }
      }
    };
    throw new RuntimeConfigurationError(message, quickFix);
  }

  /** Returns whether the configuration supports running library projects, and if it doesn't, then an explanation as to why it doesn't. */
  protected abstract Pair<Boolean,String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet);

  protected abstract void checkConfiguration(@NotNull AndroidFacet facet) throws RuntimeConfigurationException;

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
    if (module == null) {
      throw new ExecutionException("Module is not found");
    }
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      throw new ExecutionException(AndroidBundle.message("no.facet.error", module.getName()));
    }

    Project project = env.getProject();

    if (requiredAndroidModelMissing(project)) {
      // This prevents user from running the app.
      throw new ExecutionException(GRADLE_SYNC_FAILED_ERR_MSG);
    }

    AndroidFacetConfiguration configuration = facet.getConfiguration();
    AndroidPlatform platform = configuration.getAndroidPlatform();
    if (platform == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("specify.platform.error"), CommonBundle.getErrorTitle());
      ModulesConfigurator.showDialog(project, module.getName(), ClasspathEditor.NAME);
      return null;
    }

    boolean debug = DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId());
    boolean nonDebuggableOnDevice = false;

    if (debug) {
      Boolean isDebuggable = AndroidModuleInfo.get(facet).isDebuggable();
      nonDebuggableOnDevice = isDebuggable != null && !isDebuggable;

      if (!AndroidSdkUtils.activateDdmsIfNecessary(facet.getModule().getProject())) {
        return null;
      }
    }

    if (AndroidSdkUtils.getDebugBridge(getProject()) == null) return null;

    ProcessHandlerConsolePrinter printer = new ProcessHandlerConsolePrinter(null);
    TargetChooser targetChooser = getTargetChooser(facet, executor, printer);

    // If there is a session that we will embed to, we need to re-use the devices from that session.
    DeviceTarget deviceTarget = getOldSessionTarget(project, executor, targetChooser);
    if (deviceTarget == null) {
      DeployTarget chosenTarget = targetChooser.getTarget();
      if (chosenTarget == null) {
        // The user deliberately canceled, or some error was encountered and exposed by the chooser. Quietly exit.
        return null;
      }

      // Store the chosen target on the execution environment so before-run tasks can access it.
      env.putCopyableUserData(DEPLOY_TARGET_KEY, chosenTarget);

      if (chosenTarget instanceof CloudMatrixTarget) {
        return new CloudMatrixTestRunningState(env, facet, this, (CloudMatrixTarget) chosenTarget);
      } else if (chosenTarget instanceof CloudDeviceLaunchTarget) {
        return new CloudDeviceLaunchRunningState(facet, (CloudDeviceLaunchTarget) chosenTarget);
      } else if (chosenTarget instanceof DeviceTarget) {
        deviceTarget = (DeviceTarget) chosenTarget;
      } else {
        assert false : "Unknown target type: " + chosenTarget.getClass().getCanonicalName();
      }
    }

    if (deviceTarget.getDeviceFutures().isEmpty()) {
      throw new ExecutionException(AndroidBundle.message("deployment.target.not.found"));
    }

    return new AndroidRunningState(env, facet, getApkProvider(), deviceTarget, printer, getApplicationLauncher(facet), CLEAR_LOGCAT, this,
                                   nonDebuggableOnDevice);
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
  protected TargetChooser getTargetChooser(@NotNull AndroidFacet facet, @NotNull Executor executor, @NotNull ConsolePrinter printer) {
    final boolean supportMultipleDevices = supportMultipleDevices() && executor.getId().equals(DefaultRunExecutor.EXECUTOR_ID);
    switch (getTargetSelectionMode()) {
      case SHOW_DIALOG:
        return new ManualTargetChooser(this, facet, supportMultipleDevices, computeCommandLine(), executor, printer);
      case EMULATOR:
        return new EmulatorTargetChooser(facet, supportMultipleDevices, computeCommandLine(), printer,
                                         PREFERRED_AVD.length() > 0 ? PREFERRED_AVD : null);
      case USB_DEVICE:
        return new UsbDeviceTargetChooser(facet, supportMultipleDevices);
      case CLOUD_DEVICE_DEBUGGING:
        return new CloudDebuggingTargetChooser(CLOUD_DEVICE_SERIAL_NUMBER);
      case CLOUD_MATRIX_TEST:
        if (executor instanceof DefaultDebugExecutor) {
          // It does not make sense to debug a matrix of devices on the cloud.
          // TODO: Consider making the debug executor unavailable in this case rather than popping the extended chooser dialog.
          return new ManualTargetChooser(this, facet, supportMultipleDevices, computeCommandLine(), executor, printer);
        }
        return new CloudMatrixTargetChooser(SELECTED_CLOUD_MATRIX_CONFIGURATION_ID, SELECTED_CLOUD_MATRIX_PROJECT_ID);
      case CLOUD_DEVICE_LAUNCH:
        return new CloudDeviceTargetChooser(SELECTED_CLOUD_DEVICE_CONFIGURATION_ID, SELECTED_CLOUD_DEVICE_PROJECT_ID);
      default:
        throw new IllegalStateException("Unknown target selection mode " + TARGET_SELECTION_MODE);
    }
  }

  @NotNull
  protected abstract ApkProvider getApkProvider();

  @Nullable
  public static Pair<File, String> getCopyOfCompilerManifestFile(@NotNull AndroidFacet facet) throws IOException {
    final VirtualFile manifestFile = AndroidRootUtil.getCustomManifestFileForCompiler(facet);

    if (manifestFile == null) {
      return null;
    }
    File tmpDir = null;
    try {
      tmpDir = FileUtil.createTempDirectory("android_manifest_file_for_execution", "tmp");
      final File manifestCopy = new File(tmpDir, manifestFile.getName());
      FileUtil.copy(new File(manifestFile.getPath()), manifestCopy);
      //noinspection ConstantConditions
      return Pair.create(manifestCopy, PathUtil.getLocalPath(manifestFile));
    }
    catch (IOException e) {
      LOG.info(e);
      if (tmpDir != null) {
        FileUtil.delete(tmpDir);
      }
      throw e;
    }
  }

  private String computeCommandLine() {
    StringBuilder result = new StringBuilder();
    result.append("-netspeed ").append(NETWORK_SPEED).append(' ');
    result.append("-netdelay ").append(NETWORK_LATENCY).append(' ');
    if (WIPE_USER_DATA) {
      result.append("-wipe-data ");
    }
    if (DISABLE_BOOT_ANIMATION) {
      result.append("-no-boot-anim ");
    }
    if (USE_COMMAND_LINE) {
      result.append(COMMAND_LINE);
    }
    int last = result.length() - 1;
    if (result.charAt(last) == ' ') {
      result.deleteCharAt(last);
    }
    return result.toString();
  }

  @NotNull
  protected abstract ConsoleView attachConsole(AndroidRunningState state, Executor executor) throws ExecutionException;

  @NotNull
  protected abstract AndroidApplicationLauncher getApplicationLauncher(AndroidFacet facet);

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
