/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command;

import static com.google.common.base.StandardSystemProperty.USER_HOME;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput.Prefix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import org.jetbrains.annotations.VisibleForTesting;

/** Utilities to support migrating user .blazerc from home directory to workspace root */
public class BlazercMigrator {
  private static final Logger logger = Logger.getInstance(BlazercMigrator.class);
  private final VirtualFile homeBlazerc;
  private final VirtualFile workspaceBlazercDir;

  @VisibleForTesting
  public BlazercMigrator(VirtualFile homeBlazerc, VirtualFile workspaceBlazercDir) {
    this.homeBlazerc = homeBlazerc;
    this.workspaceBlazercDir = workspaceBlazercDir;
  }

  public void promptAndMigrate(BlazeContext context) {
    if (!promptMigration()) {
      return;
    }
    try {
      VfsUtil.copy(this, homeBlazerc, workspaceBlazercDir);
      context.output(
          SummaryOutput.output(Prefix.INFO, "Copied .blazerc from home to workspace root")
              .log()
              .dedupe());
    } catch (IOException e) {
      context.output(
          SummaryOutput.output(
                  Prefix.INFO,
                  "Error copying .blazerc from home to workspace root; syncing without one.")
              .log()
              .dedupe());
      logger.error(e.getMessage());
    }
  }

  private boolean promptMigration() {
    int response = showYesNoDialog();
    logger.info("User opted for .blazerc migration: " + (response == Messages.YES ? "YES" : "NO"));
    return response == Messages.YES;
  }

  @VisibleForTesting
  protected int showYesNoDialog() {
    return Messages.showYesNoDialog(
        String.format(
            "You have a .blazerc file present in your home folder. The Blaze invocations from"
                + " the IDE won't be able to read the settings from this file. If you want this"
                + " blazerc to be used, then we recommend copying it into your workspace. Do"
                + " you want the IDE to copy the blazerc from \n"
                + "%s\n"
                + " to \n"
                + "%s?",
            USER_HOME.value(), workspaceBlazercDir.getPath()),
        "Blaze Configuration",
        null);
  }
}
