/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.google.common.util.concurrent.FutureCallback;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CountDownLatchFutureCallback<V> implements FutureCallback<V> {
  private final @NotNull FutureCallback<V> myDelegate;
  private final @NotNull CountDownLatch myLatch;

  public CountDownLatchFutureCallback(@NotNull FutureCallback<V> delegate, @NotNull CountDownLatch latch) {
    myDelegate = delegate;
    myLatch = latch;
  }

  @Override
  public void onSuccess(@Nullable V result) {
    myDelegate.onSuccess(result);
    myLatch.countDown();
  }

  @Override
  public void onFailure(@NotNull Throwable throwable) {
    myDelegate.onFailure(throwable);
    myLatch.countDown();
  }
}
