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
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.tasks.AppLaunchTask;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.activity.ActivityLocator;
import com.android.tools.idea.run.activity.SpecificActivityLocator;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.tasks.SpecificActivityLaunchTask;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpecificActivityLaunch extends LaunchOption<SpecificActivityLaunch.State> {
  public static final SpecificActivityLaunch INSTANCE = new SpecificActivityLaunch();

  public static class State extends LaunchOptionState {
    public String ACTIVITY_CLASS = "";
    public boolean SEARCH_ACTIVITY_IN_GLOBAL_SCOPE = false;
    public boolean SKIP_ACTIVITY_VALIDATION = false;

    @Nullable
    @Override
    public AppLaunchTask getLaunchTask(@NotNull String applicationId,
                                       @NotNull AndroidFacet facet,
                                       @NotNull StartActivityFlagsProvider startActivityFlagsProvider,
                                       @NotNull ProfilerState profilerState,
                                       @NotNull ApkProvider apkProvider) {
      return new SpecificActivityLaunchTask(applicationId, ACTIVITY_CLASS, startActivityFlagsProvider);
    }

    @NotNull
    @Override
    public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
      try {
        if (!SKIP_ACTIVITY_VALIDATION) {
          getActivityLocator(facet).validate();
        }
        return ImmutableList.of();
      }
      catch (ActivityLocator.ActivityLocatorException e) {
        // The launch will probably fail, but we allow the user to continue in case we are looking at stale data.
        return ImmutableList.of(ValidationError.warning(e.getMessage()));
      }
    }

    @VisibleForTesting
    @NotNull
    protected SpecificActivityLocator getActivityLocator(@NotNull AndroidFacet facet) {
      Project project = facet.getModule().getProject();
      GlobalSearchScope scope = SEARCH_ACTIVITY_IN_GLOBAL_SCOPE ? GlobalSearchScope.allScope(project)
                                                                : GlobalSearchScope.projectScope(project);
      return new SpecificActivityLocator(facet, ACTIVITY_CLASS, scope);
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
