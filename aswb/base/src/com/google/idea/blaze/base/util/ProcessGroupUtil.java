/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.util;

import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;

/** Provides a mechanism to launch a commandline in new process group. */
public class ProcessGroupUtil {
  private static final String SETSID_PATH = "/usr/bin/setsid";
  private static final BoolExperiment newProcessGroupForBlazeCommands =
      new BoolExperiment("blaze.command.process.group", true);

  private ProcessGroupUtil() {}

  private static boolean useProcessGroup() {
    return SystemInfo.isLinux
        && newProcessGroupForBlazeCommands.getValue()
        && FileUtil.exists(SETSID_PATH);
  }

  public static GeneralCommandLine newProcessGroupFor(GeneralCommandLine commandLine) {
    if (!useProcessGroup()) {
      return commandLine;
    }
    String executable = commandLine.getExePath();
    commandLine.getParametersList().prependAll("--wait", executable);
    commandLine.setExePath(SETSID_PATH);
    return commandLine;
  }
}
