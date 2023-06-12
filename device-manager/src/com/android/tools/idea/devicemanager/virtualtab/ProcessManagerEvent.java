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
import com.android.tools.idea.devicemanager.virtualtab.ProcessManager.State;
import java.util.EventObject;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ProcessManagerEvent extends EventObject {
  private final @Nullable Object myKey;
  private final @Nullable Object myState;

  ProcessManagerEvent(@NotNull ProcessManager source) {
    this(source, null, null);
  }

  ProcessManagerEvent(@NotNull ProcessManager source, @Nullable Key key, @Nullable State state) {
    super(source);

    myKey = key;
    myState = state;
  }

  @Override
  public int hashCode() {
    int hashCode = source.hashCode();

    hashCode = 31 * hashCode + Objects.hashCode(myKey);
    hashCode = 31 * hashCode + Objects.hashCode(myState);

    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof ProcessManagerEvent)) {
      return false;
    }

    ProcessManagerEvent event = (ProcessManagerEvent)object;
    return source.equals(event.source) && Objects.equals(myKey, event.myKey) && Objects.equals(myState, event.myState);
  }
}
