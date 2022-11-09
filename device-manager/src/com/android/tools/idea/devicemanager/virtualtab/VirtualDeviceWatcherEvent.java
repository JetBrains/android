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

import com.android.tools.idea.devicemanager.Key;
import java.util.EventObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceWatcherEvent extends EventObject {
  private final @NotNull Key myKey;

  VirtualDeviceWatcherEvent(@NotNull VirtualDeviceWatcher source, @NotNull Key key) {
    super(source);
    myKey = key;
  }

  @NotNull Key getKey() {
    return myKey;
  }

  @Override
  public int hashCode() {
    return 31 * source.hashCode() + myKey.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDeviceWatcherEvent)) {
      return false;
    }

    VirtualDeviceWatcherEvent event = (VirtualDeviceWatcherEvent)object;
    return source.equals(event.source) && myKey.equals(event.myKey);
  }
}
