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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.google.common.base.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A convenience class for device predicates based on a {@link TargetChooser}.
 */
public class TargetDeviceFilter implements Predicate<IDevice> {

  @NotNull private final TargetChooser myTargetChooser;

  public TargetDeviceFilter(@NotNull TargetChooser targetChooser) {
    myTargetChooser = targetChooser;
  }

  @Override
  public boolean apply(@Nullable IDevice input) {
    return input != null && myTargetChooser.matchesDevice(input);
  }
}
