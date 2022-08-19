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

import java.util.EventObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ProcessManagerEvent extends EventObject {
  ProcessManagerEvent(@NotNull ProcessManager source) {
    super(source);
  }

  @Override
  public int hashCode() {
    return source.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof ProcessManagerEvent && source.equals(((ProcessManagerEvent)object).source);
  }
}
