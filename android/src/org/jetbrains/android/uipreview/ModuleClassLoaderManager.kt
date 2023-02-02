/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.rendering.classloading.ClassTransform

/**
 * Responsible for providing access to [ModuleClassLoader]s.
 *
 * This is required because normally [ModuleClassLoader] is a very heavy resource, and it is important to keep as few instances of those
 * as possible and delete those right after they are no longer needed.
 */
interface ModuleClassLoaderManager {
  fun getShared(parent: ClassLoader?, moduleRenderContext: ModuleRenderContext, holder: Any,
                additionalProjectTransformation: ClassTransform = ClassTransform.identity,
                additionalNonProjectTransformation: ClassTransform = ClassTransform.identity,
                onNewModuleClassLoader: Runnable = Runnable {}): ModuleClassLoader

  // Workaround for interfaces not currently supporting @JvmOverloads (https://youtrack.jetbrains.com/issue/KT-36102)
  fun getShared(parent: ClassLoader?, moduleRenderContext: ModuleRenderContext, holder: Any): ModuleClassLoader

  fun getPrivate(parent: ClassLoader?,
                 moduleRenderContext: ModuleRenderContext,
                 holder: Any,
                 additionalProjectTransformation: ClassTransform = ClassTransform.identity,
                 additionalNonProjectTransformation: ClassTransform = ClassTransform.identity): ModuleClassLoader

  fun getPrivate(parent: ClassLoader?, moduleRenderContext: ModuleRenderContext, holder: Any): ModuleClassLoader

  fun release(moduleClassLoader: ModuleClassLoader, holder: Any)
}