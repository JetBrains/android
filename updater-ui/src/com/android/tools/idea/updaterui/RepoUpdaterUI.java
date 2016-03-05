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
package com.android.tools.idea.updaterui;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ProgressIndicator;
import com.intellij.updater.OperationCancelledException;
import com.intellij.updater.UpdaterUI;
import com.intellij.updater.ValidationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridge between the Studio/IJ updater and Studio itself.
 * This and the updater code are packaged as an SDK component, not as part of studio itself.
 * As such it will be invoked via reflection rather than directly.
 */
@SuppressWarnings("unused")
public class RepoUpdaterUI implements UpdaterUI {
  ProgressIndicator myProgress;

  public RepoUpdaterUI(@NonNull ProgressIndicator progress) {
    myProgress = progress;
  }

  @Override
  public void startProcess(@Nullable String title) {
    // We don't have UI to set up, so nothing here.
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
  public void setStatus(@Nullable String status) {
    myProgress.setSecondaryText(status);
  }

  @Override
  public void showError(@Nullable Throwable e) {
    myProgress.logWarning("", e);
  }

  @Override
  public void checkCancelled() throws OperationCancelledException {
    if (myProgress.isCanceled()) {
      throw new OperationCancelledException();
    }
  }

  @Override
  public void setDescription(@Nullable String oldBuildDesc, @Nullable String newBuildDesc) {
    myProgress.setText("Patching " + oldBuildDesc + " to " + newBuildDesc);
  }

  @Override
  public boolean showWarning(@NonNull String message) {
    myProgress.logWarning(message);
    return false;
  }

  /**
   * We don't have a mechanism to get user feedback, so just die (with a special exception type depending on the type of error).
   */
  @Override
  @NonNull
  public Map<String, ValidationResult.Option> askUser(@NonNull List<ValidationResult> validationResults)
    throws OperationCancelledException {
    for (ValidationResult v : validationResults) {
      if (v.kind == ValidationResult.Kind.CONFLICT) {
        throw new InstallCompleteException();
      }
      if (v.kind == ValidationResult.Kind.ERROR) {
        if (v.options.contains(ValidationResult.Option.KILL_PROCESS)) {
          throw new NeedsRestartException();
        }
        throw new InstallCompleteException();
      }
    }
    return new HashMap<String, ValidationResult.Option>();
  }

  /**
   * Exception indicating that the installation was stopped because files are in use. Studio should exit and use the standalone patcher.
   */
  public static class NeedsRestartException extends OperationCancelledException {}

  /**
   * Exception indicating that the installation was stopped due to a failure to apply the patch. The complete version of the package
   * should be installed instead.
   */
  public static class InstallCompleteException extends OperationCancelledException {}
}
