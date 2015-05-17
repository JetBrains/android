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

import com.android.tools.idea.ui.properties.collections.ObservableList;
import com.android.tools.idea.ui.properties.exceptions.BindingCycleException;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions;
import com.android.tools.idea.ui.properties.expressions.list.ListExpression;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Class which manages associations between properties and target values, updating those properties
 * when the target value they are listening to changes.
 * <p/>
 * One-way bindings and two-way bindings are supported. For more details, see
 * {@link #bind(ObservableProperty, ObservableValue)} and
 * {@link #bindTwoWay(ObservableProperty, ObservableProperty)}.
 */
public final class BindingsManager {
  /**
   * Ensure bindings aren't registered in a way that causes an infinite loop, with properties
   * updating other properties back and forth forever. Valid update loops usually settle within
   * 2 or 3 steps.
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
   * Simple interface for how bindings should update themselves once the target they're listening
   * to has changed, which are added to a queue to be invoked later. A class that implements this
   * interface should also implement {@link #equals(Object)} and {@link #hashCode()} as it's
   * possible the same update request may be enqueued multiple times in a single frame (if the
   * target value is invalidated multiple times), but we really only want to update once.
   */
  private interface Updater {
    void update();
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

  private final Map<ObservableProperty<?>, OneWayBinding<?>> myOneWayBindings = Maps.newHashMap();
  private final Table<ObservableProperty<?>, ObservableProperty<?>, TwoWayBinding<?>> myTwoWayBindings = HashBasedTable.create();
  private final List<ListBindingWrapper> myListWrappers = Lists.newArrayList();
  private final Queue<Updater> myUpdaters = Queues.newArrayDeque();
  private final Queue<Updater> myDeferredUpdaters = Queues.newArrayDeque();

  private boolean myUpdateInProgress;
  private int myCycleCount;

  private final InvokeStrategy myInvokeStrategy;

  /**
   * Note: The default constructor uses an update strategy that defers property evaluation to the
   * UI thread. This is fine if you are restricting read and write access to your properties to
   * only happen on the UI thread as well (recommended), but if you want to bind properties in a
   * thread-safe manner, you will need to construct a BindingsManager using a custom invoke
   * strategy.
   */
  public BindingsManager() {
    this(APPLICATION_INVOKE_LATER_STRATEGY);
  }

  public BindingsManager(InvokeStrategy invokeStrategy) {
    myInvokeStrategy = invokeStrategy;
  }

  /**
   * Binds a property to a target value. Whenever the target value changes, the property will
   * be updated to reflect it.
   * <p/>
   * Setting a bound property's value is allowed but discouraged, as it will be overwritten as soon
   * as the target value changes, and this may be hard to debug.
   */
  public <T> void bind(@NotNull ObservableProperty<T> dest, @NotNull ObservableValue<T> src) {
    bind(dest, src, BooleanExpressions.TRUE);
  }

  /**
   * Like {@link #bind(ObservableProperty, ObservableValue)}, but takes an additional observable boolean
   * which, while set to false, disables the binding.
   * <p/>
   * This can be useful for UI fields that are initially linked to each other but which may break
   * that link later on.
   */
  public <T> void bind(@NotNull ObservableProperty<T> dest, @NotNull ObservableValue<T> src, @NotNull ObservableValue<Boolean> enabled) {
    release(dest);

    myOneWayBindings.put(dest, new OneWayBinding<T>(dest, src, enabled));
  }

  /**
   * Binds two properties to each other. Whenever either property changes, the other property will
   * be updated to reflect it.
   * <p/>
   * Although both properties can influence the other once bound, when this method is first called,
   * the first parameter will be initialized with the of the second.
   */
  public <T> void bindTwoWay(@NotNull ObservableProperty<T> first, @NotNull ObservableProperty<T> second) {
    releaseTwoWay(first, second);

    myTwoWayBindings.put(first, second, new TwoWayBinding<T>(first, second));
  }

  /**
   * Binds a list to a target list expression.
   */
  public <S, D> void bindList(@NotNull ObservableList<D> destList, @NotNull ListExpression<S, D> srcExpression) {
    releaseList(destList);

    myListWrappers.add(new ListBindingWrapper(destList, new ListBinding<S, D>(destList, srcExpression)));
  }

  /**
   * Releases a one-way binding previously registered via {@link #bind(ObservableProperty, ObservableValue)}
   */
  public void release(@NotNull ObservableProperty<?> dest) {
    OneWayBinding<?> oneWayBinding = myOneWayBindings.get(dest);
    if (oneWayBinding == null) {
      return;
    }

    oneWayBinding.dispose();
    myOneWayBindings.remove(dest);
  }

  /**
   * Releases a two-way binding previously registered via
   * {@link #bindTwoWay(ObservableProperty, ObservableProperty)}.
   */
  public <T> void releaseTwoWay(@NotNull ObservableProperty<T> first, @NotNull ObservableProperty<T> second) {
    TwoWayBinding<?> twoWayBinding = myTwoWayBindings.get(first, second);
    if (twoWayBinding == null) {
      return;
    }

    twoWayBinding.dispose();
    myTwoWayBindings.remove(first, second);
  }

  /**
   * Releases a mapping previously registered via {@link #bindList(ObservableList, ListExpression)}
   */
  public <T> void releaseList(@NotNull ObservableList<T> destList) {
    Iterator<ListBindingWrapper> i = myListWrappers.iterator();
    while (i.hasNext()) {
      ListBindingWrapper wrapper = i.next();
      if (wrapper.getList() == destList) {
        wrapper.getBinding().dispose();
        i.remove();
        break;
      }
    }
  }


  /**
   * Release all bindings (one-way and two-way) registered with this bindings manager.
   */
  public void releaseAll() {
    for (OneWayBinding<?> oneWayBinding : myOneWayBindings.values()) {
      oneWayBinding.dispose();
    }
    myOneWayBindings.clear();

    for (TwoWayBinding<?> twoWayBinding : myTwoWayBindings.values()) {
      twoWayBinding.dispose();
    }
    myTwoWayBindings.clear();

    for (ListBindingWrapper listWrapper : myListWrappers) {
      listWrapper.getBinding().dispose();
    }
    myListWrappers.clear();
  }

  private void enqueueUpdater(@NotNull Updater updater) {
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
        for (Updater propertyUpdater : myUpdaters) {
          propertyUpdater.update();
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
          invokeUpdate(); // Call self again with properties invalidated by this last cycle
        }
        else {
          myCycleCount = 0;
        }
      }
    });
  }

  private class OneWayBinding<T> extends InvalidationListener {
    private final ObservableProperty<T> myPropertyDest;
    private final ObservableValue<T> myObservableSrc;
    private final ObservableValue<Boolean> myEnabled;

    @Override
    protected void onInvalidated(@NotNull Observable sender) {
      if (myEnabled.get()) {
        enqueueUpdater(new PropertyUpdater<T>(myPropertyDest, myObservableSrc));
      }
    }

    public OneWayBinding(ObservableProperty<T> propertyDest, ObservableValue<T> observableSrc, ObservableValue<Boolean> enabled) {
      myPropertyDest = propertyDest;
      myObservableSrc = observableSrc;
      myEnabled = enabled;

      myObservableSrc.addListener(this);
      myEnabled.addListener(this);

      // Once bound, force the dest property to refresh its value from the src property
      onInvalidated(observableSrc);
    }

    public void dispose() {
      myObservableSrc.removeListener(this);
      myEnabled.removeListener(this);
    }
  }

  private class TwoWayBinding<T> {
    private final ObservableProperty<T> myPropertyLhs;
    private final ObservableProperty<T> myPropertyRhs;
    private final InvalidationListener myLeftChangedListener = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull Observable sender) {
        enqueueUpdater(new PropertyUpdater<T>(myPropertyRhs, myPropertyLhs));
      }
    };
    private final InvalidationListener myRightChangedListener = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull Observable sender) {
        enqueueUpdater(new PropertyUpdater<T>(myPropertyLhs, myPropertyRhs));
      }
    };

    public TwoWayBinding(ObservableProperty<T> propertyLhs, ObservableProperty<T> propertyRhs) {
      myPropertyLhs = propertyLhs;
      myPropertyRhs = propertyRhs;
      myPropertyLhs.addListener(myLeftChangedListener);
      myPropertyRhs.addListener(myRightChangedListener);

      // Once bound, force the left property to refresh its value from the right property
      myRightChangedListener.onInvalidated(propertyRhs);
    }

    public void dispose() {
      myPropertyLhs.removeListener(myLeftChangedListener);
      myPropertyRhs.removeListener(myRightChangedListener);
    }
  }

  private class ListBinding<S, D> extends InvalidationListener implements Updater {
    private final ObservableList<D> myDestList;
    private final ListExpression<S, D> mySrcExpression;

    private ListBinding(ObservableList<D> destList, ListExpression<S, D> srcExpression) {
      myDestList = destList;
      mySrcExpression = srcExpression;
      srcExpression.addListener(this);

      // Once bound, force the dest list to refresh its contents based on the source list
      onInvalidated(srcExpression);
    }

    @Override
    protected void onInvalidated(@NotNull Observable sender) {
      enqueueUpdater(this);
    }

    @Override
    public void update() {
      myDestList.setAll(mySrcExpression.get());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ListBinding<?, ?> that = (ListBinding<?, ?>)o;
      return Objects.equal(myDestList, that.myDestList) && Objects.equal(mySrcExpression, that.mySrcExpression);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myDestList, mySrcExpression);
    }

    public void dispose() {
      mySrcExpression.removeListener(this);
    }
  }

  /**
   * Simple helper class which wraps a property and a value and can update the property on request.
   * This class is used by both {@link OneWayBinding} and {@link TwoWayBinding} to enqueue an
   * update after they detect a change.
   */
  private static final class PropertyUpdater<T> implements Updater {
    private final ObservableProperty<T> myDest;
    private final ObservableValue<T> mySrc;

    public PropertyUpdater(ObservableProperty<T> dest, ObservableValue<T> src) {
      myDest = dest;
      mySrc = src;
    }

    @Override
    public void update() {
      myDest.set(mySrc.get());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PropertyUpdater<?> that = (PropertyUpdater<?>)o;
      return Objects.equal(myDest, that.myDest) && Objects.equal(mySrc, that.mySrc);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myDest, mySrc);
    }
  }

  /**
   * Wrapper class which helps us work around that Java {@link Map}s don't support having mutable
   * {@link List}s as keys. Ideally, we would have just done HashMap.put(list, binding), but this
   * would break if the list is ever modified.
   *
   * See also: http://stackoverflow.com/a/9973694/1299302
   */
  private static final class ListBindingWrapper {
    private ObservableList myList;
    private ListBinding myBinding;

    public ListBindingWrapper(ObservableList list, ListBinding binding) {
      myList = list;
      myBinding = binding;
    }

    public ObservableList getList() {
      return myList;
    }

    public ListBinding getBinding() {
      return myBinding;
    }
  }
}
