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

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static com.android.tools.idea.ui.properties.BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY;
import static junit.framework.TestCase.fail;
import static org.fest.assertions.Assertions.assertThat;

public final class BatchInvokerTest {

  @Test
  public void invokingImmediatelyWorks() throws Exception {
    BatchInvoker invoker = new BatchInvoker(INVOKE_IMMEDIATELY_STRATEGY);
    IntWrapper intWrapper = new IntWrapper();

    AddToValue addToValue = new AddToValue(0, intWrapper, 10);

    assertThat(intWrapper.value).isEqualTo(0);
    invoker.enqueue(addToValue);
    assertThat(intWrapper.value).isEqualTo(10);
    invoker.enqueue(addToValue);
    assertThat(intWrapper.value).isEqualTo(20);
  }

  @Test
  public void batchedInvokingWorks() throws Exception {
    TestInvokeStrategy testStrategy = new TestInvokeStrategy();
    BatchInvoker invoker = new BatchInvoker(testStrategy);

    IntWrapper intWrapper = new IntWrapper();
    AddToValue addToValue10 = new AddToValue(0, intWrapper, 10);
    AddToValue addToValue100 = new AddToValue(1, intWrapper, 100);

    assertThat(intWrapper.value).isEqualTo(0);
    invoker.enqueue(addToValue10);
    invoker.enqueue(addToValue100);
    assertThat(intWrapper.value).isEqualTo(0);
    testStrategy.updateOneStep();
    assertThat(intWrapper.value).isEqualTo(110);
  }

  @Test
  public void allDeferredInvocationsRunAtOnce() throws Exception {
    TestInvokeStrategy testStrategy = new TestInvokeStrategy();
    BatchInvoker invoker = new BatchInvoker(testStrategy);

    IntWrapper intWrapper = new IntWrapper();
    AddToValue addToValue1 = new AddToValue(0, intWrapper, 1);
    AddToValue addToValue10 = new AddToValue(1, intWrapper, 10);
    DeferRunnable deferRunnable = new DeferRunnable(invoker);
    deferRunnable.setRunnable(addToValue10);

    invoker.enqueue(addToValue1);
    invoker.enqueue(deferRunnable);
    testStrategy.updateOneStep();
    assertThat(intWrapper.value).isEqualTo(11);
  }

  @Test
  public void batchedInvokingDropsRedundantUpdates() throws Exception {
    TestInvokeStrategy testStrategy = new TestInvokeStrategy();
    BatchInvoker invoker = new BatchInvoker(testStrategy);
    IntWrapper intWrapper = new IntWrapper();

    // For add events with the same ID, all but the first will be dropped
    AddToValue addToValue1 = new AddToValue(0, intWrapper, 1);
    AddToValue addToValue10 = new AddToValue(0, intWrapper, 10);
    AddToValue addToValue100 = new AddToValue(0, intWrapper, 100);
    AddToValue addToValue2 = new AddToValue(1, intWrapper, 2);
    AddToValue addToValue20 = new AddToValue(1, intWrapper, 20);
    AddToValue addToValue200 = new AddToValue(1, intWrapper, 200);

    invoker.enqueue(addToValue1);
    invoker.enqueue(addToValue10); // dropped
    invoker.enqueue(addToValue100); // dropped
    invoker.enqueue(addToValue2);
    invoker.enqueue(addToValue20); // dropped
    invoker.enqueue(addToValue200); // dropped
    testStrategy.updateOneStep();
    assertThat(intWrapper.value).isEqualTo(3);
  }

  @Test
  public void infiniteCycleThrowsException() throws Exception {
    BatchInvoker invoker = new BatchInvoker(INVOKE_IMMEDIATELY_STRATEGY);
    DeferRunnable runnableA = new DeferRunnable(invoker);
    DeferRunnable runnableB = new DeferRunnable(invoker);
    runnableA.setRunnable(runnableB);
    runnableB.setRunnable(runnableA);

    try {
      invoker.enqueue(runnableA); // A -> B -> A -> B -> ...
      fail();
    }
    catch (BatchInvoker.InfiniteCycleException ignored) {
    }

    // Ensure invoker continues to work after throwing an exception
    IntWrapper intWrapper = new IntWrapper();
    invoker.enqueue(new AddToValue(0, intWrapper, 123));
    assertThat(intWrapper.value).isEqualTo(123);
  }

  private static final class IntWrapper {
    int value;
  }

  /**
   * Simple runnable with ID (and runnables with the same ID should be collapsed)
   */
  private static final class AddToValue implements Runnable {
    @NotNull private final IntWrapper myTarget;
    private final int myId;
    private final int myAmount;

    public AddToValue(int id, @NotNull IntWrapper target, int amount) {
      myTarget = target;
      myId = id;
      myAmount = amount;
    }

    @Override
    public void run() {
      myTarget.value += myAmount;
    }

    /**
     * Equality purely based on ID, not anything else. This allows multiple AddToValue runnables
     * with the same ID to collapse.
     */
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AddToValue that = (AddToValue)o;
      return myId == that.myId;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myId);
    }
  }

  /**
   * A runnable which assigns another runnable to a target invoker. This will let us unit test
   * deferred behavior and infinite loop scenarios.
   */
  private static final class DeferRunnable implements Runnable {
    @NotNull private final BatchInvoker myOwningInvoker;
    @Nullable private Runnable myOther;

    public DeferRunnable(@NotNull BatchInvoker owningInvoker) {
      myOwningInvoker = owningInvoker;
    }

    public void setRunnable(@NotNull Runnable other) {
      myOther = other;
    }

    @Override
    public void run() {
      assert myOther != null;
      myOwningInvoker.enqueue(myOther);
    }
  }
}