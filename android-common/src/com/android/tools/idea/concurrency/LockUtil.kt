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
@file:JvmName("LockUtil")
package com.android.tools.idea.concurrency

import com.intellij.openapi.application.runReadAction
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Runs the specified [action] with the IDE read lock (obtained first) as well as the [ReentrantLock] (obtained second), to prevent
 * deadlocks when some callers are already in a read/write action and some are not.
 *
 * Fix for b/128355384 and similar bugs.
 */
inline fun <T> ReentrantLock.withLockAndReadAccess(crossinline action: () -> T): T {
  return runReadAction { this.withLock(action) }
}

/**
 * Runs the specified [action] with the IDE read lock (obtained first) as well as the [ReentrantLock] (obtained second), to prevent
 * deadlocks when some callers are already in a read/write action and some are not.
 *
 * Fix for b/128355384 and similar bugs.
 */
fun ReentrantLock.withLockAndReadAccess(action: Runnable) {
  return runReadAction { this.withLock(action::run) }
}

/**
 * Runs the specified [action] with the IDE read lock (obtained first) as well as the [object]'s monitor (obtained second), to prevent
 * deadlocks when some callers are already in a read/write action and some are not.
 *
 * Fix for b/128355384 and similar bugs.
 */
inline fun <T> synchronizedWithReadAccess(`object`: Any, crossinline action: () -> T): T {
  return runReadAction { synchronized(`object`, action) }
}

/**
 * Runs the specified [action] with the IDE read lock (obtained first) as well as the [object]'s monitor (obtained second), to prevent
 * deadlocks when some callers are already in a read/write action and some are not.
 *
 * Fix for b/128355384 and similar bugs.
 */
fun synchronizedWithReadAccess(`object`: Any, action: Runnable) {
  return runReadAction { synchronized(`object`, action::run) }
}
