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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.*;
import com.android.repository.impl.installer.BasicInstaller;
import com.android.repository.impl.installer.PackageInstaller;
import com.android.repository.io.FileOp;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link PackageInstaller} for installing HAXM. Runs the {@link HaxmWizard} instead of the normal quickfix wizard.
 */
public class HaxmInstaller implements PackageInstaller {
  @Override
  public boolean uninstall(@NonNull LocalPackage p,
                           @NonNull ProgressIndicator progress,
                           @NonNull RepoManager manager,
                           @NonNull FileOp fop) {
    // We just uninstall the package; we don't actually remove the extension.
    return new BasicInstaller().uninstall(p, progress, manager, fop);
  }

  @Override
  public boolean install(@NonNull RemotePackage p,
                         @NonNull Downloader downloader,
                         @Nullable SettingsController settings,
                         @NonNull ProgressIndicator progress,
                         @NonNull RepoManager manager,
                         @NonNull FileOp fop) {
    final AtomicBoolean result = new AtomicBoolean(false);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        HaxmWizard wizard = new HaxmWizard();
        wizard.init();
        result.set(wizard.showAndGet());
      }
    }, ModalityState.any());
    return result.get();
  }
}
