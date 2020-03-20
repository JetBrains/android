/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ShowErrorDialogCallback<V> implements FutureCallback<V> {
  @NotNull
  private final String myTitle;

  @NotNull
  private final String myMessage;

  @Nullable
  private final Project myProject;

  ShowErrorDialogCallback(@NotNull String title, @NotNull String message, @Nullable Project project) {
    myTitle = title;
    myMessage = message;
    myProject = project;
  }

  @Override
  public void onSuccess(@Nullable V result) {
  }

  @Override
  public void onFailure(@NotNull Throwable throwable) {
    Messages.showErrorDialog(myProject, MoreObjects.firstNonNull(throwable.getMessage(), myMessage), myTitle);
  }
}
