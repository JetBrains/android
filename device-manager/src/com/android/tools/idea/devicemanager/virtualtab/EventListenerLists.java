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

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.swing.event.EventListenerList;
import org.jetbrains.annotations.NotNull;

final class EventListenerLists {
  private EventListenerLists() {
  }

  static <L, E> void fire(@NotNull EventListenerList listeners,
                          @NotNull BiConsumer<L, E> listenerMethod,
                          @NotNull Class<L> listenerClass,
                          @NotNull Supplier<E> newEvent) {
    Object[] array = listeners.getListenerList();
    E event = null;

    for (int i = array.length - 2; i >= 0; i -= 2) {
      if (array[i] != listenerClass) {
        continue;
      }

      if (event == null) {
        event = newEvent.get();
      }

      listenerMethod.accept(listenerClass.cast(array[i + 1]), event);
    }
  }
}
