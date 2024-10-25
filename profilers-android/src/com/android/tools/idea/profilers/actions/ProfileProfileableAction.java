/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.profilers.actions;

import com.android.tools.idea.profilers.ProfileRunExecutorGroup;
import com.android.tools.idea.run.profiler.ProfilingMode;
import com.intellij.execution.actions.ExecutorAction;

public class ProfileProfileableAction extends ExecutorAction {
  public ProfileProfileableAction() {
    super(ProfileRunExecutorGroup.getInstance().getChildExecutor(ProfilingMode.PROFILEABLE));
  }
}