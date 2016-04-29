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
package com.android.tools.idea.welcome.install;

import com.android.annotations.NonNull;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Integrates SDK manager progress reporting with the wizard UI.
 *
 * TODO: hopefully the welcome wizard rewrite will allow this to be removed in favor of
 * {@link InstallSelectedPackagesStep}.
 */
public class SdkManagerProgressIndicatorIntegration extends ProgressIndicatorAdapter {
  private final ProgressIndicator myIndicator;
  private final InstallContext myContext;
  private StringBuffer myErrors = new StringBuffer();

  public SdkManagerProgressIndicatorIntegration(@NotNull ProgressIndicator indicator,
                                                @NotNull InstallContext context) {
    myIndicator = indicator;
    myContext = context;
  }

  @Override
  public void setFraction(double progress) {
    myIndicator.setFraction(progress);
  }

  @Override
  public void setText(@Nullable String title) {
    myIndicator.setText(title);
  }

  @Override
  public void setSecondaryText(@Nullable String s) {
    myIndicator.setText2(s);
  }

  @Override
  public void logInfo(@NonNull String s) {
    myContext.print(s, ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  @Override
  public void logError(@NonNull String s, @Nullable Throwable e) {
    if (e != null) {
      myErrors.append(String.format("%s: %s\n", e.getClass().getName(), e.getMessage()));
    }
    myErrors.append(s);
  }

  @Override
  public void logWarning(@NonNull String s, @Nullable Throwable e) {
    if (e != null) {
      myErrors.append(String.format("%s: %s\n", e.getClass().getName(), e.getMessage()));
    }
    myErrors.append(String.format("Warning: %s\n", s));
  }

  public String getErrors() {
    return myErrors.toString();
  }
}
