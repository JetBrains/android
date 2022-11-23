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
package com.android.tools.idea.sdk.updater;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ProgressIndicator;
import com.android.tools.idea.sdk.install.patch.PatchInstallerUtil;
import com.android.tools.idea.sdk.install.patch.PatchRunner;
import com.intellij.updater.NativeFileManager;
import com.intellij.updater.OperationCancelledException;
import com.intellij.updater.SwingUpdaterUI;
import com.intellij.updater.ValidationResult;
import com.sun.jna.platform.win32.Kernel32;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Bridge between the Studio/IJ updater and Studio itself.
 * This and the updater code are packaged as an SDK component, not as part of studio itself.
 * As such it will be invoked via reflection rather than directly.
 */
@SuppressWarnings("unused")
public class RepoUpdaterUI extends SwingUpdaterUI {
  ProgressIndicator myProgress;
  Component myParentComponent;

  public RepoUpdaterUI(@Nullable Component parentComponent, @NonNull ProgressIndicator progress) {
    super();
    myProgress = progress;
    myParentComponent = parentComponent;
  }

  @Override
  public void startProcess(String title) {
    // nothing
  }

  @Override
  public void setProgress(int percentage) {
    myProgress.setFraction(percentage/100.);
    myProgress.setIndeterminate(false);
  }

  @Override
  public void setProgressIndeterminate() {
    myProgress.setIndeterminate(true);
  }

  @Override
  public void checkCancelled() throws OperationCancelledException {
    super.checkCancelled();
    if (myProgress.isCanceled()) {
      throw new OperationCancelledException();
    }
  }

  @Override
  public void setDescription(@Nullable String oldBuildDesc, @Nullable String newBuildDesc) {
    myProgress.setText("Patching " + oldBuildDesc + " to " + newBuildDesc);
  }

  @Override
  public void setDescription(String text) {
    myProgress.setText(text);
  }

  @Override
  protected Component getParentComponent() {
    return myParentComponent;
  }

  @Override
  protected void notifyCancelled() {
    // nothing
  }

  /**
   * If a validation result shows that a file is in use by studio, we need to restart and run the standalone installer.
   * Otherwise we just show the normal validation dialog.
   */
  @Override
  public Map<String, ValidationResult.Option> askUser(List<? extends ValidationResult> validationResults) throws OperationCancelledException {
    try {
      int pid = Kernel32.INSTANCE.GetCurrentProcessId();
      for (ValidationResult result : validationResults) {
        for (NativeFileManager.Process process : NativeFileManager.getProcessesUsing(result.toFile)) {
          if (process.pid == pid) {
            throw new PatchRunner.RestartRequiredException();
          }
        }
      }
    }
    catch (PatchRunner.RestartRequiredException e) {
      throw e;
    }
    catch (Throwable e) {
      // ignore other exceptions and errors and show the dialog.
    }
    return super.askUser(validationResults);
  }
}
