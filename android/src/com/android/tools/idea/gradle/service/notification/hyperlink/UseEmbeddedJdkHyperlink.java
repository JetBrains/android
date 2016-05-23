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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.sdk.IdeSdks.shouldUseEmbeddedJdk;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;

public class UseEmbeddedJdkHyperlink extends NotificationHyperlink {
  @Nullable
  public static UseEmbeddedJdkHyperlink create() {
    if (isAndroidStudio()) {
      return new UseEmbeddedJdkHyperlink();
    }
    return null;
  }

  private UseEmbeddedJdkHyperlink() {
    super("useEmbeddedJdk", "Use embedded JDK (recommended)");
  }

  @Override
  protected void execute(@NotNull Project project) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      shouldUseEmbeddedJdk(true);
    });
    GradleProjectImporter.getInstance().requestProjectSync(project, null);
  }
}
