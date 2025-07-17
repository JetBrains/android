/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers.memory

import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.ValueObject
import com.intellij.openapi.diagnostic.Logger
import java.util.Locale

/**
 * Analyzes a heap dump to find duplicate bitmap instances based on their pixel data.
 *
 * This analyzer works by finding the shared android.graphics.Bitmap$DumpData`. It
 * builds a map of native pointers to pixel buffers and then hashes the dimensions
 * and buffer content of each bitmap to identify duplicates.
 */
class BitmapDuplicationAnalyzer {

  private val duplicateBitmapInstances = mutableSetOf<InstanceObject>()

  companion object {
    private val LOG = Logger.getInstance(BitmapDuplicationAnalyzer::class.java)
    const val BITMAP_CLASS_NAME = "android.graphics.Bitmap"
    const val BITMAP_DUMP_DATA_CLASS_NAME = "android.graphics.Bitmap\$DumpData"
  }

  /**
   * A data class to hold identifying information for a bitmap to check for equality.
   */
  private data class BitmapInfo(
    val height: Int,
    val width: Int,
    val bufferHash: Int
  ) : Comparable<BitmapInfo> {
    /**
     * Orders bitmaps by their size (dimension HxW) and then buffer hash,
     * in descending order so larger bitmaps are considered more significant.
     */
    override fun compareTo(other: BitmapInfo): Int {
      val areaCompare = (other.width * other.height).compareTo(this.width * this.height)
      if (areaCompare != 0) return areaCompare

      return other.bufferHash.compareTo(this.bufferHash)
    }
  }

  /**
   * Analyzes a collection of instances to find duplicate Bitmaps based on content.
   * This should be called once after all instances are loaded.
   */
  fun analyze(instances: Iterable<InstanceObject>) {
    duplicateBitmapInstances.clear()

    // 1. Find the shared native pointer to buffer map from the heap dump.
    val ptrToBufferMap = buildPtrToBufferMap(instances)
    if (ptrToBufferMap.isEmpty()) {
      LOG.debug("Could not build pointer-to-buffer map. Skipping duplicate bitmap analysis.")
      return
    }

    // 2. Group all bitmap instances by their BitmapInfo (dimensions + content hash).
    val bitmapsByInfo = mutableMapOf<BitmapInfo, MutableList<InstanceObject>>()
    for (instance in instances) {
      if (instance.classEntry.className == BITMAP_CLASS_NAME && instance.depth != Integer.MAX_VALUE) {
        getBitmapInfo(instance, ptrToBufferMap)?.let { info ->
          bitmapsByInfo.computeIfAbsent(info) { mutableListOf() }.add(instance)
        }
      }
    }

    // 3. Any group with more than one instance contains duplicates.
    bitmapsByInfo.values.forEach { instanceList ->
      if (instanceList.size > 1) {
        duplicateBitmapInstances.addAll(instanceList)
      }
    }
  }

  /**
   * Returns the set of InstanceObjects identified as duplicates.
   */
  fun getDuplicateInstances(): Set<InstanceObject> = duplicateBitmapInstances

  /**
   * Creates a [BitmapInfo] object for a given bitmap instance if its dimensions and pixel buffer can be found.
   */
  private fun getBitmapInfo(instance: InstanceObject, ptrToBufferMap: Map<Long, ByteArray>): BitmapInfo? {
    var width: Int? = null
    var height: Int? = null
    var nativePtr: Long? = null

    for (field in instance.fields) {
      when (field.fieldName) {
        "mWidth" -> width = field.value as? Int
        "mHeight" -> height = field.value as? Int
        "mNativePtr" -> nativePtr = field.value as? Long
      }
    }

    if (width == null || height == null || nativePtr == null) return null
    val buffer = ptrToBufferMap[nativePtr] ?: return null

    return BitmapInfo(height, width, buffer.contentHashCode())
  }

  /**
   * Builds a map of native pointers to their corresponding byte buffers.
   */
  private fun buildPtrToBufferMap(instances: Iterable<InstanceObject>): Map<Long, ByteArray> {
    val ptrToBufferMap = mutableMapOf<Long, ByteArray>()

    val dumpDataInstance = instances.firstOrNull { it.classEntry.className == BITMAP_DUMP_DATA_CLASS_NAME && it.depth != Integer.MAX_VALUE }
                           ?: return emptyMap()
    val buffersInstance = getNestedInstanceObject(dumpDataInstance, "buffers") ?: return emptyMap()
    val nativesInstance = getNestedInstanceObject(dumpDataInstance, "natives") ?: return emptyMap()

    val buffersFields = buffersInstance.fields
    val nativesFields = nativesInstance.fields
    if (buffersFields.size != nativesFields.size) {
      LOG.warn(
        String.format(
          Locale.US, "Mismatch in size between 'buffers' (%d) and 'natives' (%d) fields. Cannot process.",
          buffersFields.size, nativesFields.size
        )
      )
      return emptyMap()
    }

    for (i in nativesFields.indices) {
      val nativePtr = nativesFields.getOrNull(i)?.value as? Long ?: continue
      val bufferInstance = buffersFields.getOrNull(i)?.getAsInstance() ?: continue
      val byteArray = getByteArrayFromInstanceObject(bufferInstance) ?: continue
      ptrToBufferMap[nativePtr] = byteArray
    }
    return ptrToBufferMap
  }

  private fun getByteArrayFromInstanceObject(instance: InstanceObject): ByteArray? {
    val arrayObject = instance.arrayObject ?: run {
      LOG.warn("Buffer instance does not contain an ArrayObject.")
      return null
    }

    if (arrayObject.arrayElementType != ValueObject.ValueType.BYTE) {
      LOG.warn("ArrayObject element type is not BYTE. Found: ${arrayObject.arrayElementType}")
      return null
    }

    return arrayObject.asByteArray
  }

  private fun getNestedInstanceObject(parentInstance: InstanceObject, fieldName: String): InstanceObject? {
    return parentInstance.fields.firstOrNull { field ->
      fieldName == field.fieldName && field.value is InstanceObject
    }?.getAsInstance()
  }
}