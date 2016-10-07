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

import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

/**
 * Class which manages associations between source and destination values, updating the destination
 * values when their source values change.
 * <p/>
 * One-way bindings and two-way bindings are supported. For more details, see
 * {@link #bind(SettableValue, ObservableValue)} and
 * {@link #bindTwoWay(SettableValue, SettableValue)}.
 * <p/>
 * Note: This class is currently not thread-safe. You are expected to read, write, and bind
 * values on the dispatch thread to avoid undefined behavior.
 */
public final class BindingsManager {

  private final List<OneWayBinding<?>> myOneWayBindings = Lists.newArrayList();
  private final List<TwoWayBinding<?>> myTwoWayBindings = Lists.newArrayList();

  private final BatchInvoker myInvoker;

  public BindingsManager() {
    myInvoker = new BatchInvoker();
  }

  public BindingsManager(@NotNull BatchInvoker.Strategy invokeStrategy) {
    myInvoker = new BatchInvoker(invokeStrategy);
  }

  /**
   * Binds one value to another. Whenever the source value changes, the destination value will
   * be updated to reflect it.
   * <p/>
   * If you ever rebind a value, its old binding will be discarded.
   * <p/>
   * Use {@link #bind(SettableValue, ObservableValue, ObservableValue)} instead if you need to
   * break bindings conditionally.
   */
  public <T> void bind(@NotNull SettableValue<T> dest, @NotNull ObservableValue<T> src) {
    bind(dest, src, BooleanExpressions.alwaysTrue());
  }

  /**
   * Like {@link #bind(SettableValue, ObservableValue)}, but takes an additional observable boolean
   * which, while set to false, disables the binding.
   * <p/>
   * If you ever rebind a value, its old binding will be discarded.
   * <p/>
   * This can be useful for UI fields that are initially linked to each other but which may break
   * that link later on.
   */
  public <T> void bind(@NotNull SettableValue<T> dest, @NotNull ObservableValue<T> src, @NotNull ObservableValue<Boolean> enabled) {
    release(dest);

    myOneWayBindings.add(new OneWayBinding<>(dest, src, enabled));
  }

  /**
   * Binds two values to each other. Whenever either value changes, the other value will
   * be updated to reflect it.
   * <p/>
   * Although both values can influence the other once bound, when this method is first called,
   * the first parameter will be initialized with that of the second.
   */
  public <T> void bindTwoWay(@NotNull SettableValue<T> first, @NotNull SettableValue<T> second) {
    releaseTwoWay(first, second);

    myTwoWayBindings.add(new TwoWayBinding<>(first, second));
  }

  /**
   * Releases a one-way binding previously registered via {@link #bind(SettableValue, ObservableValue)}
   */
  public void release(@NotNull SettableValue<?> dest) {
    Iterator<OneWayBinding<?>> i = myOneWayBindings.iterator();
    while (i.hasNext()) {
      OneWayBinding<?> binding = i.next();
      if (binding.myDest == dest) {
        binding.dispose();
        i.remove();
        return;
      }
    }
  }

  /**
   * Releases a two-way binding previously registered via
   * {@link #bindTwoWay(SettableValue, SettableValue)}.
   */
  public <T> void releaseTwoWay(@NotNull SettableValue<T> first, @NotNull SettableValue<T> second) {
    Iterator<TwoWayBinding<?>> i = myTwoWayBindings.iterator();
    while (i.hasNext()) {
      TwoWayBinding<?> binding = i.next();
      if (binding.myLhs == first && binding.myRhs == second) {
        binding.dispose();
        i.remove();
        return;
      }
    }
  }

  /**
   * Releases all two-way bindings registered via {@link #bindTwoWay(SettableValue, SettableValue)}
   * where either the first or second properties match the input value.
   */
  public <T> void releaseTwoWay(@NotNull SettableValue<T> value) {
    Iterator<TwoWayBinding<?>> i = myTwoWayBindings.iterator();
    while (i.hasNext()) {
      TwoWayBinding<?> binding = i.next();
      if (binding.myLhs == value || binding.myRhs == value) {
        binding.dispose();
        i.remove();
      }
    }
  }

  /**
   * Release all bindings (one-way and two-way) registered with this bindings manager.
   */
  public void releaseAll() {
    for (OneWayBinding<?> oneWayBinding : myOneWayBindings) {
      oneWayBinding.dispose();
    }
    myOneWayBindings.clear();

    for (TwoWayBinding<?> twoWayBinding : myTwoWayBindings) {
      twoWayBinding.dispose();
    }
    myTwoWayBindings.clear();
  }

  private final class OneWayBinding<T> implements InvalidationListener {
    private final SettableValue<T> myDest;
    private final ObservableValue<T> mySrc;
    private final ObservableValue<Boolean> myEnabled;

    @Override
    public void onInvalidated(@NotNull ObservableValue<?> sender) {
      if (myEnabled.get()) {
        myInvoker.enqueue(new DestUpdater<>(myDest, mySrc));
      }
    }

    public OneWayBinding(SettableValue<T> dest, ObservableValue<T> src, ObservableValue<Boolean> enabled) {
      myDest = dest;
      mySrc = src;
      myEnabled = enabled;

      mySrc.addListener(this);
      myEnabled.addListener(this);

      // Once bound, force the dest value to initialize itself with the src value
      onInvalidated(src);
    }

    public void dispose() {
      mySrc.removeListener(this);
      myEnabled.removeListener(this);
    }
  }

  private final class TwoWayBinding<T> {
    private final SettableValue<T> myLhs;
    private final SettableValue<T> myRhs;
    private final InvalidationListener myLeftChangedListener = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        myInvoker.enqueue(new DestUpdater<>(myRhs, myLhs));
      }
    };
    private final InvalidationListener myRightChangedListener = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        myInvoker.enqueue(new DestUpdater<>(myLhs, myRhs));
      }
    };

    public TwoWayBinding(SettableValue<T> lhs, SettableValue<T> rhs) {
      myLhs = lhs;
      myRhs = rhs;
      myLhs.addListener(myLeftChangedListener);
      myRhs.addListener(myRightChangedListener);

      // Once bound, force the left value to initialize itself with the right value
      myRightChangedListener.onInvalidated(rhs);
    }

    public void dispose() {
      myLhs.removeListener(myLeftChangedListener);
      myRhs.removeListener(myRightChangedListener);
    }
  }

  /**
   * Simple helper class which wraps source and destination values and can update the destination
   * value on request. This class is used by both {@link OneWayBinding} and {@link TwoWayBinding}
   * to enqueue an update after they detect a change.
   */
  private static final class DestUpdater<T> implements Runnable {
    private final SettableValue<T> myDest;
    private final ObservableValue<T> mySrc;

    public DestUpdater(SettableValue<T> dest, ObservableValue<T> src) {
      myDest = dest;
      mySrc = src;
    }

    @Override
    public void run() {
      myDest.set(mySrc.get());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DestUpdater<?> that = (DestUpdater<?>)o;
      return Objects.equal(myDest, that.myDest) && Objects.equal(mySrc, that.mySrc);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myDest, mySrc);
    }
  }
}
