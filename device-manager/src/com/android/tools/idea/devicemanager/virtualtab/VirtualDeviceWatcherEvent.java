/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.sdklib.internal.avd.AvdInfo;
import java.util.EventObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceWatcherEvent extends EventObject {
  private final @NotNull Iterable<@NotNull AvdInfo> myAvds;

  VirtualDeviceWatcherEvent(@NotNull VirtualDeviceWatcher source, @NotNull Iterable<@NotNull AvdInfo> avds) {
    super(source);
    myAvds = avds;
  }

  @NotNull Iterable<@NotNull AvdInfo> getAvds() {
    return myAvds;
  }

  @Override
  public int hashCode() {
    return 31 * source.hashCode() + myAvds.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDeviceWatcherEvent event)) {
      return false;
    }

    return source.equals(event.source) && myAvds.equals(event.myAvds);
  }
}
