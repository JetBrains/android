/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.tasks.LaunchTask;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

// Each Launch Option should extend this class and add a set of public fields such that they can be saved/restored using
// DefaultJDOMExternalizer
public abstract class LaunchOptionState {
  @Nullable
  public abstract LaunchTask getLaunchTask(@NotNull String applicationId,
                                           @NotNull AndroidFacet facet,
                                           @NotNull StartActivityFlagsProvider startActivityFlagsProvider,
                                           @NotNull ProfilerState profilerState);

  @NotNull
  public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
    return Collections.emptyList();
  }
}
