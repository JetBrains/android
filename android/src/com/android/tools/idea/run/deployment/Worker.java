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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

final class Worker<T> {
  private SwingWorker<T, Void> myDelegate;
  private T myResult;

  @NotNull
  T get(@NotNull Supplier<SwingWorker<T, Void>> delegateSupplier, @NotNull T defaultResult) {
    if (myDelegate == null) {
      myDelegate = delegateSupplier.get();
      myDelegate.execute();

      myResult = defaultResult;
      return defaultResult;
    }

    if (!myDelegate.isDone()) {
      return myResult;
    }

    try {
      myResult = myDelegate.get();
    }
    catch (InterruptedException exception) {
      // This should never happen. The delegate is done and can no longer be interrupted.
      assert false;
    }
    catch (ExecutionException exception) {
      Logger.getInstance(Worker.class).warn(exception);
    }

    myDelegate = delegateSupplier.get();
    myDelegate.execute();

    return myResult;
  }
}
