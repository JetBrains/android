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

import com.android.ddmlib.*;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.fd.FastDeployManager;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.service.notification.hyperlink.SyncProjectHyperlink;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.logcat.AndroidLogcatView;
import com.android.tools.idea.monitor.AndroidToolWindowFactory;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AndroidRunningState implements RunProfileState, AndroidExecutionState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidRunningState");

  public static final int WAITING_TIME_SECS = 20;

  private final ApkProvider myApkProvider;

  private String myTargetPackageName;
  @NotNull private final AndroidFacet myFacet;
  private final AndroidApplicationLauncher myApplicationLauncher;
  @NotNull private final ProcessHandlerConsolePrinter myPrinter;
  private final AndroidRunConfigurationBase myConfiguration;

  private final Object myDebugLock = new Object();

  @NotNull private final DeviceTarget myDeviceTarget;

  private volatile boolean myDebugMode;
  private volatile boolean myOpenLogcatAutomatically;

  private volatile DebugLauncher myDebugLauncher;

  private final ExecutionEnvironment myEnv;

  private final AtomicBoolean myStopped = new AtomicBoolean(false);
  private volatile ProcessHandler myProcessHandler;
  private final Object myLock = new Object();

  private volatile boolean myDeploy = true;

  private volatile boolean myApplicationDeployed = false;

  private ConsoleView myConsole;
  private final boolean myClearLogcatBeforeStart;
  private final List<AndroidRunningStateListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public AndroidRunningState(@NotNull ExecutionEnvironment environment,
                             @NotNull AndroidFacet facet,
                             @NotNull ApkProvider apkProvider,
                             @NotNull DeviceTarget deviceTarget,
                             @NotNull ProcessHandlerConsolePrinter printer,
                             AndroidApplicationLauncher applicationLauncher,
                             boolean clearLogcatBeforeStart,
                             @NotNull AndroidRunConfigurationBase configuration) {
    myFacet = facet;
    myApkProvider = apkProvider;
    myDeviceTarget = deviceTarget;
    myPrinter = printer;
    myConfiguration = configuration;

    myEnv = environment;
    myApplicationLauncher = applicationLauncher;
    myClearLogcatBeforeStart = clearLogcatBeforeStart;
  }

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

  @Override
  @NotNull
  public AndroidRunConfigurationBase getConfiguration() {
    return myConfiguration;
  }

  public ExecutionEnvironment getEnvironment() {
    return myEnv;
  }

  public boolean isStopped() {
    return myStopped.get();
  }

  public AtomicBoolean getStoppedRef() {
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

  @Nullable
  @Override
  public Collection<IDevice> getDevices() {
    return myDeviceTarget.getDevicesIfReady();
  }

  @Nullable
  @Override
  public ConsoleView getConsoleView() {
    return myConsole;
  }

  public void setDeploy(boolean deploy) {
    myDeploy = deploy;
  }

  public void setTargetPackageName(String targetPackageName) {
    synchronized (myDebugLock) {
      myTargetPackageName = targetPackageName;
    }
  }

  @NotNull
  public ConsolePrinter getPrinter() {
    return myPrinter;
  }

  /** Listener which launches the debugger once the target device is ready. */
  private class MyClientChangeListener implements AndroidDebugBridge.IClientChangeListener {
    @NotNull private final IDevice myDevice;

    MyClientChangeListener(@NotNull IDevice device) {
      myDevice = device;
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
        if (myDevice.equals(device) && device.isOnline()) {
          ClientData data = client.getClientData();
          if (isToLaunchDebug(data)) {
            launchDebug(client);
          }
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

  public void setConsole(@NotNull ConsoleView console) {
    myConsole = console;
  }

  @Override
  public ExecutionResult execute(@NotNull final Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
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
    myPrinter.setProcessHandler(myProcessHandler);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        start();
      }
    });

    if (console == null) { //Will not be null in debug mode or if additional option was chosen.
      console = myConfiguration.attachConsole(this, executor);
    }

    getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        if (outputType.equals(ProcessOutputTypes.STDERR)) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              ToolWindowManager.getInstance(myFacet.getModule().getProject()).getToolWindow(executor.getToolWindowId())
                .activate(null, true, false);
            }
          });
        }
      }

      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        for (ListenableFuture<IDevice> deviceFuture : myDeviceTarget.getDeviceFutures()) {
          deviceFuture.cancel(true);
        }
        myStopped.set(true);
        synchronized (myLock) {
          myLock.notifyAll();
        }
        getProcessHandler().removeProcessListener(this);
      }
    });

    myConsole = console;

    return new DefaultExecutionResult(console, myProcessHandler);
  }

  void start() {
    try {
      setTargetPackageName(myApkProvider.getPackageName());
    } catch (ApkProvisionException e) {
      myPrinter.stderr(e.getMessage());
      LOG.error(e);
      getProcessHandler().destroyProcess();
      return;
    }
    final AtomicInteger startedCount = new AtomicInteger();
    for (ListenableFuture<IDevice> targetDevice : myDeviceTarget.getDeviceFutures()) {
      Futures.addCallback(targetDevice, new FutureCallback<IDevice>() {
        @Override
        public void onSuccess(@Nullable IDevice device) {
          if (myStopped.get() || device == null) {
            return;
          }
          if (myDebugMode) {
            // Listen for when the installed app is ready for debugging.
            final MyClientChangeListener listener = new MyClientChangeListener(device);
            AndroidDebugBridge.addClientChangeListener(listener);
            getProcessHandler().addProcessListener(new ProcessAdapter() {
              @Override
              public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
                AndroidDebugBridge.removeClientChangeListener(listener);
              }
            });
          }
          if (prepareAndStartApp(device)) {
            if (startedCount.incrementAndGet() == myDeviceTarget.getDeviceFutures().size() && !myDebugMode) {
              // All the devices have been started, and we don't need to wait to attach the debugger. We're done.
              myStopped.set(true);
              getProcessHandler().destroyProcess();
            }
          } else {
            fireExecutionFailed();
            // todo: check: it may be we don't need to assign it directly
            // TODO: Why stop completely for a problem potentially affecting only a single device?
            myStopped.set(true);
            getProcessHandler().destroyProcess();
          }
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          myStopped.set(true);
          getProcessHandler().destroyProcess();
        }
      });
    }
  }

  public synchronized void setProcessHandler(ProcessHandler processHandler) {
    myProcessHandler = processHandler;
    myPrinter.setProcessHandler(processHandler);
  }

  public synchronized ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  private void fireExecutionFailed() {
    for (AndroidRunningStateListener listener : myListeners) {
      listener.executionFailed();
    }
  }

  public void setOpenLogcatAutomatically(boolean openLogcatAutomatically) {
    myOpenLogcatAutomatically = openLogcatAutomatically;
  }

  private boolean prepareAndStartApp(@NotNull final IDevice device) {
    if (myDebugMode && !LaunchUtils.canDebugAppOnDevice(myFacet, device)) {
      myPrinter.stderr(AndroidBundle.message("android.cannot.debug.noDebugPermissions", getPackageName(), device.getName()));
      return false;
    }

    if (myClearLogcatBeforeStart) {
      clearLogcatAndConsole(getModule().getProject(), device);
    }

    myPrinter.stdout("Target device: " + device.getName());
    try {
      if (myDeploy) {
        Collection<ApkInfo> apks;
        try {
          apks = myApkProvider.getApks(device);
        } catch (ApkProvisionException e) {
          myPrinter.stderr(e.getMessage());
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

      // From Version 23 onwards (in the emulator, possibly later on devices), we can dismiss the keyguard
      // with "adb shell wm dismiss-keyguard". This allows the application to show up without the user having
      // to manually dismiss the keyguard.
      final AndroidVersion canDismissKeyguard = new AndroidVersion(23, null);
      if (canDismissKeyguard.compareTo(DevicePropertyUtil.getDeviceVersion(device)) <= 0) {
        // It is not necessary to wait for the keyguard to be dismissed. On a slow emulator, this seems
        // to take a while (6s on my machine)
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            try {
              device.executeShellCommand("wm dismiss-keyguard", new NullOutputReceiver(), 10, TimeUnit.SECONDS);
            }
            catch (Exception e) {
              LOG.warn("Unable to dismiss keyguard before launching activity");
            }
          }
        });
      }

      final AndroidApplicationLauncher.LaunchResult launchResult = myApplicationLauncher.launch(this, device);
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
            myPrinter.stdout("Waiting for process: " + myTargetPackageName);
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
      myPrinter.stderr("Error: Connection to ADB failed with a timeout");
      return false;
    }
    catch (AdbCommandRejectedException e) {
      LOG.info(e);
      myPrinter.stderr("Error: Adb refused a command");
      return false;
    }
    catch (IOException e) {
      LOG.info(e);
      String message = e.getMessage();
      myPrinter.stderr("I/O Error" + (message != null ? ": " + message : ""));
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
      myPrinter.stderr(AndroidBundle.message("ddms.corrupted.error"));
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

    if (myStopped.get()) return false;
    String remotePath = "/data/local/tmp/" + packageName;
    String exceptionMessage;
    String errorMessage;
    myPrinter.stdout("Uploading file\n\tlocal path: " + localFile + "\n\tremote path: " + remotePath);
    try {
      InstalledApks installedApks = ServiceManager.getService(InstalledApks.class);
      if (myConfiguration.SKIP_NOOP_APK_INSTALLATIONS && installedApks.isInstalled(device, localFile, packageName)) {
        myPrinter.stdout("No apk changes detected.");
        if (myConfiguration.FORCE_STOP_RUNNING_APP) {
          myPrinter.stdout("Skipping file upload, force stopping package instead.");
          forceStopPackageSilently(device, packageName, true);
        }
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
      myPrinter.stderr(errorMessage);
    }
    else {
      myPrinter.stderr(errorMessage + '\n' + exceptionMessage);
    }
    return false;
  }

  /** Attempts to force stop package running on given device. */
  private void forceStopPackageSilently(@NotNull IDevice device, @NotNull String packageName, boolean ignoreErrors) {
    try {
      executeDeviceCommandAndWriteToConsole(device, "am force-stop " + packageName, new ErrorMatchingReceiver(myStopped));
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
    myPrinter.stdout("DEVICE SHELL COMMAND: " + command);
    AndroidUtils.executeCommandOnDevice(device, command, receiver, false);
  }

  private boolean installApp(@NotNull IDevice device, @NotNull String remotePath, @NotNull String packageName)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    myPrinter.stdout("Installing " + packageName);

    InstallResult result = null;
    boolean retry = true;
    while (!myStopped.get() && retry) {
      result = installApp(device, remotePath);
      if (result.installOutput != null) {
        if (result.failureCode == InstallResult.FailureCode.NO_ERROR) {
          myPrinter.stdout(result.installOutput);
        } else {
          myPrinter.stderr(result.installOutput);
        }
      }

      switch (result.failureCode) {
        case DEVICE_NOT_RESPONDING:
          myPrinter.stdout("Device is not ready. Waiting for " + WAITING_TIME_SECS + " sec.");
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
          myPrinter.stderr(AndroidBundle.message("deployment.failed.no.certificates.explanation"));
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

    return result != null && result.failureCode == InstallResult.FailureCode.NO_ERROR;
  }

  private boolean uninstallPackage(@NotNull IDevice device, @NotNull String packageName) {
    myPrinter.stdout("DEVICE SHELL COMMAND: pm uninstall " + packageName);
    String output;
    try {
      output = device.uninstallPackage(packageName);
    }
    catch (InstallException e) {
      return false;
    }

    if (output != null) {
      myPrinter.stderr(output);
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

  private InstallResult installApp(@NotNull IDevice device, @NotNull String remotePath)
    throws AdbCommandRejectedException, TimeoutException, IOException {

    ErrorMatchingReceiver receiver = new ErrorMatchingReceiver(myStopped);
    try {
      // Wipe any previous cached app data, if any
      FastDeployManager.wipeData(this, device, remotePath, receiver);

      executeDeviceCommandAndWriteToConsole(device, "pm install -r \"" + remotePath + "\"", receiver);
    }
    catch (ShellCommandUnresponsiveException e) {
      LOG.info(e);
      return new InstallResult(InstallResult.FailureCode.DEVICE_NOT_RESPONDING, null, null);
    }

    return InstallResult.forLaunchOutput(receiver);
  }

  private String validateSdkVersion(@NotNull IDevice device) {
    AndroidVersion deviceVersion = DevicePropertyUtil.getDeviceVersion(device);
    AndroidVersion minSdkVersion = myFacet.getAndroidModuleInfo().getRuntimeMinSdkVersion();
    if (!deviceVersion.canRun(minSdkVersion)) {
      myPrinter.stderr("Device API level: " + deviceVersion.toString()); // Log the device version to console for easy reference.
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

  public void addListener(@NotNull AndroidRunningStateListener listener) {
    myListeners.add(listener);
  }
}
