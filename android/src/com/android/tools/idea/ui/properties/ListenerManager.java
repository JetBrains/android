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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Convenience class for managing property listeners.
 * <p/>
 * Although you can always use {@link ObservableValue#addListener(InvalidationListener)} directly,
 * occasionally this requires creating a local field to store a listener so you can remove it
 * later. This can be fine for one or two listeners, but for more complex cases, use this class
 * to manage listeners for you (and remove them all easily using {@link #releaseAll()}
 * <p/>
 * Note: This class is currently not thread-safe. You are expected to add and remove listeners on
 * the dispatch thread to avoid undefined behavior.
 */
public final class ListenerManager {

  /**
   * List of all listeners registered by one of the listen calls.
   */
  private final List<ListenerPairing> myListeners = Lists.newArrayList();

  /**
   * The listen methods take either an invalidation listener (untyped) or a consumer (typed).
   * When a user adds a consumer listener, those are wrapped in an invalidation listener, and the
   * relationship is recorded here so we can later remove by consumer as well.
   */
  private final Map<Consumer<?>, InvalidationListener> myConsumerMapping = Maps.newHashMap();

  /**
   * List of listeners registered by listenAll.
   */
  private final List<CompositeListener> myCompositeListeners = Lists.newArrayListWithExpectedSize(0);

  private final BatchInvoker myInvoker;

  public ListenerManager() {
    myInvoker = new BatchInvoker();
  }

  public ListenerManager(@NotNull BatchInvoker.Strategy invokeStrategy) {
    myInvoker = new BatchInvoker(invokeStrategy);
  }

  /**
   * Registers the target listener with the specified observable.
   */
  public void listen(@NotNull ObservableValue<?> src, @NotNull InvalidationListener listener) {
    myListeners.add(new ListenerPairing(src, listener));
  }

  /**
   * Like {@link #listen(ObservableValue, InvalidationListener)} but with a typed listener.
   */
  public <T> void listen(@NotNull final ObservableValue<T> src, @NotNull final Consumer<T> listener) {
    InvalidationListener listenerWrapper = sender -> listener.consume(src.get());
    myConsumerMapping.put(listener, listenerWrapper);

    listen(src, listenerWrapper);
  }

  /**
   * A convenience method which both registers the target listener and then fires it with the
   * observable's latest value.
   */
  public void listenAndFire(@NotNull ObservableValue<?> src, @NotNull InvalidationListener listener) {
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
   * Listen to a collection of observable values, firing an event whenever one or more of them
   * change on any given frame.
   *
   * This method starts a fluent chain, but to actually hook up a listener, you must also call
   * {@link CompositeListener#with(Runnable)} as well.
   *
   * For example: {@code listeners.listenAll(x, y, w, h).}<b>{@code with(repaint);}</b>
   */
  @NotNull
  public CompositeListener listenAll(@NotNull ObservableValue<?>... values) {
    CompositeListener listener = new CompositeListener(values);
    myCompositeListeners.add(listener);
    return listener;
  }

  /**
   * Convenience version of {@link #listenAll(ObservableValue[])} that works when you have a
   * {@link Collection} instead of an array.
   */
  @NotNull
  public CompositeListener listenAll(@NotNull Collection<? extends ObservableValue<?>> values) {
    //noinspection unchecked
    return listenAll(Iterables.toArray(values, ObservableValue.class));
  }

  /**
   * Releases a listener previously registered via
   * {@link #listen(ObservableValue, InvalidationListener)}. If the listener was registered with
   * multiple observables, they will all be released.
   */
  public void release(@NotNull InvalidationListener listener) {
    Iterator<ListenerPairing> i = myListeners.iterator();
    while (i.hasNext()) {
      ListenerPairing listenerPairing = i.next();

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
   * Releases all listeners previously registered to a target observable via
   * {@link #listen(ObservableValue, InvalidationListener)} or
   * {@link #listen(ObservableValue, Consumer)}.
   */
  public void release(@NotNull ObservableValue<?> observable) {
    Iterator<ListenerPairing> i = myListeners.iterator();
    while (i.hasNext()) {
      ListenerPairing listenerPairing = i.next();

      if (listenerPairing.myObservable == observable) {
        listenerPairing.dispose();
        i.remove();
      }
    }
  }

  /**
   * Releases a listener previously registered via {@link #listenAll(ObservableValue...)}
   */
  public void release(@NotNull Runnable listenAllRunnable) {
    Iterator<CompositeListener> iterator = myCompositeListeners.iterator();
    while (iterator.hasNext()) {
      CompositeListener listener = iterator.next();
      if (listener.ownsRunnable(listenAllRunnable)) {
        listener.dispose();
        iterator.remove();
      }
    }
  }

  /**
   * Release all listeners registered with this manager.
   */
  public void releaseAll() {
    for (ListenerPairing listener : myListeners) {
      listener.dispose();
    }
    myListeners.clear();
    for (CompositeListener listener : myCompositeListeners) {
      listener.dispose();
    }
    myCompositeListeners.clear();
  }

  private static class ListenerPairing {
    private final ObservableValue<?> myObservable;
    private final InvalidationListener myListener;

    public ListenerPairing(ObservableValue<?> src, InvalidationListener listener) {
      myObservable = src;
      myListener = listener;

      src.addListener(listener);
    }

    public void dispose() {
      myObservable.removeListener(myListener);
    }
  }

  /**
   * Intermediate class which gives the {@link #listenAll(ObservableValue[])} method a fluent
   * interface.
   */
  public final class CompositeListener implements InvalidationListener, Runnable {

    @NotNull private final ObservableValue<?>[] myValues;
    @Nullable private Runnable myOnAnyInvalidated;

    public CompositeListener(@NotNull ObservableValue<?>... values) {
      myValues = values;
      for (ObservableValue<?> value : myValues) {
        value.addListener(this);
      }
    }

    public void dispose() {
      for (ObservableValue<?> value : myValues) {
        value.removeListener(this);
      }
    }

    /**
     * Specify the callback which will be triggered whenever any of the values we are listening to
     * changes.
     */
    public void with(@NotNull Runnable onAnyInvalidated) {
      myOnAnyInvalidated = onAnyInvalidated;
    }

    /**
     * Like {@link #with(Runnable)} but immediately runs the target callback once registered.
     *
     * This is essentially the {@link #listenAndFire(ObservableValue, InvalidationListener)}
     * equivalent of {@link #listenAll(ObservableValue[])}
     */
    public void withAndFire(@NotNull Runnable onAnyInvalidated) {
      with(onAnyInvalidated);
      run();
    }

    boolean ownsRunnable(@NotNull Runnable onAnyInvalidated) {
      return onAnyInvalidated.equals(myOnAnyInvalidated);
    }

    @Override
    public void onInvalidated(@NotNull ObservableValue<?> sender) {
      myInvoker.enqueue(this);
    }

    @Override
    public void run() {
      if (myOnAnyInvalidated != null) {
        myOnAnyInvalidated.run();
      }
    }
  }
}
