/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Worker<T> {
  @NotNull
  private final Supplier<? extends WorkerDelegate<T>> myDelegateSupplier;

  @Nullable
  private WorkerDelegate<T> myDelegate;

  @Nullable
  private T myResult;

  Worker(@NotNull Supplier<? extends WorkerDelegate<T>> delegateSupplier) {
    myDelegateSupplier = delegateSupplier;
  }

  @NotNull
  T get() {
    if (myDelegate == null) {
      myDelegate = myDelegateSupplier.get();
      myDelegate.start();

      myResult = myDelegate.getDefault();
    }

    if (!myDelegate.isFinished()) {
      assert myResult != null;
      return myResult;
    }

    myResult = myDelegate.get();

    myDelegate = myDelegateSupplier.get();
    myDelegate.start();

    return myResult;
  }
}
