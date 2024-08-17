/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;

/** SDK adapter to use transaction guards. */
public class Transactions {
  public static void submitTransactionAndWait(Runnable runnable) {
    ApplicationManager.getApplication().invokeAndWait(runnable);
  }

  public static void submitTransaction(Disposable disposable, Runnable runnable) {
    ApplicationManager.getApplication()
        .invokeLater(runnable, /* expired= */ o -> Disposer.isDisposed(disposable));
  }

  /** Runs {@link Runnable} as a write action, inside a transaction. */
  public static void submitWriteActionTransaction(Disposable disposable, Runnable runnable) {
    submitTransaction(
        disposable, () -> ApplicationManager.getApplication().runWriteAction(runnable));
  }

  /** Runs {@link Runnable} as a write action, inside a transaction. */
  public static void submitWriteActionTransactionAndWait(Runnable runnable) {
    submitTransactionAndWait(() -> ApplicationManager.getApplication().runWriteAction(runnable));
  }

  /** Runs {@link Runnable} as a read action, inside a transaction. */
  public static void submitReadActionTransaction(Disposable disposable, Runnable runnable) {
    submitTransaction(
        disposable, () -> ApplicationManager.getApplication().runReadAction(runnable));
  }
}
