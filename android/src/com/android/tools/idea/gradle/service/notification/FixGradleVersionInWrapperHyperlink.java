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
package com.android.tools.idea.gradle.service.notification;

import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.externalSystem.service.notification.EditableNotificationMessageElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;

/**
 * Fixes the Gradle version in a project's Gradle wrapper.
 */
class FixGradleVersionInWrapperHyperlink extends NotificationHyperlink {
  @NotNull private final File myWrapperPropertiesFile;
  @NotNull private final String myGradleVersion;

  @Nullable
  static NotificationHyperlink createIfProjectUsesGradleWrapper(@NotNull Project project, @NotNull String supportedGradleVersion) {
    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);
    if (wrapperPropertiesFile != null) {
      return new FixGradleVersionInWrapperHyperlink(wrapperPropertiesFile, supportedGradleVersion);
    }
    return null;
  }

  private FixGradleVersionInWrapperHyperlink(@NotNull File wrapperPropertiesFile, @NotNull String gradleVersion) {
    super("fixGradleVersionInWrapper", "Fix Gradle wrapper and re-import project");
    myWrapperPropertiesFile = wrapperPropertiesFile;
    myGradleVersion = gradleVersion;
  }

  @Override
  protected void execute(@NotNull Project project) {
    updateGradleVersion(project, myWrapperPropertiesFile, myGradleVersion);
  }

  @Override
  public boolean executeIfClicked(@NotNull Project project, @NotNull HyperlinkEvent event) {
    // we need HyperlinkEvent for the link deactivation after the fix apply
    final boolean updated = updateGradleVersion(project, myWrapperPropertiesFile, myGradleVersion);
    if(updated){
      EditableNotificationMessageElement.disableLink(event);
    }
    return updated;
  }

  static boolean updateGradleVersion(@NotNull Project project, @NotNull File wrapperPropertiesFile, @NotNull String gradleVersion) {
    try {
      boolean updated = GradleUtil.updateGradleDistributionUrl(gradleVersion, wrapperPropertiesFile);
      if (updated) {
        VirtualFile virtualFile = VfsUtil.findFileByIoFile(wrapperPropertiesFile, true);
        if (virtualFile != null) {
          virtualFile.refresh(false, false);
        }
        return true;
      }
    }
    catch (IOException e) {
      String msg = String.format("Unable to update Gradle wrapper to use Gradle %1$s\n", gradleVersion);
      msg += e.getMessage();
      Messages.showErrorDialog(project, msg, ERROR_MSG_TITLE);
    }
    return false;
  }
}
