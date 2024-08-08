/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync.projectview;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.xml.util.XmlStringUtil;

/**
 * If the user asks to sync directories, pop up a warning notifying them that source files may not
 * resolve.
 */
public class SyncDirectoriesWarning {

  private static final String PROPERTY_KEY = "suppress_syncing_directories_warning";

  private static boolean warningSuppressed() {
    return PropertiesComponent.getInstance().getBoolean(PROPERTY_KEY, false);
  }

  private static void suppressWarning() {
    PropertiesComponent.getInstance().setValue(PROPERTY_KEY, "true");
  }

  /** Warns the user that sources may not resolve. Returns false if sync should be aborted. */
  public static boolean warn(Project project) {
    if (warningSuppressed()) {
      return true;
    }
    String buildSystem = Blaze.buildSystemName(project);
    String message =
        String.format(
            "Syncing without a %s build will result in unresolved symbols "
                + "in your source files.<p>This can be useful for quickly adding directories to "
                + "your project, but if you're seeing sources marked as '(unsynced)', run a normal "
                + "%<s sync to fix it.",
            buildSystem);
    String title = String.format("Syncing without a %s build", buildSystem);
    DialogWrapper.DoNotAskOption dontAskAgain =
        new DialogWrapper.DoNotAskOption.Adapter() {
          @Override
          public void rememberChoice(boolean isSelected, int exitCode) {
            if (isSelected) {
              suppressWarning();
            }
          }

          @Override
          public String getDoNotShowMessage() {
            return "Don't warn again";
          }
        };
    int result =
        Messages.showOkCancelDialog(
            project,
            XmlStringUtil.wrapInHtml(message),
            title,
            "Run Sync",
            "Cancel",
            Messages.getWarningIcon(),
            dontAskAgain);
    return result == Messages.OK;
  }
}
