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

import com.android.tools.idea.sdk.SdkLoggerIntegration;
import com.google.common.base.Objects;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Integrates SDK manager progress reporting with the wizard UI.
 */
public class SdkManagerProgressIndicatorIntegration extends SdkLoggerIntegration {
  private final ProgressIndicator myIndicator;
  private final InstallContext myContext;
  private final int myComponentCount;
  private int myCompletedOperations = -1;
  private String previousTitle;
  private StringBuffer myErrors = new StringBuffer();

  public SdkManagerProgressIndicatorIntegration(@NotNull ProgressIndicator indicator,
                                                @NotNull InstallContext context,
                                                int componentCount) {
    assert componentCount > 0;
    myIndicator = indicator;
    myContext = context;
    myComponentCount = componentCount;
  }

  @Override
  protected void setProgress(int progress) {
    double completedOperations = progress / 100.0 + myCompletedOperations;
    double progressBar = completedOperations / (myComponentCount * 2); // Installing a component is 2 operations - download + unzip
    myIndicator.setFraction(progressBar);
  }

  @Override
  protected void setDescription(String description) {
    // Nothing
  }

  @Override
  protected void setTitle(String title) {
    if (!StringUtil.isEmptyOrSpaces(title) && !Objects.equal(title, previousTitle)) {
      previousTitle = title;
      myCompletedOperations++;
      myIndicator.setText(previousTitle);
      setProgress(0);
    }
  }

  @Override
  protected void lineAdded(String string) {
    myContext.print(string, ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  @Override
  public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
    if (t != null) {
      myErrors.append(String.format("%s: %s\n", t.getClass().getName(), t.getMessage()));
    }
    if (msgFormat != null) {
      myErrors.append(String.format(msgFormat, args));
    }
    super.error(t, msgFormat, args);
  }

  @Override
  public void warning(@NotNull String msgFormat, Object... args) {
    myErrors.append(String.format("Warning: %s\n", String.format(msgFormat, args)));
    super.warning(msgFormat, args);
  }

  public String getErrors() {
    return myErrors.toString();
  }
}
