/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.cppimpl.debug;

import com.android.ddmlib.Client;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;

public class BlazeAutoAndroidDebugger extends BlazeAutoAndroidDebuggerBase {

  @Override
  public XDebugSession getExistingDebugSession(@NotNull Project project, @NotNull Client client) {
    if (isNativeProject(project)) {
      log.info("Project has native development enabled");
      return nativeDebugger.getExistingDebugSession(project, client);
    } else {
      return super.getExistingDebugSession(project, client);
    }
  }

  @Override
  protected boolean isNativeDeployment(Project project, Client clientData) {
    return isNativeProject(project);
  }
}
