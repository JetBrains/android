/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.monitor;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * A deprecated mode for this tool window including all the monitors.
 * Once the new profilers are no longer behind a flag this condition,
 * and its behavior, can be removed.
 */
public class AndroidMonitorCondition implements Condition<Project> {
  @Override
  public boolean value(Project project) {
    return AndroidToolWindowFactory.showMonitors();
  }
}
