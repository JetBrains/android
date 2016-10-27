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

import com.intellij.util.GCUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class ObservableValueTest {

  @Test
  public void listenerIsTriggeredOnInvalidation() throws Exception {
    CountListener listener = new CountListener();
    MockObservableValue mockValue = new MockObservableValue();
    mockValue.addListener(listener);
    
    assertThat(listener.getCount()).isEqualTo(0);

    mockValue.fireInvalidated();
    assertThat(listener.getCount()).isEqualTo(1);

    mockValue.fireInvalidated();
    assertThat(listener.getCount()).isEqualTo(2);
  }

  @Test
  public void removeListenerPreventsFurtherCallbacks() throws Exception {
    CountListener listener = new CountListener();
    MockObservableValue mockValue = new MockObservableValue();
    mockValue.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);

    mockValue.fireInvalidated();
    assertThat(listener.getCount()).isEqualTo(1);

    mockValue.removeListener(listener);

    mockValue.fireInvalidated();
    assertThat(listener.getCount()).isEqualTo(1);
  }

  @Test
  public void weakListenersCanBeRemovedManually() throws Exception {
    CountListener listener = new CountListener();
    MockObservableValue mockValue = new MockObservableValue();
    mockValue.addWeakListener(listener);
    mockValue.removeListener(listener);

    mockValue.fireInvalidated();
    assertThat(listener.getCount()).isEqualTo(0);
  }

  /**
   * This test works but is slow (2 seconds) and almost certainly dependent on GC implementation.
   * Therefore, it is commented out, but you can run it locally to see it work.
   */
  //@Test
  public void weakListenerIsRemovedAutomaticallyByGc() throws Exception {
    final MutableInt strongCount = new MutableInt();
    final MutableInt weakCount = new MutableInt();
    MockObservableValue mockValue = new MockObservableValue();
    mockValue.addListener(new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        strongCount.value++;
      }
    });
    mockValue.addWeakListener(new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        weakCount.value++;
      }
    });

    assertThat(strongCount.value).isEqualTo(0);
    assertThat(weakCount.value).isEqualTo(0);

    mockValue.fireInvalidated();
    assertThat(strongCount.value).isEqualTo(1);
    assertThat(weakCount.value).isEqualTo(1);

    GCUtil.tryForceGC();

    mockValue.fireInvalidated();
    assertThat(strongCount.value).isEqualTo(2);
    assertThat(weakCount.value).isEqualTo(1);
  }

  private static final class MockObservableValue extends AbstractObservableValue {
    public void fireInvalidated() {
      notifyInvalidated();
    }

    @NotNull
    @Override
    public Object get() {
      throw new UnsupportedOperationException(); // Not used in this test
    }
  }

  private static final class MutableInt {
    public int value;
  }
}