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
package com.android.tools.idea.fd;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

public enum UpdateMode {
  /**
   * No updates
   */
  NO_CHANGES(ApplicationPatch.UPDATE_MODE_NONE),
  /**
   * Patch changes directly, keep app running without any restarting
   */
  HOT_SWAP(ApplicationPatch.UPDATE_MODE_HOT_SWAP),
  /**
   * Patch changes, restart activity to reflect changes
   */
  WARM_SWAP(ApplicationPatch.UPDATE_MODE_WARM_SWAP),
  /**
   * Store change in app directory, restart app
   */
  COLD_SWAP(ApplicationPatch.UPDATE_MODE_COLD_SWAP);

  private final int myId;

  UpdateMode(@MagicConstant(intValues = {ApplicationPatch.UPDATE_MODE_COLD_SWAP, ApplicationPatch.UPDATE_MODE_WARM_SWAP,
    ApplicationPatch.UPDATE_MODE_HOT_SWAP, ApplicationPatch.UPDATE_MODE_NONE}) int id) {
    myId = id;
  }

  @MagicConstant(intValues = {ApplicationPatch.UPDATE_MODE_COLD_SWAP, ApplicationPatch.UPDATE_MODE_WARM_SWAP,
    ApplicationPatch.UPDATE_MODE_HOT_SWAP, ApplicationPatch.UPDATE_MODE_NONE})
  public int getId() {
    return myId;
  }

  @NotNull
  public UpdateMode combine(@NotNull UpdateMode with) {
    return values()[Math.max(ordinal(), with.ordinal())];
  }
}
