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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.android.tools.idea.sdk.IdeSdks.getJdkFromJavaHome;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_JDK_CHANGED_TO_CURRENT;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UseJavaHomeAsJdkHyperlink extends NotificationHyperlink {
  @NotNull private final String myJavaHome;

  @Nullable
  public static UseJavaHomeAsJdkHyperlink create() {
    String javaHome = getJdkFromJavaHome();
    if (!isEmpty(javaHome)) {
      return new UseJavaHomeAsJdkHyperlink(javaHome);
    }
    return null;
  }

  private UseJavaHomeAsJdkHyperlink(@NotNull String javaHome) {
    super("useJavaHomeAsJdk", "Set Android Studio to use the same JDK as Gradle and sync project");
    myJavaHome = javaHome;
  }

  @Override
  protected void execute(@NotNull Project project) {
    ApplicationManager.getApplication().runWriteAction(() -> {IdeSdks.getInstance().setJdkPath(Paths.get(myJavaHome));});
    GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_JDK_CHANGED_TO_CURRENT);
  }
}
