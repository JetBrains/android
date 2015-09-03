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
package org.jetbrains.android.run;

import com.android.ddmlib.*;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.service.notification.hyperlink.SyncProjectHyperlink;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.monitor.AndroidToolWindowFactory;
import com.android.tools.idea.run.*;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.intellij.CommonBundle;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AvdsNotSupportedException;
import org.jetbrains.android.logcat.AndroidLogcatView;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.sdk.AvdManagerLog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.run.CloudConfiguration.Kind.MATRIX;
import static com.android.tools.idea.run.CloudConfiguration.Kind.SINGLE_DEVICE;
import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author coyote
 */
public class AndroidRunningState implements RunProfileState, AndroidDebugBridge.IClientChangeListener, AndroidExecutionState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidRunningState");

  @NonNls private static final String ANDROID_TARGET_DEVICES_PROPERTY = "AndroidTargetDevices";
  private static final IDevice[] EMPTY_DEVICE_ARRAY = new IDevice[0];

  public static final int WAITING_TIME_SECS = 20;

  private static final Pattern FAILURE = Pattern.compile("Failure\\s+\\[(.*)\\]");
  private static final Pattern TYPED_ERROR = Pattern.compile("Error\\s+[Tt]ype\\s+(\\d+).*");
  private static final String ERROR_PREFIX = "Error";

  public static final int NO_ERROR = -2;
  public static final int UNTYPED_ERROR = -1;

  private final ApkProvider myApkProvider;

  private String myTargetPackageName;
  private final AndroidFacet myFacet;
  private final String myCommandLine;
  private final AndroidApplicationLauncher myApplicationLauncher;
  private final AndroidRunConfigurationBase myConfiguration;

  private final Object myDebugLock = new Object();

  @NotNull
  private volatile IDevice[] myTargetDevices = EMPTY_DEVICE_ARRAY;

  private volatile String myAvdName;
  private volatile boolean myDebugMode;
  private volatile boolean myOpenLogcatAutomatically;
  private volatile boolean myFilterLogcatAutomatically;

  private volatile DebugLauncher myDebugLauncher;

  private final ExecutionEnvironment myEnv;

  private volatile boolean myStopped;
  private volatile ProcessHandler myProcessHandler;
  private final Object myLock = new Object();

  private volatile boolean myDeploy = true;

  private volatile boolean myApplicationDeployed = false;

  private ConsoleView myConsole;
  private TargetChooser myTargetChooser;
  private final boolean mySupportMultipleDevices;
  private final boolean myClearLogcatBeforeStart;
  private final List<AndroidRunningStateListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final boolean myNonDebuggableOnDevice;

  public void setDebugMode(boolean debugMode) {
    myDebugMode = debugMode;
  }

  @Nullable
  public DebugLauncher getDebugLauncher() {
    return myDebugLauncher;
  }

  public void setDebugLauncher(@NotNull DebugLauncher debugLauncher) {
    myDebugLauncher = debugLauncher;
  }

  public boolean isDebugMode() {
    return myDebugMode;
  }

  private static void runInDispatchedThread(@NotNull Runnable r, boolean blocking) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      r.run();
    }
    else if (blocking) {
      application.invokeAndWait(r, ModalityState.defaultModalityState());
    }
    else {
      application.invokeLater(r);
    }
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    Project project = myFacet.getModule().getProject();
    myProcessHandler = new DefaultDebugProcessHandler();
    AndroidProcessText.attach(myProcessHandler);
    ConsoleView console = null;
    if (isDebugMode()) {
      final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
      console = builder.getConsole();
      if (console != null) {
        console.attachToProcess(myProcessHandler);
      }
    }

    final CloudConfigurationProvider provider = CloudConfigurationProvider.getCloudConfigurationProvider();
    boolean debugMatrixOnCloud = myTargetChooser instanceof CloudTargetChooser &&
                                 ((CloudTargetChooser)myTargetChooser).getConfigurationKind() == MATRIX &&
                                 executor instanceof DefaultDebugExecutor;

    // Show the device chooser if either the config specifies it, or if the request is to debug a matrix of devices on cloud
    // (which does not make sense).
    if (myTargetChooser instanceof ManualTargetChooser || debugMatrixOnCloud) {
      if (myConfiguration.USE_LAST_SELECTED_DEVICE) {
        DeviceStateAtLaunch lastLaunchState = myConfiguration.getDevicesUsedInLastLaunch();

        if (lastLaunchState != null) {
          Set<IDevice> onlineDevices = getOnlineDevices();
          if (lastLaunchState.matchesCurrentAvailableDevices(onlineDevices)) {
            Collection<IDevice> usedDevices = lastLaunchState.filterByUsed(onlineDevices);
            myTargetDevices = usedDevices.toArray(new IDevice[usedDevices.size()]);
          }
        }

        if (myTargetDevices.length > 1 && !mySupportMultipleDevices) {
          myTargetDevices = EMPTY_DEVICE_ARRAY;
        }
      }

      if (myTargetDevices.length == 0) {
        AndroidPlatform platform = myFacet.getConfiguration().getAndroidPlatform();
        if (platform == null) {
          LOG.error("Android platform not set for module: " + myFacet.getModule().getName());
          return null;
        }

        boolean showCloudTarget = getConfiguration() instanceof AndroidTestRunConfiguration && !(executor instanceof DefaultDebugExecutor);
        final ExtendedDeviceChooserDialog chooser =
          new ExtendedDeviceChooserDialog(myFacet, platform.getTarget(), mySupportMultipleDevices, true,
                                          myConfiguration.USE_LAST_SELECTED_DEVICE, showCloudTarget, myCommandLine);
        chooser.show();
        if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
          return null;
        }

        if (chooser.isToLaunchEmulator()) {
          final String selectedAvd = chooser.getSelectedAvd();
          if (selectedAvd == null) {
            return null;
          }
          myTargetChooser = new EmulatorTargetChooser(selectedAvd);
          myAvdName = selectedAvd;
        }
        else if (chooser.isCloudTestOptionSelected()) {
          return provider
            .executeCloudMatrixTests(chooser.getSelectedMatrixConfigurationId(), chooser.getChosenCloudProjectId(), this, executor);
        }
        else {
          final IDevice[] selectedDevices = chooser.getSelectedDevices();
          if (selectedDevices.length == 0) {
            return null;
          }
          myTargetDevices = selectedDevices;

          if (chooser.useSameDevicesAgain()) {
            myConfiguration.USE_LAST_SELECTED_DEVICE = true;
            myConfiguration.setDevicesUsedInLaunch(Sets.newHashSet(selectedDevices), getOnlineDevices());
          } else {
            myConfiguration.USE_LAST_SELECTED_DEVICE = false;
            myConfiguration.setDevicesUsedInLaunch(Collections.<IDevice>emptySet(), Collections.<IDevice>emptySet());
          }
        }
      }
    }
    else if (myTargetChooser instanceof CloudTargetChooser) {
      assert provider != null;
      final CloudTargetChooser cloudTargetChooser = (CloudTargetChooser)myTargetChooser;
      if (cloudTargetChooser.getConfigurationKind() == MATRIX) {
        return provider
          .executeCloudMatrixTests(cloudTargetChooser.getCloudConfigurationId(), cloudTargetChooser.getCloudProjectId(), this, executor);
      }
      else {
        assert cloudTargetChooser.getConfigurationKind() == SINGLE_DEVICE;
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            provider.launchCloudDevice(cloudTargetChooser.getCloudConfigurationId(), cloudTargetChooser.getCloudProjectId(), myFacet);
          }
        });
        return new DefaultExecutionResult(console, myProcessHandler);
      }
    }
    else if (myTargetChooser instanceof CloudDebuggingTargetChooser) {
      assert provider != null;
      myTargetDevices = EMPTY_DEVICE_ARRAY;
      String cloudDeviceSerialNumber = ((CloudDebuggingTargetChooser)myTargetChooser).getCloudDeviceSerialNumber();
      for (IDevice device : AndroidDebugBridge.getBridge().getDevices()) {
        if (device.getSerialNumber().equals(cloudDeviceSerialNumber)) {
          myTargetDevices = new IDevice[] {device};
          break;
        }
      }
      if (myTargetDevices.length == 0) { // No matching cloud device available.
        return null;
      }
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        start(true);
      }
    });

    if (console == null) { //Will not be null in debug mode or if additional option was chosen.
      console = myConfiguration.attachConsole(this, executor);
    }
    myConsole = console;

    return new DefaultExecutionResult(console, myProcessHandler);
  }

  private Set<IDevice> getOnlineDevices() {
    AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(myFacet.getModule().getProject());
    if (debugBridge == null) {
      return Collections.emptySet();
    }

    return Sets.newHashSet(debugBridge.getDevices());
  }

  @Override
  @NotNull
  public AndroidRunConfigurationBase getConfiguration() {
    return myConfiguration;
  }

  public ExecutionEnvironment getEnvironment() {
    return myEnv;
  }

  public boolean isStopped() {
    return myStopped;
  }

  public Object getRunningLock() {
    return myLock;
  }

  public String getPackageName() {
    try {
      return myApkProvider.getPackageName();
    } catch (ApkProvisionException e) {
      return null;
    }
  }

  public String getTestPackageName() {
    try {
      return myApkProvider.getTestPackageName();
    } catch (ApkProvisionException e) {
      return null;
    }
  }

  public Module getModule() {
    return myFacet.getModule();
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @Override
  public IDevice[] getDevices() {
    return myTargetDevices;
  }

  @Nullable
  @Override
  public ConsoleView getConsoleView() {
    return myConsole;
  }

  public class MyReceiver extends AndroidOutputReceiver {
    private int errorType = NO_ERROR;
    private String failureMessage = null;
    private final StringBuilder output = new StringBuilder();

    @Override
    protected void processNewLine(String line) {
      if (line.length() > 0) {
        Matcher failureMatcher = FAILURE.matcher(line);
        if (failureMatcher.matches()) {
          failureMessage = failureMatcher.group(1);
        }
        Matcher errorMatcher = TYPED_ERROR.matcher(line);
        if (errorMatcher.matches()) {
          errorType = Integer.parseInt(errorMatcher.group(1));
        }
        else if (line.startsWith(ERROR_PREFIX) && errorType == NO_ERROR) {
          errorType = UNTYPED_ERROR;
        }
      }
      output.append(line).append('\n');
    }

    public int getErrorType() {
      return errorType;
    }

    @Override
    public boolean isCancelled() {
      return myStopped;
    }

    public StringBuilder getOutput() {
      return output;
    }
  }

  public AndroidRunningState(@NotNull ExecutionEnvironment environment,
                             @NotNull AndroidFacet facet,
                             @NotNull ApkProvider apkProvider,
                             @Nullable TargetChooser targetChooser,
                             @NotNull String commandLine,
                             AndroidApplicationLauncher applicationLauncher,
                             boolean supportMultipleDevices,
                             boolean clearLogcatBeforeStart,
                             @NotNull AndroidRunConfigurationBase configuration,
                             boolean nonDebuggableOnDevice) {
    myFacet = facet;
    myApkProvider = apkProvider;
    myCommandLine = commandLine;
    myConfiguration = configuration;

    myTargetChooser = targetChooser;
    mySupportMultipleDevices = supportMultipleDevices;

    myAvdName = targetChooser instanceof EmulatorTargetChooser
                ? ((EmulatorTargetChooser)targetChooser).getAvd()
                : null;

    myEnv = environment;
    myApplicationLauncher = applicationLauncher;
    myClearLogcatBeforeStart = clearLogcatBeforeStart;
    myNonDebuggableOnDevice = nonDebuggableOnDevice;
  }

  public void setDeploy(boolean deploy) {
    myDeploy = deploy;
  }

  public void setTargetPackageName(String targetPackageName) {
    synchronized (myDebugLock) {
      myTargetPackageName = targetPackageName;
    }
  }

  @Nullable
  private IDevice[] chooseDevicesAutomatically() {
    final List<IDevice> compatibleDevices = getAllCompatibleDevices();

    if (compatibleDevices.size() == 0) {
      return EMPTY_DEVICE_ARRAY;
    }
    else if (compatibleDevices.size() == 1) {
      return new IDevice[] {compatibleDevices.get(0)};
    }
    else {
      final IDevice[][] devicesWrapper = {null};
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          devicesWrapper[0] = chooseDevicesManually(new Predicate<IDevice>() {
            @Override
            public boolean apply(IDevice device) {
              return isCompatibleDevice(device) != Boolean.FALSE;
            }
          });
        }
      }, ModalityState.defaultModalityState());
      return devicesWrapper[0].length > 0 ? devicesWrapper[0] : null;
    }
  }

  @NotNull
  List<IDevice> getAllCompatibleDevices() {
    final List<IDevice> compatibleDevices = new ArrayList<IDevice>();
    final AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();

    if (bridge != null) {
      IDevice[] devices = bridge.getDevices();

      for (IDevice device : devices) {
        if (isCompatibleDevice(device) != Boolean.FALSE) {
          compatibleDevices.add(device);
        }
      }
    }
    return compatibleDevices;
  }

  private void chooseAvd() {
    IAndroidTarget buildTarget = myFacet.getConfiguration().getAndroidTarget();
    assert buildTarget != null;
    AvdInfo[] avds = myFacet.getValidCompatibleAvds();
    if (avds.length > 0) {
      myAvdName = avds[0].getName();
    }
    else {
      final Project project = myFacet.getModule().getProject();
      AvdManager manager = null;
      try {
        manager = myFacet.getAvdManager(new AvdManagerLog() {
          @Override
          public void error(Throwable t, String errorFormat, Object... args) {
            super.error(t, errorFormat, args);

            if (errorFormat != null) {
              final String msg = String.format(errorFormat, args);
              message(msg, STDERR);
            }
          }
        });
      }
      catch (AvdsNotSupportedException e) {
        // can't be
        LOG.error(e);
      }
      catch (final AndroidLocation.AndroidLocationException e) {
        LOG.info(e);
        runInDispatchedThread(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
          }
        }, false);
        return;
      }
      final AvdManager finalManager = manager;
      assert finalManager != null;
      runInDispatchedThread(new Runnable() {
        @Override
        public void run() {
          CreateAvdDialog dialog = new CreateAvdDialog(project, myFacet, finalManager, true, true);
          dialog.show();
          if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            AvdInfo createdAvd = dialog.getCreatedAvd();
            if (createdAvd != null) {
              myAvdName = createdAvd.getName();
            }
          }
        }
      }, true);
    }
  }

  void start(boolean chooseTargetDevice) {
    try {
      setTargetPackageName(myApkProvider.getPackageName());
    } catch (ApkProvisionException e) {
      message(e.getMessage(), STDERR);
      LOG.error(e);
      getProcessHandler().destroyProcess();
      return;
    }

    if (chooseTargetDevice) {
      //TODO: Why this message sometimes does not show up when a not yet booted device is picked?
      message("Waiting for device.", STDOUT);

      if (myTargetDevices.length == 0 && !chooseOrLaunchDevice()) {
        getProcessHandler().destroyProcess();
        fireExecutionFailed();
        return;
      }
    }
    doStart();
  }

  private void doStart() {
    if (myDebugMode) {
      AndroidDebugBridge.addClientChangeListener(this);
    }
    final DeviceReadyListener[] deviceListener = {null};
    getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        if (myDebugMode) {
          AndroidDebugBridge.removeClientChangeListener(AndroidRunningState.this);
        }
        if (deviceListener[0] != null) {
          Disposer.dispose(deviceListener[0]);
          AndroidDebugBridge.removeDeviceChangeListener(deviceListener[0]);
        }
        myStopped = true;
        synchronized (myLock) {
          myLock.notifyAll();
        }
      }
    });
    deviceListener[0] = prepareAndStartAppWhenDeviceIsOnline();
  }

  private boolean chooseOrLaunchDevice() {
    IDevice[] targetDevices = chooseDevicesAutomatically();
    if (targetDevices == null) {
      message("Canceled", STDERR);
      return false;
    }

    if (targetDevices.length > 0) {
      myTargetDevices = targetDevices;
    }
    else if (myTargetChooser instanceof EmulatorTargetChooser) {
      if (myAvdName == null) {
        chooseAvd();
      }
      if (myAvdName != null) {
        myFacet.launchEmulator(myAvdName, myCommandLine);
      }
      else if (getProcessHandler().isStartNotified()) {
        message("Canceled", STDERR);
        return false;
      }
    }
    else {
      message("USB device not found", STDERR);
      return false;
    }
    return true;
  }

  @NotNull
  private IDevice[] chooseDevicesManually(@Nullable Predicate<IDevice> filter) {
    final Project project = myFacet.getModule().getProject();
    String value = PropertiesComponent.getInstance(project).getValue(ANDROID_TARGET_DEVICES_PROPERTY);
    String[] selectedSerials = value != null ? fromString(value) : null;
    AndroidPlatform platform = myFacet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      LOG.error("Android platform not set for module: " + myFacet.getModule().getName());
      return DeviceChooser.EMPTY_DEVICE_ARRAY;
    }
    DeviceChooserDialog chooser = new DeviceChooserDialog(myFacet, platform.getTarget(), mySupportMultipleDevices, selectedSerials, filter);
    chooser.show();
    IDevice[] devices = chooser.getSelectedDevices();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE || devices.length == 0) {
      return DeviceChooser.EMPTY_DEVICE_ARRAY;
    }
    PropertiesComponent.getInstance(project).setValue(ANDROID_TARGET_DEVICES_PROPERTY, toString(devices));
    return devices;
  }

  @NotNull
  public static String toString(@NotNull IDevice[] devices) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0, n = devices.length; i < n; i++) {
      builder.append(devices[i].getSerialNumber());
      if (i < n - 1) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  @NotNull
  private static String[] fromString(@NotNull String s) {
    return s.split(" ");
  }

  public void message(@NotNull String message, @NotNull Key outputKey) {
    getProcessHandler().notifyTextAvailable(message + '\n', outputKey);
  }

  @Override
  public void clientChanged(Client client, int changeMask) {
    synchronized (myDebugLock) {
      if (myDebugLauncher == null) {
        return;
      }
      if (myDeploy && !myApplicationDeployed) {
        return;
      }
      IDevice device = client.getDevice();
      if (isMyDevice(device) && device.isOnline()) {
        if (myTargetDevices.length == 0) {
          myTargetDevices = new IDevice[]{device};
        }
        ClientData data = client.getClientData();
        if (myDebugLauncher != null && isToLaunchDebug(data)) {
          launchDebug(client);
        }
      }
    }
  }

  private boolean isToLaunchDebug(@NotNull ClientData data) {
    if (data.getDebuggerConnectionStatus() == ClientData.DebuggerStatus.WAITING) {
      // early exit without checking package name in case the debug package doesn't match
      // our target package name. This happens for instance when debugging a test that doesn't launch an application
      return true;
    }
    String description = data.getClientDescription();
    if (description == null) {
      return false;
    }
    return description.equals(myTargetPackageName) && myApplicationLauncher.isReadyForDebugging(data, getProcessHandler());
  }

  private void launchDebug(@NotNull Client client) {
    myDebugLauncher.launchDebug(client);
    myDebugLauncher = null;
  }

  @Nullable
  Boolean isCompatibleDevice(@NotNull IDevice device) {
    if (myTargetChooser instanceof EmulatorTargetChooser) {
      if (device.isEmulator()) {
        String avdName = device.getAvdName();
        if (myAvdName != null) {
          return myAvdName.equals(avdName);
        }

        AndroidPlatform androidPlatform = myFacet.getConfiguration().getAndroidPlatform();
        if (androidPlatform == null) {
          LOG.error("Target Android platform not set for module: " + myFacet.getModule().getName());
          return false;
        } else {
          LaunchCompatibility compatibility = LaunchCompatibility.canRunOnDevice(AndroidModuleInfo.get(myFacet).getRuntimeMinSdkVersion(),
                                                                                 androidPlatform.getTarget(),
                                                                                 EnumSet.noneOf(IDevice.HardwareFeature.class),
                                                                                 device,
                                                                                 null);
          return compatibility.isCompatible() != ThreeState.NO;
        }
      }
    }
    else if (myTargetChooser instanceof UsbDeviceTargetChooser) {
      return !device.isEmulator();
    }
    else if (myTargetChooser instanceof ManualTargetChooser && myConfiguration.USE_LAST_SELECTED_DEVICE) {
      DeviceStateAtLaunch lastLaunchState = myConfiguration.getDevicesUsedInLastLaunch();
      return lastLaunchState != null && lastLaunchState.usedDevice(device);
    }
    return false;
  }

  private boolean isMyDevice(@NotNull IDevice device) {
    if (myTargetDevices.length > 0) {
      return ArrayUtilRt.find(myTargetDevices, device) >= 0;
    }
    Boolean compatible = isCompatibleDevice(device);
    return compatible == null || compatible.booleanValue();
  }

  public void setTargetDevices(@NotNull IDevice[] targetDevices) {
    myTargetDevices = targetDevices;
  }

  public void setConsole(@NotNull ConsoleView console) {
    myConsole = console;
  }

  @Nullable
  private DeviceReadyListener prepareAndStartAppWhenDeviceIsOnline() {
    if (myTargetDevices.length > 0) {
      boolean allDevicesOnline = true;
      for (IDevice targetDevice : myTargetDevices) {
        if (targetDevice.isOnline()) {
          if (!prepareAndStartApp(targetDevice) && !myStopped) {
            // todo: check: it may be we don't need to assign it directly
            // TODO: Why stop completely for a problem potentially affecting only a single device?
            myStopped = true;
            getProcessHandler().destroyProcess();
            break;
          }
        }
        else {
          allDevicesOnline = false;
        }
      }
      // If all target devices are online, we are done.
      if (allDevicesOnline) {
        if (!myDebugMode && !myStopped) {
          getProcessHandler().destroyProcess();
        }
        return null;
      }
    }
    Predicate<IDevice> deviceFilter = new Predicate<IDevice>() {
      @Override
      public boolean apply(IDevice device) {
        return isMyDevice(device);
      }
    };
    DeviceReadyListener.Callback callback = new DeviceReadyListener.Callback() {
      @Override
      public void onDeviceReady(@NotNull IDevice device) {
        if ((!prepareAndStartApp(device) || !myDebugMode) && !myStopped) {
          getProcessHandler().destroyProcess();
        }
      }
    };
    final DeviceReadyListener deviceListener = new DeviceReadyListener(
      new ProcessHandlerSimpleLogger(getProcessHandler()),
      deviceFilter,
      callback);
    AndroidDebugBridge.addDeviceChangeListener(deviceListener);
    return deviceListener;
  }

  public synchronized void setProcessHandler(ProcessHandler processHandler) {
    myProcessHandler = processHandler;
  }

  public synchronized ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  private boolean prepareAndStartApp(IDevice device) {
    if (myDebugMode && myNonDebuggableOnDevice && !device.isEmulator()) {
      message(AndroidBundle.message("android.cannot.debug.noDebugPermissions", getPackageName(), device.getName()), STDERR);
      fireExecutionFailed();
      return false;
    }
    if (!doPrepareAndStart(device)) {
      fireExecutionFailed();
      return false;
    }
    return true;
  }

  private void fireExecutionFailed() {
    for (AndroidRunningStateListener listener : myListeners) {
      listener.executionFailed();
    }
  }

  public void setOpenLogcatAutomatically(boolean openLogcatAutomatically) {
    myOpenLogcatAutomatically = openLogcatAutomatically;
  }

  public void setFilterLogcatAutomatically(boolean filterLogcatAutomatically) {
    myFilterLogcatAutomatically = filterLogcatAutomatically;
  }

  private boolean doPrepareAndStart(@NotNull final IDevice device) {
    if (myClearLogcatBeforeStart) {
      clearLogcatAndConsole(getModule().getProject(), device);
    }

    message("Target device: " + device.getName(), STDOUT);
    try {
      if (myDeploy) {
        Collection<ApkInfo> apks;
        try {
          apks = myApkProvider.getApks(device);
        } catch (ApkProvisionException e) {
          message(e.getMessage(), STDERR);
          LOG.warn(e);
          return false;
        }
        for (ApkInfo apk : apks) {
          if (!uploadAndInstallApk(device, apk.getApplicationId(), apk.getFile())) {
            return false;
          }
        }
        trackInstallation(device);
        myApplicationDeployed = true;
      }
      final AndroidApplicationLauncher.LaunchResult launchResult =
        myApplicationLauncher.launch(this, device);

      if (launchResult == AndroidApplicationLauncher.LaunchResult.STOP) {
        return false;
      }
      else if (launchResult == AndroidApplicationLauncher.LaunchResult.SUCCESS) {
        checkDdms();
      }

      final Client client;
      synchronized (myDebugLock) {
        client = device.getClient(myTargetPackageName);
        if (myDebugLauncher != null) {
          if (client != null &&
              myApplicationLauncher.isReadyForDebugging(client.getClientData(), getProcessHandler())) {
            launchDebug(client);
          }
          else {
            message("Waiting for process: " + myTargetPackageName, STDOUT);
          }
        }
      }
      if (!myDebugMode && myOpenLogcatAutomatically) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            final ToolWindow androidToolWindow = ToolWindowManager.getInstance(myEnv.getProject()).
              getToolWindow(AndroidToolWindowFactory.TOOL_WINDOW_ID);

            // Activate the tool window, and once activated, make sure the right device is selected
            androidToolWindow.activate(new Runnable() {
              @Override
              public void run() {
                int count = androidToolWindow.getContentManager().getContentCount();
                for (int i = 0; i < count; i++) {
                  Content content = androidToolWindow.getContentManager().getContent(i);
                  DevicePanel devicePanel = content == null ? null : content.getUserData(AndroidToolWindowFactory.DEVICES_PANEL_KEY);
                  if (devicePanel != null) {
                    devicePanel.selectDevice(device);
                    devicePanel.selectClient(client);
                    break;
                  }
                }
              }
            }, false);
          }
        });
      }
      return true;
    }
    catch (TimeoutException e) {
      LOG.info(e);
      message("Error: Connection to ADB failed with a timeout", STDERR);
      return false;
    }
    catch (AdbCommandRejectedException e) {
      LOG.info(e);
      message("Error: Adb refused a command", STDERR);
      return false;
    }
    catch (IOException e) {
      LOG.info(e);
      String message = e.getMessage();
      message("I/O Error" + (message != null ? ": " + message : ""), STDERR);
      return false;
    }
  }

  private static int ourInstallationCount = 0;

  private static void trackInstallation(@NotNull IDevice device) {
    if (!UsageTracker.getInstance().canTrack()) {
      return;
    }

    // only track every 10th installation (just to reduce the load on the server)
    ourInstallationCount = (ourInstallationCount + 1) % 10;
    if (ourInstallationCount != 0) {
      return;
    }

    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEPLOYMENT, UsageTracker.ACTION_DEPLOYMENT_APK, null, null);

    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_SERIAL_HASH,
                                          Hashing.md5().hashString(device.getSerialNumber(), Charsets.UTF_8).toString(), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_BUILD_TAGS,
                                          device.getProperty(IDevice.PROP_BUILD_TAGS), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_BUILD_TYPE,
                                          device.getProperty(IDevice.PROP_BUILD_TYPE), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_BUILD_VERSION_RELEASE,
                                          device.getProperty(IDevice.PROP_BUILD_VERSION), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_BUILD_API_LEVEL,
                                          device.getProperty(IDevice.PROP_BUILD_API_LEVEL), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_MANUFACTURER,
                                          device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_MODEL,
                                          device.getProperty(IDevice.PROP_DEVICE_MODEL), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_CPU_ABI,
                                          device.getProperty(IDevice.PROP_DEVICE_CPU_ABI), null);
  }

  protected static void clearLogcatAndConsole(@NotNull final Project project, @NotNull final IDevice device) {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AndroidToolWindowFactory.TOOL_WINDOW_ID);
        if (toolWindow == null) {
          return;
        }

        for (Content content : toolWindow.getContentManager().getContents()) {
          final AndroidLogcatView view = content.getUserData(AndroidLogcatView.ANDROID_LOGCAT_VIEW_KEY);

          if (view != null) {
            view.clearLogcat(device);
          }
        }
      }
    }, ModalityState.defaultModalityState());
  }

  private boolean checkDdms() {
    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (myDebugMode && bridge != null && AdbService.canDdmsBeCorrupted(bridge)) {
      message(AndroidBundle.message("ddms.corrupted.error"), STDERR);
      JComponent component = myConsole == null ? null : myConsole.getComponent();
      if (component != null) {
        final ExecutionEnvironment environment = LangDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component));
        if (environment == null) {
          return false;
        }

        myConsole.printHyperlink(AndroidBundle.message("restart.adb.fix.text"), new HyperlinkInfo() {
          @Override
          public void navigate(Project project) {
            AdbService.getInstance().restartDdmlib(project);

            final ProcessHandler processHandler = getProcessHandler();
            if (!processHandler.isProcessTerminated()) {
              processHandler.destroyProcess();
            }
            ExecutionUtil.restart(environment);
          }
        });
        myConsole.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
      }
      return false;
    }
    return true;
  }

  /**
   * Installs the given apk on the device.
   * @return whether the installation was successful
   */
  private boolean uploadAndInstallApk(@NotNull IDevice device, @NotNull String packageName, @NotNull File localFile)
    throws IOException, AdbCommandRejectedException, TimeoutException {

    if (myStopped) return false;
    String remotePath = "/data/local/tmp/" + packageName;
    String exceptionMessage;
    String errorMessage;
    message("Uploading file\n\tlocal path: " + localFile + "\n\tremote path: " + remotePath, STDOUT);
    try {
      InstalledApks installedApks = ServiceManager.getService(InstalledApks.class);
      if (installedApks.isInstalled(device, localFile, packageName)) {
        message("No apk changes detected. Skipping file upload, force stopping package instead.", STDOUT);
        forceStopPackageSilently(device, packageName, true);
        return true;
      } else {
        device.pushFile(localFile.getPath(), remotePath);
        boolean installed = installApp(device, remotePath, packageName);
        if (installed) {
          installedApks.setInstalled(device, localFile, packageName);
        }
        return installed;
      }
    }
    catch (TimeoutException e) {
      LOG.info(e);
      exceptionMessage = e.getMessage();
      errorMessage = "Connection timeout";
    }
    catch (AdbCommandRejectedException e) {
      LOG.info(e);
      exceptionMessage = e.getMessage();
      errorMessage = "ADB refused the command";
    }
    catch (final SyncException e) {
      LOG.info(e);
      final SyncException.SyncError errorCode = e.getErrorCode();

      if (SyncException.SyncError.NO_LOCAL_FILE.equals(errorCode)) {
        // Sometimes, users see the issue that for Gradle projects, the apk location used is incorrect (points to build/classes/?.apk
        // instead of build/apk/?.apk).
        // This happens reasonably often, but isn't reproducible, so we add this workaround here to show a popup to 'Sync Project with
        // Gradle Files' if it is a gradle project.
        // See https://code.google.com/p/android/issues/detail?id=59018 for more info.

        // The problem is that at this point, the project maybe a Gradle-based project, but its AndroidGradleModel may be null.
        // We can check if there is a top-level build.gradle or settings.gradle file.
        DataManager.getInstance().getDataContextFromFocus().doWhenDone(new Consumer<DataContext>() {
          @Override
          public void consume(DataContext dataContext) {
            if (dataContext != null) {
              Project project = CommonDataKeys.PROJECT.getData(dataContext);
              if (project != null && hasGradleFiles(project)) {
                AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);
                String message =
                  errorCode.getMessage() + '\n' + e.getMessage() + '\n' + "The project may need to be synced with Gradle files.";
                notification.showBalloon("Unexpected Error", message, NotificationType.ERROR, new SyncProjectHyperlink());
              }
            }
          }

          private boolean hasGradleFiles(@NotNull Project project) {
            File rootDirPath = new File(FileUtil.toSystemDependentName(project.getBasePath()));
            return GradleUtil.getGradleBuildFilePath(rootDirPath).isFile() || GradleUtil.getGradleSettingsFilePath(rootDirPath).isFile();
          }
        });
      }

      errorMessage = errorCode.getMessage();
      exceptionMessage = e.getMessage();
    }
    if (errorMessage.equals(exceptionMessage) || exceptionMessage == null) {
      message(errorMessage, STDERR);
    }
    else {
      message(errorMessage + '\n' + exceptionMessage, STDERR);
    }
    return false;
  }

  /** Attempts to force stop package running on given device. */
  private void forceStopPackageSilently(@NotNull IDevice device, @NotNull String packageName, boolean ignoreErrors) {
    try {
      executeDeviceCommandAndWriteToConsole(device, "am force-stop " + packageName, new MyReceiver());
    }
    catch (Exception e) {
      if (!ignoreErrors) {
        throw new RuntimeException(e);
      }
    }
  }

  @SuppressWarnings({"DuplicateThrows"})
  public void executeDeviceCommandAndWriteToConsole(@NotNull IDevice device,
                                                    @NotNull String command,
                                                    @NotNull AndroidOutputReceiver receiver)
    throws IOException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException {
    message("DEVICE SHELL COMMAND: " + command, STDOUT);
    AndroidUtils.executeCommandOnDevice(device, command, receiver, false);
  }

  private boolean installApp(@NotNull IDevice device, @NotNull String remotePath, @NotNull String packageName)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    message("Installing " + packageName, STDOUT);

    InstallResult result = null;
    boolean retry = true;
    while (!myStopped && retry) {
      result = installApp(device, remotePath);
      if (result.installOutput != null) {
        message(result.installOutput, result.failureCode == InstallFailureCode.NO_ERROR ? STDOUT : STDERR);
      }

      switch (result.failureCode) {
        case DEVICE_NOT_RESPONDING:
          message("Device is not ready. Waiting for " + WAITING_TIME_SECS + " sec.", STDOUT);
          synchronized (myLock) {
            try {
              myLock.wait(WAITING_TIME_SECS * 1000);
            }
            catch (InterruptedException e) {
              LOG.info(e);
            }
          }
          retry = true;
          break;
        case INSTALL_FAILED_VERSION_DOWNGRADE:
          String reason = AndroidBundle.message("deployment.failed.uninstall.prompt.text",
                                                AndroidBundle.message("deployment.failed.reason.version.downgrade"));
          retry = promptUninstallExistingApp(reason) && uninstallPackage(device, packageName);
          break;
        case INCONSISTENT_CERTIFICATES:
          reason = AndroidBundle.message("deployment.failed.uninstall.prompt.text",
                                         AndroidBundle.message("deployment.failed.reason.different.signature"));
          retry = promptUninstallExistingApp(reason) && uninstallPackage(device, packageName);
          break;
        case INSTALL_FAILED_DEXOPT:
          reason = AndroidBundle.message("deployment.failed.uninstall.prompt.text",
                                         AndroidBundle.message("deployment.failed.reason.dexopt"));
          retry = promptUninstallExistingApp(reason) && uninstallPackage(device, packageName);
          break;
        case NO_CERTIFICATE:
          message(AndroidBundle.message("deployment.failed.no.certificates.explanation"), STDERR);
          showMessageDialog(AndroidBundle.message("deployment.failed.no.certificates.explanation"));
          retry = false;
          break;
        case INSTALL_FAILED_OLDER_SDK:
          reason = validateSdkVersion(device);
          if (reason != null) {
            if (shouldOpenProjectStructure(reason)) {
              openProjectStructure();
            }
            retry =  false;  // Don't retry as there needs to be another sync and build.
            break;
          }
          // Maybe throw an exception because this shouldn't happen. But let it fall through to UNTYPED_ERROR for now.
        case UNTYPED_ERROR:
          reason = AndroidBundle.message("deployment.failed.uninstall.prompt.generic.text", result.failureMessage);
          retry = promptUninstallExistingApp(reason) && uninstallPackage(device, packageName);
          break;
        default:
          retry = false;
          break;
      }
    }

    return result != null && result.failureCode == InstallFailureCode.NO_ERROR;
  }

  private boolean uninstallPackage(@NotNull IDevice device, @NotNull String packageName) {
    message("DEVICE SHELL COMMAND: pm uninstall " + packageName, STDOUT);
    String output;
    try {
      output = device.uninstallPackage(packageName);
    }
    catch (InstallException e) {
      return false;
    }

    if (output != null) {
      message(output, STDERR);
      return false;
    }
    return true;
  }

  private boolean promptUninstallExistingApp(final String reason) {
    final AtomicBoolean uninstall = new AtomicBoolean(false);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        int result = Messages.showOkCancelDialog(myFacet.getModule().getProject(),
                                                 reason,
                                                 AndroidBundle.message("deployment.failed.title"),
                                                 Messages.getQuestionIcon());
        uninstall.set(result == Messages.OK);
      }
    }, ModalityState.defaultModalityState());

    return uninstall.get();
  }

  private void showMessageDialog(@NotNull final String message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(myFacet.getModule().getProject(), message, AndroidBundle.message("deployment.failed.title"));
      }
    });
  }

  private enum InstallFailureCode {
    NO_ERROR,
    DEVICE_NOT_RESPONDING,
    INCONSISTENT_CERTIFICATES,
    INSTALL_FAILED_VERSION_DOWNGRADE,
    INSTALL_FAILED_DEXOPT,
    NO_CERTIFICATE,
    INSTALL_FAILED_OLDER_SDK,
    UNTYPED_ERROR
  }

  private static class InstallResult {
    public final InstallFailureCode failureCode;
    @Nullable public final String failureMessage;
    @Nullable public final String installOutput;

    public InstallResult(InstallFailureCode failureCode, @Nullable String failureMessage, @Nullable String installOutput) {
      this.failureCode = failureCode;
      this.failureMessage = failureMessage;
      this.installOutput = installOutput;
    }
  }

  private InstallResult installApp(@NotNull IDevice device, @NotNull String remotePath)
    throws AdbCommandRejectedException, TimeoutException, IOException {

    MyReceiver receiver = new MyReceiver();
    try {
      executeDeviceCommandAndWriteToConsole(device, "pm install -r \"" + remotePath + "\"", receiver);
    }
    catch (ShellCommandUnresponsiveException e) {
      LOG.info(e);
      return new InstallResult(InstallFailureCode.DEVICE_NOT_RESPONDING, null, null);
    }

    return new InstallResult(getFailureCode(receiver),
                             receiver.failureMessage,
                             receiver.output.toString());
  }

  private String validateSdkVersion(@NotNull IDevice device) {
    AndroidVersion deviceVersion = DevicePropertyUtil.getDeviceVersion(device);
    AndroidVersion minSdkVersion = myFacet.getAndroidModuleInfo().getRuntimeMinSdkVersion();
    if (!deviceVersion.canRun(minSdkVersion)) {
      message("Device API level: " + deviceVersion.toString(), STDERR); // Log the device version to console for easy reference.
      return AndroidBundle.message("deployment.failed.reason.oldersdk", minSdkVersion.toString(), deviceVersion.toString());
    }
    else {
      return null;
    }
  }

  private boolean shouldOpenProjectStructure(@NotNull final String reason) {
    final AtomicBoolean open = new AtomicBoolean(false);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        int result = Messages.showOkCancelDialog(myFacet.getModule().getProject(), reason,
                                                 AndroidBundle.message("deployment.failed.title"),
                                                 Messages.getQuestionIcon());
        open.set(result == Messages.OK);
      }
    }, ModalityState.defaultModalityState());

    return open.get();
  }

  /**
   * Opens the project structure dialog and selects the flavors tab.
   */
  private boolean openProjectStructure() {
    final ProjectSettingsService service = ProjectSettingsService.getInstance(myFacet.getModule().getProject());
    if (service instanceof AndroidProjectSettingsService) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ((AndroidProjectSettingsService)service).openAndSelectFlavorsEditor(myFacet.getModule());
        }
      });
    }
    return false;
  }

  private InstallFailureCode getFailureCode(MyReceiver receiver) {
    if (receiver.errorType == NO_ERROR && receiver.failureMessage == null) {
      return InstallFailureCode.NO_ERROR;
    }

    if ("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES".equals(receiver.failureMessage)) {
      return InstallFailureCode.INCONSISTENT_CERTIFICATES;
    } else if ("INSTALL_PARSE_FAILED_NO_CERTIFICATES".equals(receiver.failureMessage)) {
      return InstallFailureCode.NO_CERTIFICATE;
    } else if ("INSTALL_FAILED_VERSION_DOWNGRADE".equals(receiver.failureMessage)) {
      return InstallFailureCode.INSTALL_FAILED_VERSION_DOWNGRADE;
    } else if ("INSTALL_FAILED_DEXOPT".equals(receiver.failureMessage)) {
      return InstallFailureCode.INSTALL_FAILED_DEXOPT;
    } else if ("INSTALL_FAILED_OLDER_SDK".equals(receiver.failureMessage)) {
      return InstallFailureCode.INSTALL_FAILED_OLDER_SDK;
    }

    return InstallFailureCode.UNTYPED_ERROR;
  }

  public void addListener(@NotNull AndroidRunningStateListener listener) {
    myListeners.add(listener);
  }
}
