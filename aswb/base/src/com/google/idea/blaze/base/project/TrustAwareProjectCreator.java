/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.project;

import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import java.util.Optional;
import javax.annotation.Nullable;

/** Creates a project if the user has trusted the project. */
public class TrustAwareProjectCreator implements ExtendableBazelProjectCreator {

  /**
   * Creates a project if the user has trusted the project.
   *
   * @param builder the project builder
   * @param name the name of the project
   * @param path the path to the project
   * @return the created project, null if the user did not trust the project
   */
  @Override
  public Optional<Project> createProject(ProjectBuilder builder, String name, String path) {
    if (!canCreateProject(null)) {
      return Optional.empty();
    }

    return Optional.of(builder.createProject(name, path));
  }

  /** Returns true if the user has trusted the project. */
  @Override
  public boolean canCreateProject(@Nullable BuildSystemName buildSystemName) {
    if (TrustedProjects.isTrustedCheckDisabled()) {
      return true;
    }
    var trustText = IdeBundle.message("untrusted.project.dialog.trust.button");
    var dontOpenText = IdeBundle.message("untrusted.project.open.dialog.cancel.button");

    var choice =
        new MessageDialogBuilder.Message(
                IdeBundle.message("untrusted.project.general.dialog.title"),
                IdeBundle.message(
                    "untrusted.project.open.dialog.text",
                    ApplicationInfo.getInstance().getFullApplicationName()))
            .buttons(
                IdeBundle.message("untrusted.project.dialog.trust.button"),
                IdeBundle.message("untrusted.project.open.dialog.cancel.button"))
            .defaultButton(trustText)
            .focusedButton(dontOpenText)
            .asWarning()
            .help(TrustedProjects.TRUSTED_PROJECTS_HELP_TOPIC)
            .show(null, null);

    return trustText.equals(choice);
  }
}
