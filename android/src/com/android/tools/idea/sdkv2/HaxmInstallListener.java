/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.sdkv2;

import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.installer.PackageInstaller;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link PackageInstaller} for installing HAXM. Runs the {@link HaxmWizard} instead of the normal quickfix wizard.
 */
public class HaxmInstallListener implements PackageInstaller.StatusChangeListener {
  @Override
  public void statusChanged(@NotNull PackageInstaller installer,
                            @NotNull RepoPackage p,
                            @NotNull ProgressIndicator progress) throws PackageInstaller.StatusChangeListenerException {
    if (installer.getInstallStatus() == PackageInstaller.InstallStatus.COMPLETE) {
      final AtomicBoolean result = new AtomicBoolean(false);
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          HaxmWizard wizard = new HaxmWizard();
          wizard.init();
          result.set(wizard.showAndGet());
        }
      }, ModalityState.any());
      if (!result.get()) {
        throw new PackageInstaller.StatusChangeListenerException("HAXM setup failed!");
      }
    }
  }
}
