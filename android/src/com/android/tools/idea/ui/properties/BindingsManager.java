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

import com.android.tools.idea.ui.properties.exceptions.BindingCycleException;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

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
  /**
   * Ensure bindings aren't registered in a way that causes an infinite loop. Valid update loops
   * usually settle within 2 or 3 steps.
   */
  private static final int MAX_CYCLE_COUNT = 10;

  /**
   * A strategy on how to invoke a binding update. Instead of invoking immediately, we often want
   * to postpone the invocation, as that will allow us to avoid doing expensive updates on
   * redundant, intermediate changes, e.g. a repaint will happen if width and/or height changes,
   * and we only want to repaint once if both width and height are changed on the same frame.
   */
  public interface InvokeStrategy {
    void invoke(@NotNull Runnable runnable);
  }

  /**
   * Useful invoke strategy when developing an IDEA Plugin.
   */
  public static final InvokeStrategy APPLICATION_INVOKE_LATER_STRATEGY = new InvokeStrategy() {
    @Override
    public void invoke(@NotNull Runnable runnable) {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
    }
  };

  /**
   * Useful invoke strategy when working on a Swing application.
   */
  public static final InvokeStrategy SWING_INVOKE_LATER_STRATEGY = new InvokeStrategy() {
    @Override
    public void invoke(@NotNull Runnable runnable) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(runnable);
    }
  };

  /**
   * Useful invoke strategy for testing, when you don't care about delaying your binding updates.
   */
  public static final InvokeStrategy INVOKE_IMMEDIATELY_STRATEGY = new InvokeStrategy() {
    @Override
    public void invoke(@NotNull Runnable runnable) {
      runnable.run();
    }
  };

  private final List<OneWayBinding<?>> myOneWayBindings = Lists.newArrayList();
  private final List<TwoWayBinding<?>> myTwoWayBindings = Lists.newArrayList();
  private final Queue<DestUpdater> myUpdaters = Queues.newArrayDeque();
  private final Queue<DestUpdater> myDeferredUpdaters = Queues.newArrayDeque();

  private boolean myUpdateInProgress;
  private int myCycleCount;

  private final InvokeStrategy myInvokeStrategy;

  public BindingsManager() {
    this(ApplicationManager.getApplication() != null ? APPLICATION_INVOKE_LATER_STRATEGY : SWING_INVOKE_LATER_STRATEGY);
  }

  public BindingsManager(InvokeStrategy invokeStrategy) {
    myInvokeStrategy = invokeStrategy;
  }

  /**
   * Binds one value to another. Whenever the source value changes, the destination value will
   * be updated to reflect it.
   * <p/>
   * Setting a bound value is allowed but discouraged, as it will be overwritten as soon as the
   * target value changes, and this may be hard to debug. If you are careful and know what you're
   * doing, this can still be useful - for example, you might also wish to add a listener
   * to the bound value and, detecting an external change, release the binding.
   */
  public <T> void bind(@NotNull SettableValue<T> dest, @NotNull ObservableValue<T> src) {
    bind(dest, src, BooleanExpressions.TRUE);
  }

  /**
   * Like {@link #bind(SettableValue, ObservableValue)}, but takes an additional observable boolean
   * which, while set to false, disables the binding.
   * <p/>
   * This can be useful for UI fields that are initially linked to each other but which may break
   * that link later on.
   */
  public <T> void bind(@NotNull SettableValue<T> dest, @NotNull ObservableValue<T> src, @NotNull ObservableValue<Boolean> enabled) {
    release(dest);

    myOneWayBindings.add(new OneWayBinding<T>(dest, src, enabled));
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

    myTwoWayBindings.add(new TwoWayBinding<T>(first, second));
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

  private void enqueueUpdater(@NotNull DestUpdater updater) {
    if (myUpdateInProgress) {
      if (!myDeferredUpdaters.contains(updater)) {
        myDeferredUpdaters.add(updater);
      }
      return;
    }

    // Prepare to run an update if we're the first update request. Any other requests that are made
    // before the update runs will get lumped in with it.
    boolean shouldInvoke = myUpdaters.isEmpty();
    if (!myUpdaters.contains(updater)) {
      myUpdaters.add(updater);
    }

    if (shouldInvoke) {
      invokeUpdate();
    }
  }

  private void invokeUpdate() {
    myInvokeStrategy.invoke(new Runnable() {
      @Override
      public void run() {
        myUpdateInProgress = true;
        for (DestUpdater updater : myUpdaters) {
          updater.update();
        }
        myUpdaters.clear();
        myUpdateInProgress = false;

        if (!myDeferredUpdaters.isEmpty()) {
          myCycleCount++;
          if (myCycleCount > MAX_CYCLE_COUNT) {
            throw new BindingCycleException();
          }

          myUpdaters.addAll(myDeferredUpdaters);
          myDeferredUpdaters.clear();
          invokeUpdate(); // Call self again with any bindings invalidated by this last cycle
        }
        else {
          myCycleCount = 0;
        }
      }
    });
  }

  private class OneWayBinding<T> extends InvalidationListener {
    private final SettableValue<T> myDest;
    private final ObservableValue<T> mySrc;
    private final ObservableValue<Boolean> myEnabled;

    @Override
    protected void onInvalidated(@NotNull Observable sender) {
      if (myEnabled.get()) {
        enqueueUpdater(new DestUpdater<T>(myDest, mySrc));
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

  private class TwoWayBinding<T> {
    private final SettableValue<T> myLhs;
    private final SettableValue<T> myRhs;
    private final InvalidationListener myLeftChangedListener = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull Observable sender) {
        enqueueUpdater(new DestUpdater<T>(myRhs, myLhs));
      }
    };
    private final InvalidationListener myRightChangedListener = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull Observable sender) {
        enqueueUpdater(new DestUpdater<T>(myLhs, myRhs));
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
  private static final class DestUpdater<T> {
    private final SettableValue<T> myDest;
    private final ObservableValue<T> mySrc;

    public DestUpdater(SettableValue<T> dest, ObservableValue<T> src) {
      myDest = dest;
      mySrc = src;
    }

    public void update() {
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
