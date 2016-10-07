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
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.tasks.AndroidDeepLinkLaunchTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DeepLinkLaunch extends LaunchOption<DeepLinkLaunch.State> {
  public static final DeepLinkLaunch INSTANCE = new DeepLinkLaunch();

  public static final class State extends LaunchOptionState {
    public String DEEP_LINK = "";

    @Nullable
    @Override
    public LaunchTask getLaunchTask(@NotNull String applicationId,
                                    @NotNull AndroidFacet facet,
                                    @NotNull StartActivityFlagsProvider startActivityFlagsProvider,
                                    @NotNull ProfilerState profilerState) {
      return new AndroidDeepLinkLaunchTask(DEEP_LINK, startActivityFlagsProvider);
    }

    @NotNull
    @Override
    public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
      if  (DEEP_LINK == null || DEEP_LINK.isEmpty()) {
        return ImmutableList.of(ValidationError.warning("URL not specified"));
      } else {
        return ImmutableList.of();
      }
    }
  }

  @NotNull
  @Override
  public String getId() {
    return AndroidRunConfiguration.LAUNCH_DEEP_LINK;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "URL";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  @NotNull
  @Override
  public LaunchOptionConfigurable<State> createConfigurable(@NotNull Project project, @NotNull LaunchOptionConfigurableContext context) {
    return new DeepLinkConfigurable(project, context);
  }
}

