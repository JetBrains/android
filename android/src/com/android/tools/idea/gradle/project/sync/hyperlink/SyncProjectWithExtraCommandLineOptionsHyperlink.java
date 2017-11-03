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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_REQUEST;

public class SyncProjectWithExtraCommandLineOptionsHyperlink extends NotificationHyperlink {
  public static final Key<String[]> EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY = Key.create("extra.gradle.command.line.options");

  @NotNull private final String[] myExtraOptions;

  @NotNull
  public static NotificationHyperlink syncProjectRefreshingDependencies() {
    return new SyncProjectWithExtraCommandLineOptionsHyperlink("Re-download dependencies and sync project (requires network)",
                                                               "--refresh-dependencies");
  }

  public SyncProjectWithExtraCommandLineOptionsHyperlink(@NotNull String text, @NotNull String... extraOptions) {
    super("syncProject", text);
    myExtraOptions = extraOptions;
  }

  @Override
  protected void execute(@NotNull Project project) {
    // This is the only way that we can pass extra command line options to the Gradle sync process.
    project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, myExtraOptions);
    GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_USER_REQUEST, null);
  }
}
