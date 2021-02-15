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
package org.jetbrains.android.uipreview

import com.google.common.util.concurrent.MoreExecutors
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * When executing the user code some code paths may take significantly longer time when executed the first time. This happens because the
 * [ClassLoader] has to load the classes used in those paths first. If that happens e.g. in interactive preview this produces visual
 * glitches and might even affect the logic. In order to prevent this from happening, we load the classes in advance.
 */
@JvmOverloads
fun preload(
  moduleClassLoader: ModuleClassLoader,
  classesToPreload: Collection<String>,
  executor: Executor = MoreExecutors.directExecutor()): CompletableFuture<Void> {
  val future = CompletableFuture<Void>()
  val moduleClassLoaderRef = WeakReference(moduleClassLoader)

  executor.execute {
    try {
      val classLoader = moduleClassLoaderRef.get() ?: return@execute
      for (classToPreload in classesToPreload) {
        try {
          if (future.isCancelled) {
            break
          }
          classLoader.loadClass(classToPreload)
        }
        catch (ignore: ClassNotFoundException) {
        }
      }
    }
    finally {
      future.complete(null)
    }
  }
  return future
}
