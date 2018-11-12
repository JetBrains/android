/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.tools.deployer.UIService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public class IdeService implements UIService {

  public static final String TITLE = "Application Installation Failed";
  private final Project project;

  public IdeService(Project project) {
    this.project = project;
  }

  @Override
  public boolean prompt(@NotNull String message) {
    return UIUtil.invokeAndWaitIfNeeded(() -> {
      int result = Messages.showOkCancelDialog(
          project, message, TITLE, Messages.getQuestionIcon());
      return result == Messages.OK;
    });
  }

  @Override
  public void message(@NotNull String message) {
    ApplicationManager.getApplication().invokeLater(
      () -> Messages.showErrorDialog(project, message, TITLE));
  }
}
