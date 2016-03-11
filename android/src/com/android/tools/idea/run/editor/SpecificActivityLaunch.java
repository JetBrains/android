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

import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.activity.ActivityLocator;
import com.android.tools.idea.run.activity.SpecificActivityLocator;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.SpecificActivityLaunchTask;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpecificActivityLaunch extends LaunchOption<SpecificActivityLaunch.State> {
  public static final SpecificActivityLaunch INSTANCE = new SpecificActivityLaunch();

  public static final class State extends LaunchOptionState {
    public String ACTIVITY_CLASS = "";

    @Nullable
    @Override
    public LaunchTask getLaunchTask(@NotNull String applicationId,
                                    @NotNull AndroidFacet facet,
                                    boolean waitForDebugger,
                                    @Nullable AndroidDebugger androidDebugger,
                                    @NotNull String extraAmOptions,
                                    @NotNull ProfilerState profilerState) {
      return new SpecificActivityLaunchTask(applicationId, ACTIVITY_CLASS, waitForDebugger, androidDebugger, extraAmOptions);
    }

    @NotNull
    @Override
    public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
      try {
        getActivityLocator(facet).validate();
        return ImmutableList.of();
      }
      catch (ActivityLocator.ActivityLocatorException e) {
        // The launch will probably fail, but we allow the user to continue in case we are looking at stale data.
        return ImmutableList.of(ValidationError.warning(e.getMessage()));
      }
    }

    @NotNull
    private SpecificActivityLocator getActivityLocator(@NotNull AndroidFacet facet) {
      return new SpecificActivityLocator(facet, ACTIVITY_CLASS);
    }
  }

  @NotNull
  @Override
  public String getId() {
    return AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Specified Activity";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  @NotNull
  @Override
  public LaunchOptionConfigurable<State> createConfigurable(@NotNull Project project, @NotNull LaunchOptionConfigurableContext context) {
    return new SpecificActivityConfigurable(project, context);
  }
}
