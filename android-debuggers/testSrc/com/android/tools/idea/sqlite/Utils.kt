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
package com.android.tools.idea.sqlite

import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.sqlite.jdbc.SqliteJdbcServiceTest
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

object Utils {
  fun <V> pumpEventsAndWaitForFuture(future: ListenableFuture<V>): V {
    try {
      return pumpEventsAndWaitForFuture(future, SqliteJdbcServiceTest.TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }
    catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  fun <V> pumpEventsAndWaitForFutureException(future: ListenableFuture<V>): Throwable {
    try {
      pumpEventsAndWaitForFuture(future, SqliteJdbcServiceTest.TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
      throw RuntimeException("Expected ExecutionException from future, got value instead")
    }
    catch (e: ExecutionException) {
      return e.cause ?: e
    }
    catch (t: Throwable) {
      throw RuntimeException("Expected ExecutionException from future, got Throwable instead", t)
    }
  }
}
