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
package com.android.tools.profilers.memory.adapters

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import java.nio.ByteOrder
import java.util.AbstractList
import java.util.Locale

/** Represents an individual heap object instance fetched lazily on demand from Perfetto Trace Processor Daemon. */
class TraceProcessorHeapDumpInstanceObject(
  private val classEntry: ClassDb.ClassEntry,
  private val instance: TraceProcessor.HeapDumpInstancesResult.InstanceData,
  private val valueType: ValueObject.ValueType = ValueObject.ValueType.OBJECT,
  private val captureObject: HeapDumpCaptureObject,
  private val reverseReferences: List<TraceProcessor.GetReferencesResult.ReferenceData> = emptyList(),
  internal val forwardReferences: List<TraceProcessor.GetReferencesResult.ReferenceData> = emptyList(),
) : InstanceObject {
  override fun getHeapId() = instance.heapName.standardHeapId()

  override fun getClassEntry() = classEntry

  override fun getName(): String {
    return ""
  }

  override fun getValueText(): String {
    val name =
      captureObject.getRepresentedClassId(instance.id)?.let { representedClassId ->
        "${captureObject.classDatabase.getEntry(representedClassId).simpleClassName}.class"
      } ?: classEntry.simpleClassName
    return String.format(Locale.US, "%s@%d (0x%x)", name, instance.id, instance.id)
  }

  override fun getToStringText() =
    when (valueType) {
      ValueObject.ValueType.STRING -> if (!instance.stringValue.isEmpty) "\"${instance.stringValue.toStringUtf8()}\"" else ""
      else -> ""
    }

  override fun getDepth() = instance.depth.toInt()

  override fun getNativeSize() = instance.nativeSize

  override fun getShallowSize() = instance.selfSize.toInt()

  override fun getRetainedNativeSize() = instance.retainedNativeSize

  override fun getRetainedSize() = instance.retainedSize

  override fun getValueType(): ValueObject.ValueType {
    if (captureObject.getRepresentedClassId(instance.id) != null) {
      return ValueObject.ValueType.CLASS
    }
    return valueType
  }

  internal var fetchedForwardReferences = false
  internal var fetchedReverseReferences = false
  internal val fetchedReferences: Boolean
    get() = fetchedForwardReferences && fetchedReverseReferences

  private val reverseReferencesList = mutableListOf<TraceProcessor.GetReferencesResult.ReferenceData>()
  private val forwardReferencesList = mutableListOf<TraceProcessor.GetReferencesResult.ReferenceData>()

  internal fun setReferences(
    forward: List<TraceProcessor.GetReferencesResult.ReferenceData>,
    reverse: List<TraceProcessor.GetReferencesResult.ReferenceData>,
  ) {
    forwardReferencesList.clear()
    forwardReferencesList.addAll(forward)
    reverseReferencesList.clear()
    reverseReferencesList.addAll(reverse)
    fetchedForwardReferences = true
    fetchedReverseReferences = true
  }

  internal fun setReverseReferences(reverse: List<TraceProcessor.GetReferencesResult.ReferenceData>) {
    reverseReferencesList.clear()
    reverseReferencesList.addAll(reverse)
    fetchedReverseReferences = true
  }

  private fun ensureForwardReferencesFetched() {
    if (fetchedForwardReferences) return
    fetchedForwardReferences = true

    if (forwardReferences.isNotEmpty()) {
      forwardReferences.forEach { ref ->
        forwardReferencesList.add(
          TraceProcessor.GetReferencesResult.ReferenceData.newBuilder()
            .setOwnerId(ref.ownerId)
            .setOwnedId(ref.ownedId)
            .setFieldName(ref.fieldName)
            .build()
        )
      }
      return
    }

    val result = captureObject.getReferencesBulk(listOf(instance.id), fetchForward = true, fetchReverse = false)

    result.referenceList.forEach { ref ->
      if (ref.ownerId == instance.id) {
        forwardReferencesList.add(ref)
      }
    }
  }

  private fun ensureReverseReferencesFetched() {
    if (fetchedReverseReferences) return
    fetchedReverseReferences = true

    if (reverseReferences.isNotEmpty()) {
      reverseReferences.forEach { ref ->
        reverseReferencesList.add(
          TraceProcessor.GetReferencesResult.ReferenceData.newBuilder()
            .setOwnerId(ref.ownerId)
            .setOwnedId(ref.ownedId)
            .setFieldName(ref.fieldName)
            .build()
        )
      }
      return
    }

    val result = captureObject.getReferencesBulk(listOf(instance.id), fetchForward = false, fetchReverse = true)

    result.referenceList.forEach { ref ->
      if (ref.ownedId == instance.id) {
        reverseReferencesList.add(ref)
      }
    }
  }

  private fun resolveMissingInstances(missingIds: List<Long>) {
    if (missingIds.isEmpty()) return
    val fetchedInstances = captureObject.getInstancesByIds(missingIds)
    fetchedInstances.forEach { inst ->
      if (captureObject.classDatabase.hasEntry(inst.typeId)) {
        val cls = captureObject.classDatabase.getEntry(inst.typeId)
        captureObject.getOrCreateTraceProcessorHeapDumpInstance(cls, inst)
      } else {
        val representedId = captureObject.syntheticToRepresentedClassMap[inst.typeId]
        if (representedId != null) {
          val representedEntry = captureObject.classDatabase.getEntry(representedId)
          captureObject.getOrCreateTraceProcessorHeapDumpInstance(representedEntry, inst)
        }
      }
    }
  }

  override fun getReferences(): List<ReferenceObject> {
    if (isRoot) return listOf()
    ensureReverseReferencesFetched()

    return object : AbstractList<ReferenceObject>() {
      private val groups by lazy { reverseReferencesList.groupBy { it.ownerId }.values.toList() }
      private val resolvedList: List<ReferenceObject> by lazy {
        val missingIds = groups.map { it.first().ownerId }.filter { captureObject.findInstanceObjectByIdCached(it) == null }
        resolveMissingInstances(missingIds)

        val prefetchedInstances = mutableListOf<TraceProcessorHeapDumpInstanceObject>()
        groups.forEach { refs ->
          (captureObject.findInstanceObjectByIdCached(refs.first().ownerId) as? TraceProcessorHeapDumpInstanceObject)?.let {
            prefetchedInstances.add(it)
          }
        }
        if (prefetchedInstances.isNotEmpty()) {
          captureObject.prefetchReverseReferences(prefetchedInstances)
        }

        groups.map { refs ->
          val ownerInst = captureObject.findInstanceObjectByIdCached(refs.first().ownerId)
          if (ownerInst != null) {
            val fieldNames = refs.map { ref ->
              if (ownerInst.valueType == ValueObject.ValueType.ARRAY) {
                ref.fieldName.substringAfter("[").substringBefore("]")
              } else {
                ref.fieldName.substringAfterLast('.')
              }
            }
            ReferenceObject(fieldNames, ownerInst)
          } else {
            val placeholderEntry =
              if (captureObject.classDatabase.hasEntry(0)) captureObject.classDatabase.getEntry(0)
              else captureObject.classDatabase.registerClass(0, "Unknown")
            val placeholderInst = TraceProcessorHeapDumpInstance(captureObject, placeholderEntry, false, 0, 0)
            ReferenceObject(refs.map { it.fieldName }, placeholderInst)
          }
        }
      }

      override val size: Int
        get() = groups.size

      override fun get(index: Int): ReferenceObject {
        return resolvedList[index]
      }
    }
  }

  @Volatile var fetchedFieldsList: List<FieldObject>? = null
  @Volatile var preloadedPrimitiveFields: TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields? = null

  val instanceId: Long
    get() = instance.id

  override fun getFieldCount(): Int {
    fetchedFieldsList?.let {
      return it.size
    }
    var count = 0
    if (instance.hasReferences) count += 1
    if (instance.hasPrimitiveFields) count += 1
    if (valueType == ValueObject.ValueType.ARRAY && instance.arrayLength > 0) count += 1
    return count
  }

  private inner class PrimitiveFieldObject(
    private val fieldName: String,
    private val valType: ValueObject.ValueType,
    private val valText: String,
    private val size: Int,
    private val rawValue: Any? = null,
  ) : FieldObject {
    override fun getFieldName() = fieldName

    override fun getAsInstance() = null

    override fun getValue() = rawValue

    override fun getName() = fieldName

    override fun getValueType() = valType

    override fun getValueText() = valText

    override fun getToStringText() = ""

    override fun getDepth() = this@TraceProcessorHeapDumpInstanceObject.depth

    override fun getNativeSize() = 0L

    override fun getShallowSize() = size

    override fun getRetainedSize() = size.toLong()

    override fun getRetainedNativeSize() = 0L
  }

  private fun addPlaceholderArrayFields(fields: MutableList<FieldObject>) {
    val typeName = classEntry.className.substringBeforeLast("[]")
    val valType = TraceProcessorMemoryUtil.getValueType(typeName)
    val size = TraceProcessorMemoryUtil.getSize(typeName)
    val defaultValue = TraceProcessorMemoryUtil.getDefaultValue(typeName)
    val rawValue = TraceProcessorMemoryUtil.parseRawValue(typeName, defaultValue)
    for (i in 0 until instance.arrayLength) {
      fields.add(PrimitiveFieldObject("$i", valType, defaultValue, size, rawValue))
    }
  }

  override fun getFields(): List<FieldObject> {
    fetchedFieldsList?.let {
      return it
    }
    ensureForwardReferencesFetched()
    val fields = mutableListOf<FieldObject>()

    val isARTHeapDump = captureObject.isARTHeapDump

    val primitiveFieldsResult =
      if (preloadedPrimitiveFields == null && isARTHeapDump) captureObject.getPrimitiveFields(instance.id) else null
    val instResult = preloadedPrimitiveFields ?: primitiveFieldsResult?.instancesList?.firstOrNull()

    if (instResult != null) {
      instResult.fieldList.forEach { primitiveField ->
        val simpleName = primitiveField.name.substringAfterLast(".")
        val typeName = primitiveField.typeName
        val valType = TraceProcessorMemoryUtil.getValueType(typeName)
        val valText = TraceProcessorMemoryUtil.getPrimitiveFieldText(typeName, primitiveField.value)
        val size = TraceProcessorMemoryUtil.getSize(typeName)
        val rawValue = TraceProcessorMemoryUtil.parseRawValue(typeName, primitiveField.value)
        fields.add(PrimitiveFieldObject(simpleName, valType, valText, size, rawValue))
      }

      if (!instResult.arrayBlob.isEmpty) {
        val buffer = instResult.arrayBlob.asReadOnlyByteBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val typeName = instResult.arrayType
        val valType = TraceProcessorMemoryUtil.getValueType(typeName)
        val size = TraceProcessorMemoryUtil.getSize(typeName)
        var i = 0
        while (buffer.remaining() >= size) {
          val valueStr = TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer(typeName, buffer)
          val rawValue = TraceProcessorMemoryUtil.parseRawValue(typeName, valueStr)
          fields.add(PrimitiveFieldObject("$i", valType, valueStr, size, rawValue))
          i++
        }
      } else if (valueType == ValueObject.ValueType.ARRAY && instance.arrayLength > 0 && forwardReferencesList.isEmpty()) {
        addPlaceholderArrayFields(fields)
      }
    } else if (valueType == ValueObject.ValueType.ARRAY && instance.arrayLength > 0 && forwardReferencesList.isEmpty()) {
      addPlaceholderArrayFields(fields)
    }

    val refsToProcess =
      if (valueType == ValueObject.ValueType.ARRAY) {
        forwardReferencesList.sortedBy { ref -> ref.fieldName.substringAfter("[").substringBefore("]").toIntOrNull() ?: 0 }
      } else {
        forwardReferencesList
      }

    val missingIds = refsToProcess.map { it.ownedId }.filter { captureObject.findInstanceObjectByIdCached(it) == null }
    resolveMissingInstances(missingIds)

    refsToProcess.forEach { ref ->
      val targetInstance = captureObject.findInstanceObjectById(ref.ownedId)
      val simpleName =
        if (valueType == ValueObject.ValueType.ARRAY) {
          ref.fieldName.substringAfter("[").substringBefore("]")
        } else {
          ref.fieldName.substringAfterLast(".")
        }

      if (targetInstance != null) {
        fields.add(
          object : FieldObject {
            override fun getFieldName() = simpleName

            override fun getAsInstance() = targetInstance

            override fun getValue() = targetInstance

            override fun getName() = simpleName

            override fun getValueType() = targetInstance.valueType

            override fun getValueText() = "{${targetInstance.classEntry.simpleClassName}}"

            override fun getToStringText() = targetInstance.toStringText

            override fun getDepth() = targetInstance.depth

            override fun getNativeSize() = targetInstance.nativeSize

            override fun getShallowSize() = targetInstance.shallowSize

            override fun getRetainedSize() = targetInstance.retainedSize

            override fun getRetainedNativeSize() = targetInstance.retainedNativeSize
          }
        )
      } else {
        // Fallback for unresolved references to ensure fields.size matches getFieldCount().
        fields.add(PrimitiveFieldObject(simpleName, ValueObject.ValueType.OBJECT, "null", 0, null))
      }
    }

    fetchedFieldsList = fields
    return fields
  }

  override fun getArrayObject(): ArrayObject? {
    if (valueType == ValueObject.ValueType.ARRAY && TraceProcessorMemoryUtil.isPrimitiveArray(classEntry.className)) {
      val primitiveFieldsResult = if (preloadedPrimitiveFields == null) captureObject.getPrimitiveFields(instance.id) else null
      val instResult = preloadedPrimitiveFields ?: primitiveFieldsResult?.instancesList?.firstOrNull() ?: return null
      if (instResult.arrayBlob.isEmpty) return null

      return object : ArrayObject {
        override fun getArrayElementType(): ValueObject.ValueType {
          // If it's a primitive array, valueType is ARRAY, but we need to return the element type
          val typeName = instResult.arrayType
          return TraceProcessorMemoryUtil.getValueType(typeName)
        }

        override fun getArrayLength(): Int = instance.arrayLength.toInt()

        override fun getAsByteArray(): ByteArray {
          val buffer = instResult.arrayBlob.asReadOnlyByteBuffer()
          // HPROF data in TraceProcessor primitive array blob is Big Endian
          buffer.order(ByteOrder.BIG_ENDIAN)
          val bytes = ByteArray(buffer.remaining())
          buffer.get(bytes)
          return bytes
        }

        override fun getAsCharArray(): CharArray {
          val buffer = instResult.arrayBlob.asReadOnlyByteBuffer()
          buffer.order(ByteOrder.BIG_ENDIAN)
          val chars = CharArray(buffer.remaining() / 2)
          buffer.asCharBuffer().get(chars)
          return chars
        }

        override fun getAsArray(): Array<Any>? = null
      }
    }
    return null
  }
}
