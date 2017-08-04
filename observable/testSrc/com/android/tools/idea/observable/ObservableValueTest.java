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

import com.intellij.util.ref.GCUtil;
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

  @Test
  public void weakListenerIsRemovedAutomaticallyByGc() throws Exception {
    final MutableInt strongCount = new MutableInt();
    final MutableInt weakCount = new MutableInt();
    MockObservableValue mockValue = new MockObservableValue();
    mockValue.addListener(sender -> strongCount.value++);
    {
      InvalidationListener scopedListener = sender -> weakCount.value++;
      mockValue.addWeakListener(scopedListener);

      assertThat(strongCount.value).isEqualTo(0);
      assertThat(weakCount.value).isEqualTo(0);

      mockValue.fireInvalidated();
      assertThat(strongCount.value).isEqualTo(1);
      assertThat(weakCount.value).isEqualTo(1);
    }
    // If the next line isn't here, the GC does not clean up the scopedListener, likely as an
    // optimization. See: https://stackoverflow.com/a/25960828
    //noinspection unused
    Object forceGcToCleanUpScopedListener = null;
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