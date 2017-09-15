/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Immutable;
import org.jetbrains.annotations.NotNull;

@Immutable
public final class BuildSelection {
  @NotNull public final BuildCause why;
  public final boolean brokenForSecondaryUser;

  @VisibleForTesting
  public BuildSelection(@NotNull BuildCause why, boolean brokenForSecondaryUser) {
    this.why = why;
    this.brokenForSecondaryUser = brokenForSecondaryUser;
  }

  public BuildMode getBuildMode() {
    return why.getBuildMode();
  }
}
