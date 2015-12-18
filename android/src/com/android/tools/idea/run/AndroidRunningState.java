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
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.ddms.adb.AdbService;
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
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
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
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AndroidRunningState implements RunProfileState, AndroidExecutionState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidRunningState");

  public static final int WAITING_TIME_SECS = 20;
  public static final String DEVICE_COMMAND_PREFIX = "DEVICE SHELL COMMAND: ";

  private final ApkProvider myApkProvider;

  @NotNull private final String myPackageName;
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final AndroidApplicationLauncher myApplicationLauncher;
  @NotNull private final ProcessHandlerConsolePrinter myPrinter;
  @NotNull private final AndroidRunConfigurationBase myConfiguration;
  @NotNull private final LaunchOptions myLaunchOptions;

  private final Object myDebugLock = new Object();

  @NotNull private final DeviceTarget myDeviceTarget;

  private volatile DebugLauncher myDebugLauncher;

  private final ExecutionEnvironment myEnv;

  private final AtomicBoolean myStopped = new AtomicBoolean(false);
  private volatile ProcessHandler myProcessHandler;
  private final Object myLock = new Object();

  private volatile boolean myApplicationDeployed = false;

  private ConsoleView myConsole;
  private final List<AndroidRunningStateListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public AndroidRunningState(@NotNull ExecutionEnvironment environment,
                             @NotNull AndroidFacet facet,
                             @NotNull ApkProvider apkProvider,
                             @NotNull DeviceTarget deviceTarget,
                             @NotNull ProcessHandlerConsolePrinter printer,
                             @NotNull AndroidApplicationLauncher applicationLauncher,
                             @NotNull LaunchOptions launchOptions,
                             @NotNull AndroidRunConfigurationBase configuration) throws ExecutionException {
    myFacet = facet;
    myApkProvider = apkProvider;
    myDeviceTarget = deviceTarget;
    myPrinter = printer;
    myConfiguration = configuration;

    myEnv = environment;
    myApplicationLauncher = applicationLauncher;
    myLaunchOptions = launchOptions;

    try {
      myPackageName = myApkProvider.getPackageName();
    } catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to determine package name", e);
    }
  }

  // Used by downstream plugins.
  @Nullable
  public DebugLauncher getDebugLauncher() {
    return myDebugLauncher;
  }

  public void setDebugLauncher(@NotNull DebugLauncher debugLauncher) {
    myDebugLauncher = debugLauncher;
  }

  public boolean isDebugMode() {
    return myLaunchOptions.isDebug();
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

  @NotNull
  public String getPackageName() {
    return myPackageName;
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
        if (myLaunchOptions.isDeploy() && !myApplicationDeployed) {
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

    private boolean isToLaunchDebug(@NotNull ClientData data) {
      if (myApplicationLauncher.isReadyForDebugging(data, getProcessHandler())) {
        // early exit without checking package name in case the debug package doesn't match
        // our target package name. This happens for instance when debugging a test that doesn't launch an application
        return true;
      }
      String description = data.getClientDescription();
      if (description == null) {
        return false;
      }
      return description.equals(getPackageName());
    }
  }

  private void launchDebug(@NotNull Client client) {
    myDebugLauncher.launchDebug(client);
    myDebugLauncher = null;
  }

  public void setConsole(@NotNull ConsoleView console) {
    myConsole = console;
  }

  // Note: execute isn't called if we are re-attaching to an existing session and there is no need to create
  // a new process handler and console. In such a scenario, control flow directly goes to #start().
  @Override
  public ExecutionResult execute(@NotNull final Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    myProcessHandler = new DefaultDebugProcessHandler();
    AndroidProcessText.attach(myProcessHandler);
    myConsole = myConfiguration.attachConsole(this, executor);
    myPrinter.setProcessHandler(myProcessHandler);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        start();
      }
    });

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

    return new DefaultExecutionResult(myConsole, myProcessHandler);
  }

  void start() {
    final AtomicInteger startedCount = new AtomicInteger();
    for (ListenableFuture<IDevice> targetDevice : myDeviceTarget.getDeviceFutures()) {
      Futures.addCallback(targetDevice, new FutureCallback<IDevice>() {
        @Override
        public void onSuccess(@Nullable IDevice device) {
          if (myStopped.get() || device == null) {
            return;
          }
          if (isDebugMode()) {
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
            if (startedCount.incrementAndGet() == myDeviceTarget.getDeviceFutures().size() && !isDebugMode()) {
              // All the devices have been started, and we don't need to wait to attach the debugger. We're done.
              myStopped.set(true);
              getProcessHandler().destroyProcess();
            }
            fireExecutionStarted(device);
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
          myPrinter.stderr(t.getMessage());
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

  private void fireExecutionStarted(@NotNull IDevice device) {
    for (AndroidRunningStateListener listener : myListeners) {
      listener.executionStarted(device);
    }
  }

  private boolean prepareAndStartApp(@NotNull final IDevice device) {
    if (isDebugMode() && !LaunchUtils.canDebugAppOnDevice(myFacet, device)) {
      myPrinter.stderr(AndroidBundle.message("android.cannot.debug.noDebugPermissions", getPackageName(), device.getName()));
      return false;
    }

    if (myLaunchOptions.isClearLogcatBeforeStart()) {
      clearLogcatAndConsole(getModule().getProject(), device);
    }

    myPrinter.stdout("Target device: " + device.getName());
    try {
      if (myLaunchOptions.isDeploy()) {
        if (!installApks(device)) {
          return false;
        }
        trackInstallation(device);
        myApplicationDeployed = true;
      }

      LaunchUtils.initiateDismissKeyguard(device);

      final AndroidApplicationLauncher.LaunchResult launchResult = myApplicationLauncher.launch(this, device);
      if (launchResult == AndroidApplicationLauncher.LaunchResult.STOP) {
        return false;
      }
      else if (launchResult == AndroidApplicationLauncher.LaunchResult.SUCCESS) {
        checkDdms();
      }

      final Client client;
      final String pkgName = getPackageName();
      synchronized (myDebugLock) {
        client = device.getClient(pkgName);
        if (myDebugLauncher != null) {
          if (client != null &&
              myApplicationLauncher.isReadyForDebugging(client.getClientData(), getProcessHandler())) {
            launchDebug(client);
          }
          else {
            myPrinter.stdout("Waiting for process: " + pkgName);
          }
        }
      }

      if (!isDebugMode() && myLaunchOptions.isOpenLogcatAutomatically()) {
        showLogcatConsole(device, client);
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
      myPrinter.stderr("Error: adb rejected command: " + e);
      return false;
    }
    catch (IOException e) {
      LOG.info(e);
      String message = e.getMessage();
      myPrinter.stderr("I/O Error" + (message != null ? ": " + message : ""));
      return false;
    }
  }

  private boolean installApks(@NotNull IDevice device) {
    Collection<ApkInfo> apks;
    try {
      apks = myApkProvider.getApks(device);
    } catch (ApkProvisionException e) {
      myPrinter.stderr(e.getMessage());
      LOG.warn(e);
      return false;
    }

    ApkInstaller installer = new ApkInstaller(myFacet, myLaunchOptions, ServiceManager.getService(InstalledApkCache.class), myPrinter);
    for (ApkInfo apk : apks) {
      if (!apk.getFile().exists()) {
        String message = "The APK file " + apk.getFile().getPath() + " does not exist on disk.";
        myPrinter.stderr(message);
        LOG.error(message);
        return false;
      }

      if (!installer.uploadAndInstallApk(device, apk.getApplicationId(), apk.getFile(), myStopped)) {
        return false;
      }
    }
    return true;
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

  private static void clearLogcatAndConsole(@NotNull final Project project, @NotNull final IDevice device) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
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
    });
  }

  private void showLogcatConsole(@NotNull final IDevice device, @Nullable final Client client) {
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

  private boolean checkDdms() {
    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (isDebugMode() && bridge != null && AdbService.canDdmsBeCorrupted(bridge)) {
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

  public void executeDeviceCommandAndWriteToConsole(@NotNull IDevice device,
                                                    @NotNull String command,
                                                    @NotNull AndroidOutputReceiver receiver)
    throws IOException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException {
    myPrinter.stdout("DEVICE SHELL COMMAND: " + command);
    AndroidUtils.executeCommandOnDevice(device, command, receiver, false);
  }

  public void addListener(@NotNull AndroidRunningStateListener listener) {
    myListeners.add(listener);
  }
}
