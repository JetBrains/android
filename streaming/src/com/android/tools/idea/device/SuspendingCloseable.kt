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
package com.android.tools.idea.device

/**
 * Similar to [java.io.Closeable] but with a suspending [close] method.
 */
interface SuspendingCloseable {
  suspend fun close()
}

/**
 * See [java.io.Closeable.use].
 */
suspend inline fun <T : SuspendingCloseable?, R> T.use(block: (T) -> R): R {
  var exception: Throwable? = null
  try {
    return block(this)
  }
  catch (e: Throwable) {
    exception = e
    throw e
  }
  finally {
    if (this != null) {
      if (exception == null) {
        close()
      }
      else {
        try {
          close()
        }
        catch (closeException: Throwable) {
          exception.addSuppressed(closeException)
        }
      }
    }
  }
}
