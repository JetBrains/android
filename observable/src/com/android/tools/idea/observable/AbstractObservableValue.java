/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.observable;

import com.google.common.collect.ImmutableList;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation for {@link ObservableValue}, providing the logic for adding/removing listeners.
 */
public abstract class AbstractObservableValue<T> implements ObservableValue<T> {
  private final List<InvalidationListener> myListeners = new ArrayList<>(0);
  private final List<WeakReference<InvalidationListener>> myWeakListeners = new ArrayList<>(0);
  private boolean myNotificationsEnabled = true;

  @Override
  public final void addListener(@NotNull InvalidationListener listener) {
    myListeners.add(listener);
  }

  @Override
  public final void removeListener(@NotNull InvalidationListener listener) {
    myListeners.remove(listener);
    Iterator<WeakReference<InvalidationListener>> it = myWeakListeners.iterator();
    while (it.hasNext()) {
      InvalidationListener l = it.next().get();
      if (l == null || l == listener) {
        it.remove();
      }
    }
  }

  @Override
  public final void addWeakListener(@NotNull InvalidationListener listener) {
    myWeakListeners.add(new WeakReference<>(listener));
  }

  /**
   * Call to let our listeners know that our value has changed, and they should consider themselves
   * invalidated.
   */
  protected final void notifyInvalidated() {
    if (!myNotificationsEnabled) {
      return;
    }

    ImmutableList<InvalidationListener> listenersSnapshot = ImmutableList.copyOf(myListeners);
    ImmutableList<WeakReference<InvalidationListener>> weakListenersSnapshot = ImmutableList.copyOf(myWeakListeners);

    for (InvalidationListener listener : listenersSnapshot) {
      listener.onInvalidated();
    }

    for (WeakReference<InvalidationListener> reference : weakListenersSnapshot) {
      InvalidationListener listener = reference.get();
      if (listener != null) {
        listener.onInvalidated();
      }
    }

    Iterator<WeakReference<InvalidationListener>> it = myWeakListeners.iterator();
    while (it.hasNext()) {
      InvalidationListener listener = it.next().get();
      if (listener == null) {
        it.remove();
      }
    }
  }

  /**
   * Call to enable / disable the firing of listeners. Child classes may use this (with caution!)
   * to prevent multiple listeners being fired for the same invalidation event.
   */
  protected final void setNotificationsEnabled(boolean enabled) {
    myNotificationsEnabled = enabled;
  }
}
