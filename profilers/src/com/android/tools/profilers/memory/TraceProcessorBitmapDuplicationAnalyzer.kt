/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.TraceProcessorHeapDumpInstanceObject
import com.android.tools.profilers.memory.adapters.ValueObject
import com.intellij.openapi.diagnostic.Logger

/** Analyzes a Trace Processor heap dump to find duplicate bitmap instances based on their pixel data. */
class TraceProcessorBitmapDuplicationAnalyzer {

  private val duplicateBitmapInstances = mutableSetOf<InstanceObject>()

  companion object {
    private val LOG = Logger.getInstance(TraceProcessorBitmapDuplicationAnalyzer::class.java)
    const val BITMAP_CLASS_NAME = "android.graphics.Bitmap"
    const val BITMAP_DUMP_DATA_CLASS_NAME = "android.graphics.Bitmap\$DumpData"
  }

  /** A data class to hold identifying information for a bitmap to check for equality. */
  private data class BitmapInfo(val height: Int, val width: Int, val bufferHash: Int) : Comparable<BitmapInfo> {
    override fun compareTo(other: BitmapInfo): Int {
      val areaCompare = (other.width * other.height).compareTo(this.width * this.height)
      if (areaCompare != 0) return areaCompare

      return other.bufferHash.compareTo(this.bufferHash)
    }
  }

  fun analyze(instances: Iterable<InstanceObject>, captureObject: CaptureObject? = null) {
    duplicateBitmapInstances.clear()

    val targetInstances = instances.filter {
      (it.classEntry.className == BITMAP_CLASS_NAME || it.classEntry.className == BITMAP_DUMP_DATA_CLASS_NAME) &&
        it.depth != Integer.MAX_VALUE
    }

    val dumpDataInstances = targetInstances.filter { it.classEntry.className == BITMAP_DUMP_DATA_CLASS_NAME }
    val bitmapInstances = targetInstances.filter { it.classEntry.className == BITMAP_CLASS_NAME }

    val tpDumpData = dumpDataInstances.filterIsInstance<TraceProcessorHeapDumpInstanceObject>()
    val tpBitmaps = bitmapInstances.filterIsInstance<TraceProcessorHeapDumpInstanceObject>()
    if (captureObject is HeapDumpCaptureObject) {
      captureObject.prefetchReferences(tpDumpData + tpBitmaps)
    }

    // 1. Build a map of native pointers to their pixel buffer hashes (API 26+)
    val ptrToBufferMap = buildPtrToBufferMap(dumpDataInstances, captureObject)

    // 2. Prefetch mBuffer byte arrays for API <= 25 traces to prevent N+1 queries
    val mBufferMap = mutableMapOf<Long, TraceProcessorHeapDumpInstanceObject>()
    bitmapInstances.forEach { bitmap ->
      getNestedInstanceObject(bitmap, "mBuffer")?.let { mBufferInst ->
        if (mBufferInst is TraceProcessorHeapDumpInstanceObject) {
          mBufferMap[mBufferInst.instanceId] = mBufferInst
        }
      }
    }
    if (captureObject is HeapDumpCaptureObject && mBufferMap.isNotEmpty()) {
      prefetchPrimitiveFieldsBulk(captureObject, mBufferMap)
    }

    // 3. Group instances by their dimensions and pixel buffer contents.
    try {
      val bitmapsByInfo = mutableMapOf<BitmapInfo, MutableList<InstanceObject>>()
      bitmapInstances.forEach { instance ->
        val info = getBitmapInfo(instance, ptrToBufferMap)
        if (info != null) {
          bitmapsByInfo.computeIfAbsent(info) { mutableListOf() }.add(instance)
        }
      }

      // 4. Any group with more than one instance contains duplicates.
      bitmapsByInfo.values.forEach { instanceList ->
        if (instanceList.size > 1) {
          duplicateBitmapInstances.addAll(instanceList)
        }
      }
    } finally {
      // Free temporary protobuf byte array payloads from long-lived instances
      mBufferMap.values.forEach { it.preloadedPrimitiveFields = null }
    }
  }

  fun getDuplicateInstances(): Set<InstanceObject> = duplicateBitmapInstances

  private fun prefetchPrimitiveFieldsBulk(
    captureObject: HeapDumpCaptureObject,
    instanceMap: Map<Long, TraceProcessorHeapDumpInstanceObject>,
  ) {
    instanceMap.keys.toList().chunked(50).forEach { idChunk ->
      val result = captureObject.getPrimitiveFieldsBulk(idChunk)
      idChunk.forEach { id ->
        instanceMap[id]?.preloadedPrimitiveFields = TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.getDefaultInstance()
      }
      result.instancesList.forEach { protoInst -> instanceMap[protoInst.instanceId]?.preloadedPrimitiveFields = protoInst }
    }
  }

  private fun getBitmapInfo(instance: InstanceObject, ptrToBufferMap: Map<Long, Int>): BitmapInfo? {
    var width: Int? = null
    var height: Int? = null
    var nativePtr: Long? = null
    var bufferInstance: InstanceObject? = null

    for (field in instance.fields) {
      when (field.fieldName) {
        "mWidth" -> width = field.value as? Int
        "mHeight" -> height = field.value as? Int
        "mNativePtr" -> nativePtr = field.value as? Long
        "mBuffer" -> bufferInstance = field.getAsInstance()
      }
    }

    if (width == null || height == null) return null

    val bufferHash: Int
    if (bufferInstance != null) {
      val byteArray = getByteArrayFromInstanceObject(bufferInstance) ?: return null
      bufferHash = byteArray.contentHashCode()
    } else if (nativePtr != null && nativePtr != 0L) {
      bufferHash = ptrToBufferMap[nativePtr] ?: return null
    } else {
      return null
    }

    return BitmapInfo(height, width, bufferHash)
  }

  private fun buildPtrToBufferMap(dumpDataInstances: Iterable<InstanceObject>, captureObject: CaptureObject?): Map<Long, Int> {
    val acc = mutableMapOf<Long, Int>()

    dumpDataInstances.chunked(50).forEach { chunk ->
      val bufferInstancesToFetch = mutableMapOf<Long, TraceProcessorHeapDumpInstanceObject>()
      val dumpDataMap = mutableMapOf<Long, TraceProcessorHeapDumpInstanceObject>()

      chunk.forEach { dumpDataInstance ->
        val dumpDataTp = dumpDataInstance as? TraceProcessorHeapDumpInstanceObject
        if (dumpDataTp != null) dumpDataMap[dumpDataTp.instanceId] = dumpDataTp
      }

      if (captureObject is HeapDumpCaptureObject && dumpDataMap.isNotEmpty()) {
        val idChunk = dumpDataMap.keys.toList()
        val result = captureObject.getPrimitiveFieldsBulk(idChunk)
        result.instancesList.forEach { protoInst -> dumpDataMap[protoInst.instanceId]?.preloadedPrimitiveFields = protoInst }
      }

      val nestedArrays = mutableListOf<TraceProcessorHeapDumpInstanceObject>()
      chunk.forEach { dumpDataInstance ->
        val buffersInstance = getNestedInstanceObject(dumpDataInstance, "buffers") as? TraceProcessorHeapDumpInstanceObject
        val nativesInstance = getNestedInstanceObject(dumpDataInstance, "natives") as? TraceProcessorHeapDumpInstanceObject
        if (buffersInstance != null) nestedArrays.add(buffersInstance)
        if (nativesInstance != null) nestedArrays.add(nativesInstance)
      }

      if (captureObject is HeapDumpCaptureObject && nestedArrays.isNotEmpty()) {
        captureObject.prefetchReferences(nestedArrays)
      }

      chunk.forEach { dumpDataInstance ->
        val buffersInstance = getNestedInstanceObject(dumpDataInstance, "buffers")
        val nativesInstance = getNestedInstanceObject(dumpDataInstance, "natives")
        if (buffersInstance != null && nativesInstance != null) {
          val buffersFields = buffersInstance.fields
          val buffersFieldsMap = buffersFields.associateBy { it.fieldName }
          for (i in buffersFields.indices) {
            val fieldKeyBracket = "[$i]"
            val fieldKeyString = i.toString()
            val bufferInstance = (buffersFieldsMap[fieldKeyBracket] ?: buffersFieldsMap[fieldKeyString])?.getAsInstance()
            val tpBuffer = bufferInstance as? TraceProcessorHeapDumpInstanceObject
            if (tpBuffer != null) {
              bufferInstancesToFetch[tpBuffer.instanceId] = tpBuffer
            }
          }
        }
      }

      dumpDataMap.values.forEach { it.preloadedPrimitiveFields = null }

      if (captureObject is HeapDumpCaptureObject && bufferInstancesToFetch.isNotEmpty()) {
        prefetchPrimitiveFieldsBulk(captureObject, bufferInstancesToFetch)
      }

      try {
        chunk.forEach { dumpDataInstance ->
          val buffersInstance = getNestedInstanceObject(dumpDataInstance, "buffers")
          val nativesInstance = getNestedInstanceObject(dumpDataInstance, "natives")
          if (buffersInstance != null && nativesInstance != null) {
            val buffersFields = buffersInstance.fields
            val nativesFields = nativesInstance.fields
            val buffersFieldsMap = buffersFields.associateBy { it.fieldName }
            val nativesFieldsMap = nativesFields.associateBy { it.fieldName }
            for (i in nativesFields.indices) {
              val fieldKeyBracket = "[$i]"
              val fieldKeyString = i.toString()
              val nativePtr = (nativesFieldsMap[fieldKeyBracket] ?: nativesFieldsMap[fieldKeyString])?.value as? Long ?: continue
              val bufferInstance = (buffersFieldsMap[fieldKeyBracket] ?: buffersFieldsMap[fieldKeyString])?.getAsInstance() ?: continue
              val byteArray = getByteArrayFromInstanceObject(bufferInstance) ?: continue
              acc[nativePtr] = byteArray.contentHashCode()
            }
          }
        }
      } finally {
        bufferInstancesToFetch.values.forEach { it.preloadedPrimitiveFields = null }
      }
    }

    return acc
  }

  private fun getByteArrayFromInstanceObject(instance: InstanceObject): ByteArray? {
    val arrayObject =
      instance.arrayObject
        ?: run {
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
    return parentInstance.fields.firstOrNull { field -> fieldName == field.fieldName && field.value is InstanceObject }?.getAsInstance()
  }
}
