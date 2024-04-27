/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.perflib.heap.ClassInstance
import java.lang.StringBuilder
import com.android.tools.perflib.heap.ArrayInstance
import com.android.tools.perflib.heap.ClassObj
import com.android.tools.perflib.heap.Field
import com.android.tools.perflib.heap.Instance
import com.android.tools.profiler.proto.Memory.AllocationStack
import com.android.tools.profiler.proto.Memory.AllocationStack.StackFrameWrapper
import com.android.tools.perflib.heap.RootObj
import com.android.tools.perflib.heap.Type
import com.google.common.annotations.VisibleForTesting
import java.util.Locale

/**
 * A UI representation of a [ClassInstance].
 */
internal class HeapDumpInstanceObject(private val captureObject: HeapDumpCaptureObject,
                                      private val instance: Instance,
                                      private val classEntry: ClassDb.ClassEntry,
                                      precomputedValueType: ValueObject.ValueType?) : InstanceObject {
  private val valueType = when {
    precomputedValueType != null -> precomputedValueType
    else -> instance.classObj.let { classObj ->
      when {
        instance is ClassObj -> ValueObject.ValueType.CLASS
        instance is ClassInstance && classObj!!.className == ClassDb.JAVA_LANG_STRING -> ValueObject.ValueType.STRING
        classObj!!.className.endsWith("[]") -> ValueObject.ValueType.ARRAY
        else -> ValueObject.ValueType.OBJECT
      }
    }
  }

  override fun equals(other: Any?) = other is HeapDumpInstanceObject && instance === other.instance
  override fun hashCode() = System.identityHashCode(instance) // cheap hashcode implementation in sync with `equals` as defined above

  override fun getName() = ""

  // TODO show length of array instance
  override fun getValueText() = String.format(Locale.US, NAME_FORMATTER, classEntry.simpleClassName, instance.uniqueId, instance.uniqueId)

  override fun getToStringText() = when (valueType) {
    ValueObject.ValueType.STRING -> when (val text = (instance as ClassInstance).getAsString(MAX_VALUE_TEXT_LENGTH)) {
      null -> INVALID_STRING_VALUE
      else -> {
        val content = when (val textLength = text.length) {
          MAX_VALUE_TEXT_LENGTH -> "${text.substring(0, textLength - 1)}..."
          else -> text
        }
        "\"$content\""
      }
    }
    else -> ""
  }

  override fun getHeapId() = instance.heap!!.id
  override fun getClassEntry() = classEntry
  override fun getDepth() = instance.distanceToGcRoot
  override fun getNativeSize() = instance.nativeSize
  override fun getShallowSize() = instance.size
  override fun getRetainedSize() = instance.totalRetainedSize

  override fun getFieldCount() = when (instance) {
    is ClassInstance -> instance.values.size
    is ArrayInstance -> instance.length
    is ClassObj -> instance.staticFieldValues.size
    else -> 0
  }

  override fun getFields(): List<FieldObject> = when (instance) {
    is ClassInstance -> instance.values.map { HeapDumpFieldObject(captureObject, instance, it) }
    is ArrayInstance -> instance.arrayType.let { type ->
      instance.values.mapIndexed { index, value ->
        HeapDumpFieldObject(captureObject, instance, ClassInstance.FieldValue(Field(type, index.toString()), value))
      }
    }
    is ClassObj -> instance.staticFieldValues.map { (key, value) ->
      HeapDumpFieldObject(captureObject, instance, ClassInstance.FieldValue(key, value))
    }
    else -> listOf()
  }

  override fun getArrayObject() = when (instance) {
    is ArrayInstance -> object : ArrayObject {
      override fun getArrayElementType() = VALUE_TYPE_MAP[instance.arrayType]!!
      override fun getAsByteArray() = when (arrayElementType) {
        ValueObject.ValueType.BYTE -> instance.asRawByteArray(0, instance.length)
        else -> null
      }
      override fun getAsCharArray() = when (arrayElementType) {
        ValueObject.ValueType.CHAR -> instance.asCharArray(0, instance.length)
        else -> null
      }
      override fun getAsArray() = instance.values
      override fun getArrayLength() = instance.length
    }
    else -> null
  }

  override fun getValueType() = valueType

  override fun getAllocationCallStack() = when (val st = instance.stack) {
    null -> null
    else -> {
      val builder = AllocationStack.newBuilder()
      val frameBuilder = StackFrameWrapper.newBuilder()
      for (stackFrame in st.frames) {
        val fileName = stackFrame.filename
        val guessedClassName = if (fileName.endsWith(".java")) fileName.substring(0, fileName.length - ".java".length) else fileName
        frameBuilder.addFrames(AllocationStack.StackFrame.newBuilder()
                                 .setClassName(guessedClassName)
                                 .setMethodName(stackFrame.methodName)
                                 .setLineNumber(stackFrame.lineNumber)
                                 .setFileName(fileName)
                                 .build())
      }
      builder.setFullStack(frameBuilder)
      builder.build()
    }
  }

  override fun isCallStackEmpty() = instance.stack == null || instance.stack!!.frames.isEmpty()
  override fun getIsRoot() = instance is RootObj
  override fun getReferences() = if (isRoot) listOf() else extractReferences()

  @VisibleForTesting
  fun extractReferences(): List<ReferenceObject> {
    val order = compareBy(Instance::distanceToGcRoot, Instance::id) // to enforce more deterministic order
    // Hard referrers first, soft second
    val sortedReferences = instance.hardReverseReferences.sortedWith(order) +
                           instance.softReverseReferences.sortedWith(order)
    return sortedReferences.map { reference ->
      // Note that each instance can have multiple references to the same object.
      val referencingFieldNames = when (reference) {
        is ClassInstance -> reference.values.mapNotNull { ref ->
          ref.field.name.takeIf { ref.field.type == Type.OBJECT && ref.value === instance }
        }
        is ArrayInstance -> {
          assert(reference.arrayType == Type.OBJECT)
          reference.values.withIndex().mapNotNull { ref ->
            ref.index.toString().takeIf { ref.value === instance }
          }
        }
        is ClassObj -> reference.staticFieldValues.mapNotNull { (key, value) ->
          key.name.takeIf { key.type == Type.OBJECT && value === instance }
        }
        else -> listOf()
      }
      ReferenceObject(referencingFieldNames, captureObject.findInstanceObject(reference)!!)
    }.toList()
  }

  companion object {
    private const val NAME_FORMATTER = "%s@%d (0x%x)"
    private const val MAX_VALUE_TEXT_LENGTH = 1024
    private const val INVALID_STRING_VALUE = " ...<invalid string value>..."
    private val VALUE_TYPE_MAP = mapOf(
      Type.BOOLEAN to ValueObject.ValueType.BOOLEAN,
      Type.BYTE to ValueObject.ValueType.BYTE,
      Type.CHAR to ValueObject.ValueType.CHAR,
      Type.SHORT to ValueObject.ValueType.SHORT,
      Type.INT to ValueObject.ValueType.INT,
      Type.LONG to ValueObject.ValueType.LONG,
      Type.FLOAT to ValueObject.ValueType.FLOAT,
      Type.DOUBLE to ValueObject.ValueType.DOUBLE,
      Type.OBJECT to ValueObject.ValueType.OBJECT,
    )
  }
}
