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
package com.android.tools.idea.ui.properties;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Convenience class for managing property listeners.
 * <p/>
 * Although you can always use {@link Observable#addListener(InvalidationListener)} directly,
 * occasionally this requires creating a local field to store a listener so you can remove it
 * later. This can be fine for one or two listeners, but for more complex cases, use this class
 * to manage listeners for you (and remove them all easily using {@link #releaseAll()}
 * <p/>
 * Note: This class is currently not thread-safe. You are expected to add and remove listeners on
 * the dispatch thread to avoid undefined behavior.
 */
public final class ListenerManager {
  private final List<ListenerPairing<?>> myListeners = Lists.newArrayList();
  private final Map<Consumer<?>, InvalidationListener> myConsumerMapping = Maps.newHashMap();

  /**
   * Registers the target listener with the specified observable.
   */
  public <T> void listen(@NotNull ObservableValue<T> src, @NotNull InvalidationListener listener) {
    myListeners.add(new ListenerPairing<T>(src, listener));
  }

  /**
   * Like {@link #listen(ObservableValue, InvalidationListener)} but with a typed listener.
   */
  public <T> void listen(@NotNull final ObservableValue<T> src, @NotNull final Consumer<T> listener) {
    InvalidationListener listenerWrapper = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        listener.consume(src.get());
      }
    };
    myConsumerMapping.put(listener, listenerWrapper);

    listen(src, listenerWrapper);
  }

  /**
   * A convenience method which both registers the target listener and then fires it with the
   * observable's latest value.
   */
  public <T> void listenAndFire(@NotNull ObservableValue<T> src, @NotNull InvalidationListener listener) {
    listen(src, listener);
    listener.onInvalidated(src);
  }

  /**
   * A convenience method which both registers the target listener and then fires it with the
   * observable's latest value.
   */
  public <T> void listenAndFire(@NotNull final ObservableValue<T> src, @NotNull final Consumer<T> listener) {
    listen(src, listener);
    listener.consume(src.get());
  }

  /**
   * Releases a listener previously registered via
   * {@link #listen(ObservableValue, InvalidationListener)}. If the listener was registered with
   * multiple observables, they will all be released.
   */
  public void release(@NotNull InvalidationListener listener) {
    Iterator<ListenerPairing<?>> i = myListeners.iterator();
    while (i.hasNext()) {
      ListenerPairing<?> listenerPairing = i.next();

      if (listenerPairing.myListener == listener) {
        listenerPairing.dispose();
        i.remove();
      }
    }
  }

  /**
   * Releases a listener previously registered via
   * {@link #listen(ObservableValue, Consumer)}. If the listener was registered with
   * multiple observables, they will all be released.
   */
  public void release(@NotNull Consumer<?> listener) {
    InvalidationListener listenerWrapper = myConsumerMapping.get(listener);
    if (listenerWrapper == null) {
      return;
    }
    release(listenerWrapper);
  }

  /**
   * Release all listeners registered with this manager.
   */
  public void releaseAll() {
    for (ListenerPairing<?> listener : myListeners) {
      listener.dispose();
    }
    myListeners.clear();
  }

  private static class ListenerPairing<T> {
    private final ObservableValue<T> myObservable;
    private final InvalidationListener myListener;

    public ListenerPairing(ObservableValue<T> src, InvalidationListener listener) {
      myObservable = src;
      myListener = listener;

      src.addListener(listener);
    }

    public void dispose() {
      myObservable.removeListener(myListener);
    }
  }
}
