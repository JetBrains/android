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
package com.android.tools.idea.fd.actions;

import com.android.tools.fd.client.UpdateMode;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

/**
 * Action which performs an instant run, with restarting the activity
 */
public class InstantRunWithRestart extends InstantRunWithoutRestart {
  public InstantRunWithRestart() {
    super("Perform Instant Run With Activity Restart", AndroidIcons.RunIcons.Replay);
  }

  @NotNull
  @Override
  protected UpdateMode getUpdateMode() {
    return UpdateMode.WARM_SWAP;
  }
}
