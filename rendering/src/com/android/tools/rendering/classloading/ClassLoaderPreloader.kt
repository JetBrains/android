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
package com.android.tools.rendering.classloading

import com.google.common.util.concurrent.MoreExecutors
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

/**
 * When executing the user code some code paths may take significantly longer time when executed the
 * first time. This happens because the [ClassLoader] has to load the classes used in those paths
 * first. If that happens e.g. in interactive preview this produces visual glitches and might even
 * affect the logic. In order to prevent this from happening, we load the classes in advance.
 */
@JvmOverloads
fun preload(
  classLoader: ClassLoader,
  isActive: () -> Boolean,
  classesToPreload: Collection<String>,
  executor: Executor = MoreExecutors.directExecutor(),
) {
  val classLoaderRef = WeakReference(classLoader)

  executor.execute {
    val theClassLoader = classLoaderRef.get() ?: return@execute
    for (classToPreload in classesToPreload) {
      try {
        if (!isActive()) {
          break
        }
        theClassLoader.loadClass(classToPreload)
      } catch (_: NoClassDefFoundError) {} catch (_: ClassNotFoundException) {}
    }
  }
}
