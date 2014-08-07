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

package org.jetbrains.android.sdk;

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.repository.updater.SettingsController;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repository.remote.RemoteSdk;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.utils.NullLogger;
import com.google.common.collect.Lists;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.logcat.AdbErrors;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdkData");

  private final Map<IAndroidTarget, SoftReference<AndroidTargetData>> myTargetDatas =
    new HashMap<IAndroidTarget, SoftReference<AndroidTargetData>>();

  private static final Ddmlib myDdmlib = new Ddmlib();
  private final LocalSdk myLocalSdk;
  private final RemoteSdk myRemoteSdk;
  private final SettingsController mySettingsController;
  private final DeviceManager myDeviceManager;

  private final int myPlatformToolsRevision;
  private final int mySdkToolsRevision;

  private static final List<SoftReference<AndroidSdkData>> mInstances = Lists.newArrayList();

  /** Singleton access classes */

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull File sdkLocation) {
    return getSdkData(sdkLocation, false);
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull File sdkLocation, boolean forceReparse) {
    File canonicalLocation = new File(FileUtil.toCanonicalPath(sdkLocation.getPath()));

    if (!forceReparse) {
      Iterator<SoftReference<AndroidSdkData>> it = mInstances.iterator();
      while (it.hasNext()) {
        AndroidSdkData sdkData = it.next().get();
        // Lazily remove stale soft references
        if (sdkData == null) {
          it.remove();
          continue;
        }
        if (FileUtil.filesEqual(sdkData.getLocation(), canonicalLocation)) {
          return sdkData;
        }
      }
    }
    if (!DefaultSdks.isValidAndroidSdkPath(canonicalLocation)) {
      return null;
    }
    LocalSdk localSdk = new LocalSdk(canonicalLocation);
    AndroidSdkData sdkData = new AndroidSdkData((localSdk));
    mInstances.add(0, new SoftReference<AndroidSdkData>(sdkData));
    return mInstances.get(0).get();
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull String sdkPath) {
    File file = new File(FileUtil.toSystemDependentName(sdkPath));
    return getSdkData(file);
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull Sdk sdk) {
    String sdkHomePath = sdk.getHomePath();
    if (sdkHomePath != null) {
      return getSdkData(sdk.getHomePath());
    }
    return null;
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull Project project) {
    Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (sdk != null) {
      return getSdkData(sdk);
    }
    return null;
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull Module module) {
    return getSdkData(module.getProject());
  }

  private AndroidSdkData(@NotNull LocalSdk localSdk) {
    myLocalSdk = localSdk;
    mySettingsController = new SettingsController(new NullLogger() /* TODO */);
    myRemoteSdk = new RemoteSdk(mySettingsController);
    String path = localSdk.getPath();
    assert path != null;
    myPlatformToolsRevision = AndroidCommonUtils.parsePackageRevision(path, SdkConstants.FD_PLATFORM_TOOLS);
    mySdkToolsRevision = AndroidCommonUtils.parsePackageRevision(path, SdkConstants.FD_TOOLS);
    myDeviceManager = DeviceManager.createInstance(localSdk.getLocation(), new MessageBuildingSdkLog());
  }

  @NotNull
  public File getLocation() {
    File location = myLocalSdk.getLocation();

    // The LocalSdk should always have been initialized.
    assert location != null;

    return location;
  }

  @Deprecated
  @NotNull
  public String getPath() {
    return getLocation().getPath();
  }

  @Nullable
  public BuildToolInfo getLatestBuildTool() {
    return myLocalSdk.getLatestBuildTool();
  }

  @NotNull
  public IAndroidTarget[] getTargets() {
    return myLocalSdk.getTargets();
  }

  // be careful! target name is NOT unique

  @Nullable
  public IAndroidTarget findTargetByName(@NotNull String name) {
    for (IAndroidTarget target : getTargets()) {
      if (target.getName().equals(name)) {
        return target;
      }
    }
    return null;
  }

  @Nullable
  public IAndroidTarget findTargetByApiLevel(@NotNull String apiLevel) {
    IAndroidTarget candidate = null;
    for (IAndroidTarget target : getTargets()) {
      if (AndroidSdkUtils.targetHasId(target, apiLevel)) {
        if (target.isPlatform()) {
          return target;
        }
        else if (candidate == null) {
          candidate = target;
        }
      }
    }
    return candidate;
  }

  @Nullable
  public IAndroidTarget findTargetByHashString(@NotNull String hashString) {
    return myLocalSdk.getTargetFromHashString(hashString);
  }

  public int getPlatformToolsRevision() {
    return myPlatformToolsRevision;
  }

  public int getSdkToolsRevision() {
    return mySdkToolsRevision;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj.getClass() != getClass()) return false;
    AndroidSdkData sdkData = (AndroidSdkData)obj;
    return FileUtil.filesEqual(getLocation(), sdkData.getLocation());
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(getLocation());
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  private boolean initializeDdmlib(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    while (true) {
      final MyInitializeDdmlibTask task = new MyInitializeDdmlibTask(project, myDdmlib);

      AdbErrors.clear();

      myDdmlib.initialize(getAdbPath());

      boolean retryWas = false;
      while (!task.isFinished()) {
        ProgressManager.getInstance().run(task);
        boolean finished = task.isFinished();

        if (task.isCanceled()) {
          myDdmlib.setAdbCrashed(!finished);
          return false;
        }

        myDdmlib.setAdbCrashed(false);

        if (!finished) {
          final String adbErrorString = combine(AdbErrors.getErrors());
          final int result = Messages.showDialog(project, "ADB not responding. You can wait more, or kill \"" +
                                                          SdkConstants.FN_ADB +
                                                          "\" process manually and click 'Restart'" +
                                                          (adbErrorString.length() > 0 ? "\nErrors from ADB:\n" + adbErrorString : ""),
                                                 CommonBundle.getErrorTitle(), new String[]{"&Wait more", "&Restart", "&Cancel"}, 0,
                                                 Messages.getErrorIcon());
          if (result == 2) {
            // cancel
            myDdmlib.setAdbCrashed(true);
            return false;
          }
          else if (result == 1) {
            // restart
            myDdmlib.setAdbCrashed(true);
            retryWas = true;
          }
        }
      }

      // task finished, but if we had problems, ddmlib can be still initialized incorrectly, so we invoke initialize once again
      if (!retryWas) {
        break;
      }
    }

    return true;
  }

  @NotNull
  private static String combine(@NotNull String[] strs) {
    final StringBuilder builder = new StringBuilder();

    for (String str : strs) {
      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder.append(str);
    }
    return builder.toString();
  }

  private String getAdbPath() {
    String path = getLocation() + File.separator + SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + SdkConstants.FN_ADB;
    if (!new File(path).exists()) {
      path = getLocation() + File.separator + AndroidCommonUtils.toolPath(SdkConstants.FN_ADB);
    }
    try {
      return new File(path).getCanonicalPath();
    }
    catch (IOException e) {
      LOG.info(e);
      return path;
    }
  }

  public static void terminateDdmlib() {
    myDdmlib.terminate();
  }

  @Nullable
  public AndroidDebugBridge getDebugBridge(@NotNull Project project) {
    if (!initializeDdmlib(project)) {
      return null;
    }
    return AndroidDebugBridge.getBridge();
  }

  @NotNull
  public LocalSdk getLocalSdk() {
    return myLocalSdk;
  }

  @NotNull
  public RemoteSdk getRemoteSdk() {
    return myRemoteSdk;
  }

  @NotNull
  public SettingsController getSettingsController() {
    return mySettingsController;
  }

  @NotNull
  public DeviceManager getDeviceManager() {
    return myDeviceManager;
  }

  @NotNull
  public AndroidTargetData getTargetData(@NotNull IAndroidTarget target) {
    final SoftReference<AndroidTargetData> targetDataRef = myTargetDatas.get(target);
    AndroidTargetData targetData = targetDataRef != null ? targetDataRef.get() : null;
    if (targetData == null) {
      targetData = new AndroidTargetData(this, target);
      myTargetDatas.put(target, new SoftReference<AndroidTargetData>(targetData));
    }
    return targetData;
  }

  private static class MyInitializeDdmlibTask extends Task.Modal {
    private final Object myLock = new Object();
    private final Ddmlib myDdmlib;
    private volatile boolean myCanceled;

    public MyInitializeDdmlibTask(Project project, Ddmlib ddmlib) {
      super(project, "Waiting for ADB", true);
      myDdmlib = ddmlib;
    }

    public boolean isFinished() {
      synchronized (myLock) {
        return myDdmlib.isConnected();
      }
    }

    public boolean isCanceled() {
      synchronized (myLock) {
        return myCanceled;
      }
    }

    @Override
    public void onCancel() {
      synchronized (myLock) {
        myCanceled = true;
        myLock.notifyAll();
      }
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      synchronized (myLock) {
        final long startTime = System.currentTimeMillis();

        final long timeout = 10000;

        while (myDdmlib.isConnectionInProgress() && !myCanceled && !indicator.isCanceled()) {
          long wastedTime = System.currentTimeMillis() - startTime;
          if (wastedTime >= timeout) {
            break;
          }
          try {
            myLock.wait(Math.min(timeout - wastedTime, 500));
          }
          catch (InterruptedException e) {
            break;
          }
        }
      }
    }
  }
}
