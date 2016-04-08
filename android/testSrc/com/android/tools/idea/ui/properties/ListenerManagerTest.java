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

import com.android.tools.idea.ui.properties.core.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class ListenerManagerTest {

  @Test
  public void simpleListenerWorks() throws Exception {
    ListenerManager listeners = new ListenerManager();

    IntListener intListener = new IntListener();
    IntValueProperty intProperty = new IntValueProperty(10);

    listeners.listen(intProperty, intListener);

    assertThat(intListener.myInvalidationCount).isEqualTo(0);
    assertThat(intListener.myLastValue).isEqualTo(0);

    intProperty.set(20);
    assertThat(intListener.myInvalidationCount).isEqualTo(1);
    assertThat(intListener.myLastValue).isEqualTo(20);

    intProperty.set(20);
    assertThat(intListener.myInvalidationCount).isEqualTo(1);
    assertThat(intListener.myLastValue).isEqualTo(20);

    intProperty.set(30);
    assertThat(intListener.myInvalidationCount).isEqualTo(2);
    assertThat(intListener.myLastValue).isEqualTo(30);
  }

  @Test
  public void simpleConsumerWorks() throws Exception {
    ListenerManager listeners = new ListenerManager();

    IntReceiver intListener = new IntReceiver();
    IntValueProperty intProperty = new IntValueProperty(10);

    listeners.listen(intProperty, intListener);

    assertThat(intListener.myInvalidationCount).isEqualTo(0);
    assertThat(intListener.myLastValue).isEqualTo(0);

    intProperty.set(20);
    assertThat(intListener.myInvalidationCount).isEqualTo(1);
    assertThat(intListener.myLastValue).isEqualTo(20);

    intProperty.set(20);
    assertThat(intListener.myInvalidationCount).isEqualTo(1);
    assertThat(intListener.myLastValue).isEqualTo(20);

    intProperty.set(30);
    assertThat(intListener.myInvalidationCount).isEqualTo(2);
    assertThat(intListener.myLastValue).isEqualTo(30);
  }

  @Test
  public void listenAndFireWorks() throws Exception {
    ListenerManager listeners = new ListenerManager();

    IntListener intListener = new IntListener();
    IntValueProperty intProperty = new IntValueProperty(10);

    listeners.listenAndFire(intProperty, intListener);

    assertThat(intListener.myInvalidationCount).isEqualTo(1);
    assertThat(intListener.myLastValue).isEqualTo(10);

    intProperty.set(20);
    assertThat(intListener.myInvalidationCount).isEqualTo(2);
    assertThat(intListener.myLastValue).isEqualTo(20);
  }

  @Test
  public void listenWithConsumerAndFireWorks() throws Exception {
    ListenerManager listeners = new ListenerManager();

    IntReceiver intListener = new IntReceiver();
    IntValueProperty intProperty = new IntValueProperty(10);

    listeners.listenAndFire(intProperty, intListener);

    assertThat(intListener.myInvalidationCount).isEqualTo(1);
    assertThat(intListener.myLastValue).isEqualTo(10);

    intProperty.set(20);
    assertThat(intListener.myInvalidationCount).isEqualTo(2);
    assertThat(intListener.myLastValue).isEqualTo(20);
  }

  @Test
  public void listenAllWorks() throws Exception {
    TestInvokeStrategy testStrategy = new TestInvokeStrategy();
    ListenerManager listeners = new ListenerManager(testStrategy);

    IntProperty x = new IntValueProperty(0);
    IntProperty y = new IntValueProperty(0);
    IntProperty w = new IntValueProperty(640);
    IntProperty h = new IntValueProperty(480);
    BoolProperty grayscale = new BoolValueProperty(false);

    CountingRunnable mockRepaint = new CountingRunnable();

    listeners.listenAll(x, y, w, h, grayscale).with(mockRepaint);

    assertThat(mockRepaint.myRunCount).isEqualTo(0);

    w.set(1280);
    h.set(720);

    assertThat(mockRepaint.myRunCount).isEqualTo(0);
    testStrategy.updateOneStep();
    assertThat(mockRepaint.myRunCount).isEqualTo(1);

    grayscale.set(true);
    testStrategy.updateOneStep();
    assertThat(mockRepaint.myRunCount).isEqualTo(2);
  }

  @Test
  public void releasingListenerWorks() throws Exception {
    ListenerManager listeners = new ListenerManager();

    IntListener intListener = new IntListener();
    IntValueProperty intProperty = new IntValueProperty(10);
    listeners.listen(intProperty, intListener);

    intProperty.set(20);
    assertThat(intListener.myInvalidationCount).isEqualTo(1);

    listeners.release(intListener);

    intProperty.set(30);
    assertThat(intListener.myInvalidationCount).isEqualTo(1);
    assertThat(intListener.myLastValue).isEqualTo(20);
  }

  @Test
  public void releasingConsumerWorks() throws Exception {
    ListenerManager listeners = new ListenerManager();

    IntReceiver intListener = new IntReceiver();
    IntValueProperty intProperty = new IntValueProperty(10);
    listeners.listen(intProperty, intListener);

    intProperty.set(20);
    assertThat(intListener.myInvalidationCount).isEqualTo(1);

    listeners.release(intListener);

    intProperty.set(30);
    assertThat(intListener.myInvalidationCount).isEqualTo(1);
    assertThat(intListener.myLastValue).isEqualTo(20);
  }

  @Test
  public void releasingObservableWorks() throws Exception {
    ListenerManager listeners = new ListenerManager();

    IntListener intListener1 = new IntListener();
    IntListener intListener2 = new IntListener();
    IntValueProperty intProperty = new IntValueProperty(10);
    listeners.listen(intProperty, intListener1);
    listeners.listen(intProperty, intListener2);

    intProperty.set(20);
    assertThat(intListener1.myInvalidationCount).isEqualTo(1);
    assertThat(intListener2.myInvalidationCount).isEqualTo(1);

    listeners.release(intProperty);

    intProperty.set(30);
    assertThat(intListener1.myInvalidationCount).isEqualTo(1);
    assertThat(intListener2.myInvalidationCount).isEqualTo(1);
  }

  @Test
  public void releasingCompositeListenerWorks() throws Exception {
    TestInvokeStrategy testStrategy = new TestInvokeStrategy();
    ListenerManager listeners = new ListenerManager(testStrategy);

    IntProperty a = new IntValueProperty();
    IntProperty b = new IntValueProperty();
    IntProperty c = new IntValueProperty();
    IntProperty d = new IntValueProperty();

    CountingRunnable compositeListener = new CountingRunnable();

    listeners.listenAll(a, b, c, d).with(compositeListener);

    a.set(1);
    b.set(2);
    c.set(3);
    testStrategy.updateOneStep();
    assertThat(compositeListener.myRunCount).isEqualTo(1);

    listeners.release(compositeListener);
    d.set(4);
    c.set(5);
    b.set(6);
    testStrategy.updateOneStep();

    assertThat(compositeListener.myRunCount).isEqualTo(1);
  }

  @Test
  public void releaseAllReleasesAllListeners() throws Exception {

    ListenerManager listeners = new ListenerManager();

    IntListener intListener1 = new IntListener();
    IntReceiver intListener2 = new IntReceiver();
    IntValueProperty intProperty1 = new IntValueProperty(10);
    IntValueProperty intProperty2 = new IntValueProperty(10);
    listeners.listen(intProperty1, intListener1);
    listeners.listen(intProperty2, intListener2);

    intProperty1.set(20);
    intProperty2.set(20);
    assertThat(intListener1.myInvalidationCount).isEqualTo(1);
    assertThat(intListener2.myInvalidationCount).isEqualTo(1);

    listeners.releaseAll();

    intProperty1.set(30);
    intProperty2.set(30);
    assertThat(intListener1.myInvalidationCount).isEqualTo(1);
    assertThat(intListener2.myInvalidationCount).isEqualTo(1);
  }

  @Test
  public void releaseAllReleasesAllCompositeListeners() throws Exception {
    TestInvokeStrategy testStrategy = new TestInvokeStrategy();
    ListenerManager listeners = new ListenerManager(testStrategy);

    IntProperty a = new IntValueProperty();
    IntProperty b = new IntValueProperty();
    IntProperty c = new IntValueProperty();
    IntProperty d = new IntValueProperty();

    CountingRunnable compositeListener = new CountingRunnable();

    listeners.listenAll(a, b, c, d).with(compositeListener);

    a.set(1);
    b.set(2);
    c.set(3);
    testStrategy.updateOneStep();
    assertThat(compositeListener.myRunCount).isEqualTo(1);

    listeners.releaseAll();
    d.set(4);
    c.set(5);
    b.set(6);
    testStrategy.updateOneStep();

    assertThat(compositeListener.myRunCount).isEqualTo(1);
  }

  @Test
  public void releasingOneListenerFromMultipleObservablesWorks() {
    ListenerManager listeners = new ListenerManager();

    IntListener intListener1 = new IntListener();
    IntListener intListener2 = new IntListener();
    IntValueProperty intProperty1 = new IntValueProperty(1);
    IntValueProperty intProperty2 = new IntValueProperty(2);
    IntValueProperty intProperty3 = new IntValueProperty(3);
    IntValueProperty intProperty4 = new IntValueProperty(4);

    listeners.listen(intProperty1, intListener1);
    listeners.listen(intProperty2, intListener1);
    listeners.listen(intProperty2, intListener2);
    listeners.listen(intProperty3, intListener1);
    listeners.listen(intProperty4, intListener1);

    intProperty1.set(10);
    intProperty2.set(20);
    intProperty3.set(30);
    intProperty4.set(40);
    assertThat(intListener1.myInvalidationCount).isEqualTo(4);
    assertThat(intListener2.myInvalidationCount).isEqualTo(1);

    listeners.release(intListener1);

    intProperty1.set(100);
    intProperty2.set(200);
    intProperty3.set(300);
    intProperty4.set(400);
    assertThat(intListener1.myInvalidationCount).isEqualTo(4);
    assertThat(intListener2.myInvalidationCount).isEqualTo(2);
  }

  private static class IntListener implements InvalidationListener {
    int myInvalidationCount = 0;
    int myLastValue;

    @Override
    public void onInvalidated(@NotNull ObservableValue<?> sender) {
      myInvalidationCount++;
      myLastValue = ((ObservableInt)sender).get();
    }
  }

  private static class IntReceiver implements Consumer<Integer> {
    int myInvalidationCount = 0;
    int myLastValue;

    @Override
    public void consume(Integer value) {
      myInvalidationCount++;
      myLastValue = value;
    }
  }

  private static class CountingRunnable implements Runnable {
    int myRunCount;

    @Override
    public void run() {
      myRunCount++;
    }
  }
}