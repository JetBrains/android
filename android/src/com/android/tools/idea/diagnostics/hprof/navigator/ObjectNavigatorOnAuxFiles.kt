/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof.navigator

import com.android.tools.idea.diagnostics.hprof.classstore.ClassDefinition
import com.android.tools.idea.diagnostics.hprof.classstore.ClassStore
import com.android.tools.idea.diagnostics.hprof.parser.Type
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import java.nio.ByteBuffer
import kotlin.experimental.and

class ObjectNavigatorOnAuxFiles(
  private val roots: Long2ObjectOpenHashMap<RootReason>,
  private val auxOffsets: ByteBuffer,
  private val aux: ByteBuffer,
  classStore: ClassStore,
  instanceCount: Long,
  private val idSize: Int
) : ObjectNavigator(classStore, instanceCount) {

  override fun getClass() = currentClass!!

  override fun getClassForObjectId(id: Long): ClassDefinition {
    auxOffsets.position((id * 4).toInt())
    aux.position(auxOffsets.int)
    val classId = aux.readId()
    return if (classId == 0) classStore.classClass else classStore[classId]
  }

  private var softWeakReferenceIndex: Int = -1
  private var currentObjectId = 0L
  private var arraySize = 0
  private var currentClass: ClassDefinition? = null
  private val references = LongArrayList()
  private var softWeakReferenceId = 0L

  private enum class ReferenceType { Strong, Weak, Soft }

  private var referenceType = ReferenceType.Strong
  private var extraData = 0

  override val id: Long
    get() = currentObjectId

  override fun createRootsIterator(): Iterator<RootObject> {
    return object : Iterator<RootObject> {
      val internalIterator = roots.iterator()
      override fun hasNext(): Boolean {
        return internalIterator.hasNext()
      }

      override fun next(): RootObject {
        val (id, reason) = internalIterator.next()
        return RootObject(id, reason)
      }
    }
  }

  override fun getReferencesCopy(): LongArrayList {
    val result = LongArrayList()
    for (i in 0 until references.count()) {
      result.add(references.getLong(i))
    }
    return result
  }

  override fun isNull(): Boolean {
    return id == 0L
  }

  override fun goTo(id: Long, referenceResolution: ReferenceResolution) {
    auxOffsets.position((id * 4).toInt())
    aux.position(auxOffsets.int)
    currentObjectId = id
    references.clear()
    softWeakReferenceId = 0L
    softWeakReferenceIndex = -1
    referenceType = ReferenceType.Strong
    extraData = 0

    if (id == 0L) {
      currentClass = null
      return
    }
    val classId = aux.readId()
    val classDefinition: ClassDefinition
    if (classId == 0) {
      classDefinition = classStore.classClass
    }
    else {
      classDefinition = classStore[classId]
    }
    currentClass = classDefinition
    if (classId == 0) {
      preloadClass(id.toInt(), referenceResolution)
      return
    }
    if (classDefinition.isPrimitiveArray()) {
      preloadPrimitiveArray()
      return
    }
    if (classDefinition.isArray()) {
      preloadObjectArray(referenceResolution)
      return
    }
    preloadInstance(classDefinition, referenceResolution)
  }

  override fun getExtraData(): Int {
    return extraData
  }

  private fun preloadPrimitiveArray() {
    arraySize = aux.readNonNegativeLEB128Int()
  }

  private fun preloadClass(classId: Int,
                           referenceResolution: ReferenceResolution) {
    arraySize = 0

    if (referenceResolution != ReferenceResolution.NO_REFERENCES) {
      val classDefinition = classStore[classId]
      classDefinition.constantFields.forEach(references::add)
      classDefinition.staticFields.forEach { references.add(it.objectId) }
    }
  }

  private fun preloadObjectArray(referenceResolution: ReferenceResolution) {
    val nullElementsCount = aux.readNonNegativeLEB128Int()
    val nonNullElementsCount = aux.readNonNegativeLEB128Int()

    arraySize = nullElementsCount + nonNullElementsCount

    if (referenceResolution != ReferenceResolution.NO_REFERENCES) {
      for (i in 0 until nonNullElementsCount) {
        references.add(aux.readId().toLong())
      }
    }
  }

  private fun preloadInstance(classDefinition: ClassDefinition,
                              referenceResolution: ReferenceResolution) {
    arraySize = 0

    if (referenceResolution == ReferenceResolution.NO_REFERENCES) {
      return
    }

    var c = classDefinition
    var isSoftReference = false
    var isWeakReference = false
    val includeSoftWeakReferences = referenceResolution == ReferenceResolution.ALL_REFERENCES
    val includeInnerClassRefs = referenceResolution != ReferenceResolution.STRONG_EXCLUDING_INNER_CLASS
    do {
      isSoftReference = isSoftReference || classStore.softReferenceClass == c
      isWeakReference = isWeakReference || classStore.weakReferenceClass == c
      val fields = c.refInstanceFields
      fields.forEach {
        val reference = aux.readId()
        if (!(isSoftReference || isWeakReference) || it.name != "referent") {
          if (it.name == "this$0" && !includeInnerClassRefs) {
            references.add(0L)
          } else {
            references.add(reference.toLong())
          }
        }
        else {
          softWeakReferenceId = reference.toLong()
          softWeakReferenceIndex = references.count() // current index in references list
          referenceType = if (isSoftReference) ReferenceType.Soft else ReferenceType.Weak
          // Soft/weak reference
          if (includeSoftWeakReferences) {
            references.add(reference.toLong())
          }
          else {
            references.add(0L)
          }
        }
      }
      val superClassId = c.superClassId
      if (superClassId == 0L) {
        break
      }
      c = classStore[superClassId]
    }
    while (true)

    if (isExtraDataPresent(classDefinition)) {
      preloadExtraData()
    }
  }

  private val directByteBufferClass = classStore.getClassIfExists("java.nio.DirectByteBuffer")
  private val editorImplClass = classStore.getClassIfExists("com.intellij.openapi.editor.impl.EditorImpl")

  private fun isExtraDataPresent(classDefinition: ClassDefinition): Boolean =
    classDefinition == directByteBufferClass || classDefinition == editorImplClass

  private fun preloadExtraData() {
    extraData = aux.readNonNegativeLEB128Int()
  }

  override fun getSoftReferenceId(): Long {
    return if (referenceType == ReferenceType.Soft) softWeakReferenceId else 0
  }

  override fun getWeakReferenceId(): Long {
    return if (referenceType == ReferenceType.Weak) softWeakReferenceId else 0
  }

  override fun getSoftWeakReferenceIndex(): Int {
    return softWeakReferenceIndex
  }

  override fun getObjectSize(): Int {
    val localClass = currentClass ?: return idSize // size of null value

    return when {
      localClass.isPrimitiveArray() ->
        localClass.instanceSize + Type.getType(localClass.name).size * arraySize + ClassDefinition.ARRAY_PREAMBLE_SIZE
      localClass.isArray() -> localClass.instanceSize + idSize * arraySize + ClassDefinition.ARRAY_PREAMBLE_SIZE
      else -> localClass.instanceSize + ClassDefinition.OBJECT_PREAMBLE_SIZE
    }
  }

  override fun copyReferencesTo(outReferences: LongArrayList) {
    outReferences.clear()
    outReferences.ensureCapacity(references.count())
    for (i in 0 until references.count()) {
      outReferences.add(references.getLong(i))
    }
  }

  override fun getRootReasonForObjectId(id: Long): RootReason? {
    var rootReason = roots[id]

    // If the root is a java frame, then first check if any static or constant is a root too. This way,
    // frame root will be reported only is there are no other roots.
    if (rootReason != null && !rootReason.javaFrame) {
      return rootReason
    }
    classStore.forEachClass { classDefinition ->
      if (classDefinition.id == id) {
        rootReason = RootReason.createClassDefinitionReason(classDefinition)
      }
      classDefinition.staticFields.firstOrNull {
        it.objectId == id
      }?.let {
        rootReason = RootReason.createStaticFieldReferenceReason(classDefinition, it.name)
      }
      val index = classDefinition.constantFields.indexOfFirst {
        it == id
      }
      if (index != -1) {
        rootReason = RootReason.createConstantReferenceReason(classDefinition, index)
      }
    }
    return rootReason
  }

  private fun ByteBuffer.readId(): Int {
    return readNonNegativeLEB128Int()
  }

  private fun ByteBuffer.readNonNegativeLEB128Int(): Int {
    var v = 0
    var shift = 0
    while (true) {
      val b = get()
      v = v or ((b and 0x7f).toInt() shl shift)
      if (b >= 0) {
        break
      }
      shift += 7
    }
    return v
  }
}

