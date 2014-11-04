/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.sdklib.devices.Storage;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.sdk.SdkMerger;
import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.collect.ImmutableSet;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Android SDK installable component.
 */
public final class AndroidSdk extends InstallableComponent {
  public static final long SIZE = 2300 * Storage.Unit.MiB.getNumberOfBytes();
  private static final ScopedStateStore.Key<Boolean> KEY_INSTALL_SDK =
    ScopedStateStore.createKey("download.sdk", ScopedStateStore.Scope.PATH, Boolean.class);
  private static final ScopedStateStore.Key<String> KEY_SDK_INSTALL_LOCATION =
    ScopedStateStore.createKey("download.sdk.location", ScopedStateStore.Scope.PATH, String.class);
  private final DownloadableFileDescription myAndroidSdkDescription = DownloadableFileService.getInstance()
    .createFileDescription(FirstRunWizardDefaults.getSdkDownloadUrl(), FirstRunWizardDefaults.ANDROID_SDK_ARCHIVE_FILE_NAME);
  private final ScopedStateStore.Key<Boolean> myKeyCustomInstall;
  private ScopedStateStore myState;
  private SdkComponentsStep myStep;

  public AndroidSdk(ScopedStateStore.Key<Boolean> keyCustomInstall) {
    super("Android SDK", SIZE, KEY_INSTALL_SDK);
    myKeyCustomInstall = keyCustomInstall;
  }

  @NotNull
  @Override
  public Set<DownloadableFileDescription> getFilesToDownloadAndExpand() {
    if (getHandoffAndroidSdkSource() == null) {
      return ImmutableSet.of(myAndroidSdkDescription);
    }
    else {
      return ImmutableSet.of();
    }
  }

  @Override
  public void init(ScopedStateStore state) {
    myState = state;
    InstallerData data = InstallerData.get(state);
    String location;
    if (data.exists()) {
      location = data.getAndroidDest();
    }
    else {
      location = FirstRunWizardDefaults.getDefaultSdkLocation();
    }
    state.put(KEY_INSTALL_SDK, true);
    state.put(KEY_SDK_INSTALL_LOCATION, location);
  }

  @Override
  public DynamicWizardStep[] createSteps() {
    myStep = new SdkComponentsStep(InstallComponentsPath.COMPONENTS, myKeyCustomInstall, KEY_SDK_INSTALL_LOCATION);
    return new DynamicWizardStep[]{myStep};
  }

  @Override
  public boolean hasVisibleStep() {
    return myStep.isStepVisible();
  }

  @Override
  public void perform(@NotNull InstallContext downloaded) throws WizardException {
    String destinationPath = myState.get(KEY_SDK_INSTALL_LOCATION);
    assert destinationPath != null;
    final File destination = new File(destinationPath);
    File source = getHandoffAndroidSdkSource();
    if (FileUtil.filesEqual(source, destination)) {
      return;
    }
    ProgressStep progressStep = downloaded.getProgressStep();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setText("Installing Android SDK");
    indicator.setIndeterminate(true);
    if (source == null) {
      source = downloaded.getExpandedLocation(myAndroidSdkDescription);
      assert source != null && source.isDirectory();
    }
    try {
      FileUtil.ensureExists(destination);
      if (!FileUtil.filesEqual(destination.getCanonicalFile(), source.getCanonicalFile())) {
        if (SdkMerger.hasMergeableContent(source, destination)) {
          SdkMerger.mergeSdks(source, destination, indicator);
        }
      }
      progressStep.print(String.format("Android SDK was installed to %s", destination), ConsoleViewContentType.SYSTEM_OUTPUT);
      final Application application = ApplicationManager.getApplication();
      // SDK can only be set from write action, write action can only be started from UI thread
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          application.runWriteAction(new Runnable() {
            @Override
            public void run() {
              DefaultSdks.setDefaultAndroidHome(destination, null);
              AndroidFirstRunPersistentData.getInstance().markSdkUpToDate();
            }
          });
        }
      }, application.getAnyModalityState());
    }
    catch (IOException e) {
      throw new WizardException(WelcomeUIUtils.getMessageWithDetails("Unable to install Android SDK", e.getMessage()), e);
    }
  }

  @Override
  public boolean isOptional() {
    return false;
  }

  @Nullable
  private File getHandoffAndroidSdkSource() {
    InstallerData data = InstallerData.get(myState);
    String androidSrc = data.getAndroidSrc();
    if (!StringUtil.isEmpty(androidSrc)) {
      File srcFolder = new File(androidSrc);
      File[] files = srcFolder.listFiles();
      if (srcFolder.isDirectory() && files != null && files.length > 0) {
        return srcFolder;
      }
    }
    return null;
  }
}
