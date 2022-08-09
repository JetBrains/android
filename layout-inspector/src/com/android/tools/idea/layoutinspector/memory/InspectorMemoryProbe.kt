/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE") // TODO: remove usage of sun.awt.image.SurfaceManager.
package com.android.tools.idea.layoutinspector.memory

import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.layoutinspector.metrics.statistics.MemoryStatistics
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.stats.MemoryProbe
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

private const val DOT = "."

/**
 * Memory Probe for the Layout Inspector.
 *
 * The memory usage kept in the model will be calculated after each structural change.
 * The calculation is performed on a background thread and will be cancelled if another
 * update happens during the calculation.
 */
class InspectorMemoryProbe(
  private val model: InspectorModel,
  private val stats: MemoryStatistics
) {

  private val includedPackagePrefixes = listOf(
    ViewNode::class.java.`package`.name + DOT,
    ResourceType::class.java.`package`.name + DOT,
    ResourceReference::class.java.`package`.name + DOT,
    ImmutableList::class.java.`package`.name + DOT,
    java.awt.Image::class.java.`package`.name + DOT,
    java.lang.Object::class.java.`package`.name + DOT,
    java.util.ArrayList::class.java.`package`.name + DOT,
    "sun.awt.image."
  )

  private val excludedClasses = listOf(
    sun.awt.image.SurfaceManager::class.java // avoid counting cached data from awt SurfaceManager
  )

  init {
    model.modificationListeners.add(::modelChanged)
  }

  @Suppress("UNUSED_PARAMETER")
  private fun modelChanged(old: AndroidWindow?, new: AndroidWindow?, structuralChange: Boolean) {
    if (structuralChange) {
      run(model.lastGeneration)
    }
  }

  private fun run(generation: Int) {
    ApplicationManager.getApplication()?.executeOnPooledThread { checkMemory(generation) }
  }

  private fun checkMemory(generation: Int) {
    val startTime = System.currentTimeMillis()
    val cancelled = { model.updating || model.lastGeneration != generation }
    val probe = MemoryProbe(includedPackagePrefixes, excludedClasses = excludedClasses, excludeStaticFields = true, cancelled = cancelled)
    val size = probe.check(model.root)
    val elapsed = System.currentTimeMillis() - startTime
    if (!probe.wasCancelled) {
      val hasSkiaImages = model.windows.values.any { it.imageType == ImageType.SKP || it.imageType == ImageType.SKP_PENDING }
      stats.recordModelSize(hasSkiaImages, size, elapsed)
      Logger.getInstance(InspectorMemoryProbe::class.java).debug("Layout Inspector Memory Use: ${size / 1024 / 1024}mb time: ${elapsed}ms")
    }
  }
}
