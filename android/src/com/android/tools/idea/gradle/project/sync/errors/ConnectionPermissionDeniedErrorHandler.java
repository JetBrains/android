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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CONNECTION_DENIED;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

// See https://code.google.com/p/android/issues/detail?id=75520
public class ConnectionPermissionDeniedErrorHandler extends BaseSyncErrorHandler {
  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (rootCause instanceof SocketException && isNotEmpty(text) && text.contains("Permission denied: connect")) {
      updateUsageTracker(CONNECTION_DENIED);
      return "Connection to the Internet denied.";
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    NotificationHyperlink quickFix =
      new OpenUrlHyperlink("https://developer.android.com/studio/troubleshoot.html#project-sync", "More details (and potential fix)");
    hyperlinks.add(quickFix);
    return hyperlinks;
  }
}