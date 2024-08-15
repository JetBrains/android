/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.idea.blaze.android.run.binary.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.activity.ActivityLocator;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Provides the launch task for activity */
public class DefaultActivityLaunchTask extends ActivityLaunchTask {
  private static final String ID = "DEFAULT_ACTIVITY";

  @NotNull private final ActivityLocator myActivityLocator;

  public DefaultActivityLaunchTask(
      @NotNull String applicationId,
      @NotNull ActivityLocator activityLocator,
      @NotNull StartActivityFlagsProvider startActivityFlagsProvider) {
    super(applicationId, startActivityFlagsProvider);
    myActivityLocator = activityLocator;
  }

  @Nullable
  @Override
  protected String getQualifiedActivityName(@NotNull IDevice device) {
    try {
      return myActivityLocator.getQualifiedActivityName(device);
    } catch (ActivityLocator.ActivityLocatorException e) {
      return null;
    }
  }
}
