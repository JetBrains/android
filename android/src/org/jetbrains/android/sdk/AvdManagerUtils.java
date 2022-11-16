/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.sdk;

import com.android.prefs.AndroidLocationsException;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.sdk.IdeAvdManagers;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AvdManagerUtils {
  private AvdManagerUtils() {}

  public static @Nullable AvdManager getAvdManager(@NotNull AndroidFacet facet) {
    try {
      return IdeAvdManagers.INSTANCE.getAvdManager(AndroidSdkData.getSdkHolder(facet));
    }
    catch (AndroidLocationsException exception) {
      return null;
    }
  }
}
