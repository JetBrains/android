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

import static org.fest.assertions.Assertions.assertThat;

public final class ObservableValueTest {

  @Test
  public void listenerIsTriggeredOnInvalidation() throws Exception {
    CountListener listener = new CountListener();
    MockObservable mockObservable = new MockObservable();
    mockObservable.addListener(listener);
    
    assertThat(listener.getCount()).isEqualTo(0);

    mockObservable.fireInvalidated();
    assertThat(listener.getCount()).isEqualTo(1);

    mockObservable.fireInvalidated();
    assertThat(listener.getCount()).isEqualTo(2);
  }

  @Test
  public void removeListenerPreventsFurtherCallbacks() throws Exception {
    CountListener listener = new CountListener();
    MockObservable mockObservable = new MockObservable();
    mockObservable.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);

    mockObservable.fireInvalidated();
    assertThat(listener.getCount()).isEqualTo(1);

    mockObservable.removeListener(listener);

    mockObservable.fireInvalidated();
    assertThat(listener.getCount()).isEqualTo(1);
  }

  @Test
  public void weakListenersCanBeRemovedManually() throws Exception {
    CountListener listener = new CountListener();
    MockObservable mockObservable = new MockObservable();
    mockObservable.addWeakListener(listener);
    mockObservable.removeListener(listener);

    mockObservable.fireInvalidated();
    assertThat(listener.getCount()).isZero();
  }

  /**
   * This test works but is slow (2 seconds) and almost certainly dependent on GC implementation.
   * Therefore, it is commented out, but you can run it locally to see it work.
   */
  //@Test
  public void weakListenerIsRemovedAutomaticallyByGc() throws Exception {
    final MutableInt strongCount = new MutableInt();
    final MutableInt weakCount = new MutableInt();
    MockObservable mockObservable = new MockObservable();
    mockObservable.addListener(new InvalidationListener() {
      @Override
      protected void onInvalidated(@NotNull Observable sender) {
        strongCount.value++;
      }
    });
    mockObservable.addWeakListener(new InvalidationListener() {
      @Override
      protected void onInvalidated(@NotNull Observable sender) {
        weakCount.value++;
      }
    });

    assertThat(strongCount.value).isEqualTo(0);
    assertThat(weakCount.value).isEqualTo(0);

    mockObservable.fireInvalidated();
    assertThat(strongCount.value).isEqualTo(1);
    assertThat(weakCount.value).isEqualTo(1);

    GCUtil.tryForceGC();

    mockObservable.fireInvalidated();
    assertThat(strongCount.value).isEqualTo(2);
    assertThat(weakCount.value).isEqualTo(1);
  }

  private static final class MockObservable extends AbstractObservable {
    public void fireInvalidated() {
      notifyInvalidated();
    }
  }

  private static final class MutableInt {
    public int value;
  }
}