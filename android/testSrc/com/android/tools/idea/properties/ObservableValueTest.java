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
package com.android.tools.idea.properties;

import com.intellij.util.GCUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public final class ObservableValueTest {

  @Test
  public void listenerIsTriggeredOnInvalidation() throws Exception {
    final MutableInt listenerCount = new MutableInt();
    MockObservable mockObservable = new MockObservable();

    mockObservable.addListener(new InvalidationListener<Integer>() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<Integer> sender) {
        listenerCount.value++;
      }
    });

    assertThat(listenerCount.value).isEqualTo(0);

    mockObservable.fireInvalidated();
    assertThat(listenerCount.value).isEqualTo(1);

    mockObservable.fireInvalidated();
    assertThat(listenerCount.value).isEqualTo(2);
  }

  @Test
  public void removeListenerPreventsFurtherCallbacks() throws Exception {
    final MutableInt listenerCount = new MutableInt();
    MockObservable mockObservable = new MockObservable();

    InvalidationListener<Integer> listener = new InvalidationListener<Integer>() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<Integer> sender) {
        listenerCount.value++;
      }
    };
    mockObservable.addListener(listener);

    assertThat(listenerCount.value).isEqualTo(0);

    mockObservable.fireInvalidated();
    assertThat(listenerCount.value).isEqualTo(1);

    mockObservable.removeListener(listener);

    mockObservable.fireInvalidated();
    assertThat(listenerCount.value).isEqualTo(1);
  }

  @Test
  public void weakListenersCanBeRemovedManually() throws Exception {
    final MutableInt weakListenerCount = new MutableInt();

    MockObservable mockObservable = new MockObservable();
    InvalidationListener<Integer> listener = new InvalidationListener<Integer>() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<Integer> sender) {
        weakListenerCount.value++;
      }
    };
    mockObservable.addWeakListener(listener);
    mockObservable.removeListener(listener);

    mockObservable.fireInvalidated();
    assertThat(weakListenerCount.value).isZero();
  }

  /**
   * This test works but is slow (2 seconds) and almost certainly dependent on GC implementation.
   * Therefore, it is commented out, but you can run it locally to see it work.
   */
  //@Test
  public void weakListenerIsRemovedAutomaticallyByGc() throws Exception {
    final MutableInt strongListenerCount = new MutableInt();
    final MutableInt weakListenerCount = new MutableInt();

    MockObservable mockObservable = new MockObservable();
    mockObservable.addListener(new InvalidationListener<Integer>() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<Integer> sender) {
        strongListenerCount.value++;
      }
    });

    mockObservable.addWeakListener(new InvalidationListener<Integer>() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<Integer> sender) {
        weakListenerCount.value++;
      }
    });

    assertThat(strongListenerCount.value).isEqualTo(0);
    assertThat(weakListenerCount.value).isEqualTo(0);

    mockObservable.fireInvalidated();
    assertThat(strongListenerCount.value).isEqualTo(1);
    assertThat(weakListenerCount.value).isEqualTo(1);

    GCUtil.tryForceGC();

    mockObservable.fireInvalidated();
    assertThat(strongListenerCount.value).isEqualTo(2);
    assertThat(weakListenerCount.value).isEqualTo(1);
  }

  private static final class MockObservable extends AbstractObservable<Integer> {
    @NotNull
    @Override
    public Integer get() {
      return -1;
    }

    public void fireInvalidated() {
      notifyInvalidated();
    }
  }

  private static class MutableInt {
    public int value;
  }
}