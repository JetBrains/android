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
import com.android.tools.idea.sdk.SdkMerger;
import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.collect.ImmutableSet;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
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
  private static final ScopedStateStore.Key<Boolean> KEY_INSTALL_SDK =
    ScopedStateStore.createKey("download.sdk", ScopedStateStore.Scope.PATH, Boolean.class);
  private static final ScopedStateStore.Key<String> KEY_SDK_INSTALL_LOCATION =
    ScopedStateStore.createKey("download.sdk.location", ScopedStateStore.Scope.PATH, String.class);

  private final DownloadableFileDescription myAndroidSdkDescription =
    DownloadableFileService.getInstance().createFileDescription(FirstRunWizardDefaults.getSdkDownloadUrl(), FirstRunWizardDefaults.ANDROID_SDK_ARCHIVE_FILE_NAME);
  private final ScopedStateStore.Key<Boolean> myKeyCustomInstall;

  public AndroidSdk(ScopedStateStore.Key<Boolean> keyCustomInstall) {
    super("Android SDK", 3 * 1024 * Storage.Unit.MiB.getNumberOfBytes(), KEY_INSTALL_SDK);
    myKeyCustomInstall = keyCustomInstall;
  }

  private static <T> boolean isEmptyOrNull(@Nullable T[] files) {
    return files == null || files.length == 0;
  }

  private static void copySdk(File source, File destination) throws IOException {
    try {
      File[] children = source.listFiles();
      assert children != null;
      for (File child : children) {
        FileUtil.copyDir(child, destination);
      }
    }
    catch (IOException e) {
      FileUtil.delete(destination);
      throw e;
    }
  }

  @NotNull
  @Override
  public Set<DownloadableFileDescription> getFilesToDownloadAndExpand() {
    return ImmutableSet.of(myAndroidSdkDescription);
  }

  @Override
  public void init(ScopedStateStore state) {
    state.put(KEY_INSTALL_SDK, true);
    state.put(KEY_SDK_INSTALL_LOCATION, FirstRunWizardDefaults.getDefaultSdkLocation());
  }

  @Override
  public DynamicWizardStep[] createSteps() {
    return new DynamicWizardStep[]{new SdkComponentsStep(InstallComponentsPath.COMPONENTS, myKeyCustomInstall, KEY_SDK_INSTALL_LOCATION)};
  }

  @Override
  public void perform(@NotNull InstallContext downloaded, @NotNull ScopedStateStore parameters) throws WizardException {
    ProgressStep progressStep = downloaded.getProgressStep();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setText("Installing Android SDK");
    indicator.setIndeterminate(true);
    String destinationPath = parameters.get(KEY_SDK_INSTALL_LOCATION);
    assert destinationPath != null;
    File destination = new File(destinationPath);
    File source = downloaded.getExpandedLocation(myAndroidSdkDescription);
    assert source != null && source.isDirectory();
    try {
      FileUtil.ensureExists(destination);
      if (!FileUtil.filesEqual(destination.getCanonicalFile(), source.getCanonicalFile())) {
        if (!destination.exists() || isEmptyOrNull(destination.listFiles())) {
          copySdk(source, destination);
        }
        else if (SdkMerger.hasMergeableContent(source, destination)) {
          SdkMerger.mergeSdks(source, destination, indicator);
        }
      }
      progressStep.print(String.format("Android SDK was installed to %s", destination), ConsoleViewContentType.SYSTEM_OUTPUT);
    }
    catch (IOException e) {
      throw new WizardException(WelcomeUIUtils.getMessageWithDetails("Unable to install Android SDK", e.getMessage()), e);
    }
  }
}
