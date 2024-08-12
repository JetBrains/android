/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.hotswap;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.run.BlazeBeforeRunCommandHelper;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import java.io.IOException;
import java.nio.file.Path;

/** Builds a hotswap-compatible execution command from a blaze command. */
public interface HotSwapCommandBuilder {
  ExtensionPointName<HotSwapCommandBuilder> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.HotSwapCommandBuilder");

  boolean handles(Project project);

  ImmutableList<String> build(BlazeCommand.Builder blazeCommand) throws IOException;

  static ImmutableList<String> getBashCommandsToRunScript(
      Project project, BlazeCommand.Builder command) throws IOException {
    for (HotSwapCommandBuilder builder : EP_NAME.getExtensions()) {
      if (builder.handles(project)) {
        return builder.build(command);
      }
    }

    // Default implementation.
    Path scriptPath = BlazeBeforeRunCommandHelper.createScriptPathFile();
    command.addBlazeFlags("--script_path=" + scriptPath);
    String blaze = ParametersListUtil.join(command.build().toList());
    return ImmutableList.of("/bin/bash", "-c", blaze + " && " + scriptPath);
  }
}
