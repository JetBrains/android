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
package com.android.tools.idea.rendering.classloading.loaders

import com.android.tools.idea.rendering.classloading.ClassConverter
import com.android.tools.idea.rendering.classloading.PseudoClassLocator
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import org.jetbrains.org.objectweb.asm.ClassWriter

/**
 * A [DelegatingClassLoader.Loader] that applies the given [transform] to the loaded classes.
 *
 * The [pseudoClassLocator] is needed for ASM to be able to apply certain transformations without having to
 * fully load the class in the class loader.
 * [asmFlags] includes the flags needed to apply the transformation. See [ClassWriter].
 *
 * [onRewrite] will be called after a class has been successfully transformed.
 */
class AsmTransformingLoader @JvmOverloads constructor(
  private val transform: ClassTransform,
  private val delegate: DelegatingClassLoader.Loader,
  private val pseudoClassLocator: PseudoClassLocator,
  private val asmFlags: Int = ClassWriter.COMPUTE_FRAMES,
  private val onRewrite: (fqcn: String, durationMs: Long, size: Int) -> Unit = { _, _, _ -> }) : DelegatingClassLoader.Loader {

  override fun loadClass(fqcn: String): ByteArray? {
    val bytes = delegate.loadClass(fqcn) ?: return null
    val startTime = System.currentTimeMillis()
    val rewrittenBytes = ClassConverter.rewriteClass(bytes, transform, asmFlags, pseudoClassLocator)
    onRewrite(fqcn, System.currentTimeMillis() - startTime, rewrittenBytes.size)
    return rewrittenBytes
  }
}