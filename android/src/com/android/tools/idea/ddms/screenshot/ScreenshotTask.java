/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ddms.screenshot;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use com.android.tools.idea.ui.screenshot.ScreenshotTask
 */
@Deprecated
public class ScreenshotTask extends Task.Modal {
  private final @NotNull ScreenshotSupplier myScreenshotSupplier;

  private @Nullable ScreenshotImage myImage;
  private @Nullable String myError;

  public ScreenshotTask(@NotNull Project project, @NotNull ScreenshotSupplier screenshotSupplier) {
    super(project, AndroidBundle.message("android.ddms.actions.screenshot.title"), true);
    myScreenshotSupplier = screenshotSupplier;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);

    indicator.setText(AndroidBundle.message("android.ddms.screenshot.task.step.obtain"));
    try {
      myImage = myScreenshotSupplier.captureScreenshot();
    }
    catch (Exception e) {
      if (indicator.isCanceled()) {
        return;
      }
      String message = ExceptionUtil.getMessage(e);
      if (message == null) {
        AndroidBundle.message("android.ddms.screenshot.task.error1", e.getClass().getName());
      }
      else {
        myError = message;
      }
    }
  }

  public @Nullable ScreenshotImage getScreenshot() {
    return myImage;
  }

  public @Nullable String getError() {
    return myError;
  }
}
