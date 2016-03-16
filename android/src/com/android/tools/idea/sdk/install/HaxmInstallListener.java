/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.install;

import com.android.annotations.NonNull;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.tools.idea.sdk.wizard.HaxmWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link PackageInstaller} for installing HAXM. Runs the {@link HaxmWizard} instead of the normal quickfix wizard.
 */
public class HaxmInstallListener implements PackageOperation.StatusChangeListener {
  @Override
  public void statusChanged(@NonNull PackageOperation op, @NonNull ProgressIndicator progress)
    throws PackageOperation.StatusChangeListenerException {
    if (op.getInstallStatus() == PackageOperation.InstallStatus.COMPLETE) {
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
        throw new PackageOperation.StatusChangeListenerException("HAXM setup failed!");
      }
    }
  }
}
