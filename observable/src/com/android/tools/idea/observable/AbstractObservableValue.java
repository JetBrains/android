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

import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.bool.IsEqualToExpression;
import com.android.tools.idea.observable.expressions.Expression;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * Implementation for {@link ObservableValue}, providing the logic for adding/removing listeners.
 */
public abstract class AbstractObservableValue<T> implements ObservableValue<T> {
  private final List<InvalidationListener> myListeners = Lists.newArrayListWithCapacity(0);
  private final List<WeakReference<InvalidationListener>> myWeakListeners = Lists.newArrayListWithCapacity(0);
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

  @NotNull
  @Override
  public final <S> Expression<S> transform(@NotNull Function<T, S> function) {
    return new Expression<S>(this) {
      @NotNull
      @Override
      public S get() {
        return function.apply(AbstractObservableValue.this.get());
      }
    };
  }

  @NotNull
  @Override
  public final ObservableBool isEqualTo(@NotNull T value) {
    return new IsEqualToExpression<>(this, value);
  }

  /**
   * Call to let our listeners know that our value has changed, and they should consider themselves
   * invalidated.
   */
  protected final void notifyInvalidated() {
    if (!myNotificationsEnabled) {
      return;
    }

    for (InvalidationListener listener : myListeners) {
      listener.onInvalidated(this);
    }

    Iterator<WeakReference<InvalidationListener>> it = myWeakListeners.iterator();
    while (it.hasNext()) {
      InvalidationListener listener = it.next().get();
      if (listener != null) {
        listener.onInvalidated(this);
      }
      else {
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
