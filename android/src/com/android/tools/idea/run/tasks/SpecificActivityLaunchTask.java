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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpecificActivityLaunchTask extends ActivityLaunchTask {
  @NotNull private final String myActivity;

  public SpecificActivityLaunchTask(@NotNull String applicationId,
                                    @NotNull String activity,
                                    @NotNull StartActivityFlagsProvider startActivityFlagsProvider) {
    super(applicationId, startActivityFlagsProvider);
    myActivity = activity;
  }

  @Nullable
  @Override
  protected String getQualifiedActivityName(@NotNull IDevice device, @NotNull ConsolePrinter printer) {
    return myActivity;
  }
}
