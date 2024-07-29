/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.resources.aar.FrameworkResourceRepository
import com.android.tools.concurrency.AndroidIoManager
import com.android.tools.res.FrameworkResourceRepositoryManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CachedSingletonsRegistry
import com.intellij.openapi.application.PathManager
import java.util.concurrent.Executor
import java.util.function.Supplier

/**
 * Studio-specific Application service for caching and reusing instances of
 * [FrameworkResourceRepository].
 */
class StudioFrameworkResourceRepositoryManager :
  FrameworkResourceRepositoryManagerImpl(
    PathManager.getSystemPath(),
    // Don't create a persistent cache in tests to avoid unnecessary overhead.
    if (ApplicationManager.getApplication().isUnitTestMode) Executor {}
    else AndroidIoManager.getInstance().getBackgroundDiskIoExecutor(),
  ) {
  companion object {
    /**
     * [FrameworkResourceRepository] is heavily used in editing so we cache the instance to avoid
     * the expensive service lookup on every [getInstance] request.
     */
    @Suppress("UnstableApiUsage")
    private val instanceSupplier: Supplier<FrameworkResourceRepositoryManagerImpl> =
      CachedSingletonsRegistry.lazy {
        ApplicationManager.getApplication()
          .getService(FrameworkResourceRepositoryManagerImpl::class.java)!!
      }

    @JvmStatic fun getInstance() = instanceSupplier.get()
  }
}
