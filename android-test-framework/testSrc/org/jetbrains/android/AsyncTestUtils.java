/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android;

import com.android.tools.idea.util.FutureUtils;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class AsyncTestUtils {
  private static final long TIMEOUT_MILLISECONDS = 30_000;

  public static <V> List<V> pumpEventsAndWaitForFutures(List<ListenableFuture<V>> futures) {
    return pumpEventsAndWaitForFuture(Futures.allAsList(futures));
  }

  public static <V> V pumpEventsAndWaitForFuture(ListenableFuture<V> future) {
    try {
      return FutureUtils.pumpEventsAndWaitForFuture(future, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <V> Throwable pumpEventsAndWaitForFutureException(ListenableFuture<V> future) {
    try {
      FutureUtils.pumpEventsAndWaitForFuture(future, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
      throw new RuntimeException("Expected ExecutionException from future, got value instead");
    }
    catch (ExecutionException e) {
      return e;
    }
    catch (Throwable t) {
      throw new RuntimeException("Expected ExecutionException from future, got Throwable instead", t);
    }
  }
}
