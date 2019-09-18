/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof

import com.android.tools.idea.diagnostics.hprof.parser.ConstantPoolEntry
import com.android.tools.idea.diagnostics.hprof.parser.InstanceFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.StaticFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.Type
import com.android.tools.idea.diagnostics.hprof.util.HprofWriter
import com.android.tools.idea.experimental.codeanalysis.datastructs.Modifier
import gnu.trove.TLongIntHashMap
import gnu.trove.TObjectHashingStrategy
import gnu.trove.TObjectLongHashMap
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.Boolean
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Short
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.lang.reflect.Array

class HProfBuilder(dos: DataOutputStream, val classNameMapping: ((Class<*>) -> String?)? = null) {

  private val objectToIdMap = TObjectLongHashMap<Any>(TObjectHashingStrategy.IDENTITY)
  private val stringToIdMap = TObjectLongHashMap<String>()
  private val classObjectIdToClassSerialNumber = TLongIntHashMap()

  private var nextStringId = 1L
  private var nextObjectId = 1L
  private var nextStackFrameId = 1L
  private var nextStackTraceSerialNumberId = 1
  private var nextClassSerialNumber = 1

  private val idSize = 8

  private val writer = HprofWriter(dos, idSize, System.currentTimeMillis())

  init {
    addObject(Class::class.java)
    addObject(SoftReference::class.java)
    addObject(WeakReference::class.java)
  }

  fun addRootGlobalJNI(o: Any) {
    val id = addObject(o)
    writer.writeRootGlobalJNI(id, 0)
  }

  fun addRootUnknown(o: Any) {
    val id = addObject(o)
    writer.writeRootUnknown(id)
  }

  fun addRootJavaFrame(o: Any, threadSerialNumber: Int, frameIndex: Int) {
    val id = addObject(o)
    writer.writeRootJavaFrame(id, threadSerialNumber, frameIndex)
  }

  fun addStackTrace(thread: Thread, topFramesCount: Int): Int {
    val stackTrace = thread.stackTrace
    val stackFrameIds = LongArray(kotlin.math.min(topFramesCount, stackTrace.size))
    for (i in stackFrameIds.indices) {
      val ste = stackTrace[i]
      val stackFrameId = nextStackFrameID()
      stackFrameIds[i] = stackFrameId
      val classObjectId = addObject(Class.forName(ste.className))
      val classSerialNumber = getClassSerialNumber(classObjectId)
      writer.writeStackFrame(stackFrameId,
                             addString(ste.className + "." + ste.methodName),
                             addString("()"),
                             0,
                             classSerialNumber,
                             if (ste.isNativeMethod) -1 else ste.lineNumber)
    }
    val stackTraceSerialNumber = nextStackTraceSerialNumberID()
    writer.writeStackTrace(stackTraceSerialNumber, thread.id, stackFrameIds)
    return stackTraceSerialNumber
  }

  fun addObject(o: Any?): Long {
    if (o == null) {
      return 0
    }
    if (objectToIdMap.containsKey(o)) {
      return objectToIdMap[o]
    }
    val objectID = nextObjectID()
    objectToIdMap.put(o, objectID)

    val oClass: Class<*> = o.javaClass
    addObject(oClass)

    when {
      o is Class<*> -> addClass(objectID, o)
      oClass.isArray && oClass.componentType.isPrimitive -> addPrimitiveArray(objectID, o)
      oClass.isArray -> addObjectArray(objectID, o)
      else -> addInstanceObject(objectID, o)
    }
    return objectID
  }

  private fun addPrimitiveArray(id: Long, o: Any) {
    // Contents of primitive arrays are not yet supported
    val elementType = Type.getType(o.javaClass.name)
    val elementSize = elementType.size

    val arraySize = Array.getLength(o)
    val bytes = ByteArray(arraySize * elementSize)
    var curr = 0
    for (i in 0 until arraySize) {
      val value =
        when (elementType) {
          Type.BYTE -> Array.getByte(o, i).toLong()
          Type.SHORT -> Array.getShort(o, i).toLong()
          Type.INT -> Array.getInt(o, i).toLong()
          Type.OBJECT -> throw IllegalStateException()
          Type.BOOLEAN -> if (Array.getBoolean(o, i)) 1L else 0L
          Type.CHAR -> Array.getChar(o, i).toLong()
          Type.FLOAT -> Float.floatToRawIntBits(Array.getFloat(o, i)).toLong()
          Type.DOUBLE -> Double.doubleToRawLongBits(Array.getDouble(o, i))
          Type.LONG -> Array.getLong(o, i)
          else -> throw IllegalStateException()
        }
      serializeToBytes(bytes, curr, value, elementType.size)
      curr += elementType.size
    }

    writer.writePrimitiveArrayDump(id,
                                   0,
                                   elementType,
                                   bytes,
                                   arraySize)
  }

  private fun serializeToBytes(bytes: ByteArray, offset: Int, value: Long, count: Int): Int {
    var result = offset
    for (i in count - 1 downTo 0) {
      bytes[result++] = ((value shl (i * 8)) and 0xff).toByte()
    }
    return result
  }

  private fun addObjectArray(id: Long, o: Any) {
    val arrayClassObjectId = addObject(o.javaClass)
    val length = Array.getLength(o)
    val elements = LongArray(length)

    for (i in 0 until length) {
      val element = Array.get(o, i)
      val elementID = addObject(element)
      elements[i] = elementID
    }

    writer.writeObjectArrayDump(id,
                                0,
                                arrayClassObjectId,
                                elements)
  }

  private fun addInstanceObject(id: Long, o: Any) {
    var oClass: Class<*> = o.javaClass
    val classObjectId = addObject(oClass)
    val baos = ByteArrayOutputStream()
    DataOutputStream(baos).use { dos ->
      do {
        oClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.forEach { field ->
          field.isAccessible = true
          when (field.type) {
            java.lang.Long.TYPE -> dos.writeLong(field.getLong(o))
            Integer.TYPE -> dos.writeInt(field.getInt(o))
            Short.TYPE -> dos.writeShort(field.getShort(o).toInt())
            Character.TYPE -> dos.writeChar(field.getChar(o).toInt())
            Byte.TYPE -> dos.writeByte(field.getByte(o).toInt())
            Boolean.TYPE -> dos.writeBoolean(field.getBoolean(o))
            Double.TYPE -> dos.writeDouble(field.getDouble(o))
            Float.TYPE -> dos.writeFloat(field.getFloat(o))
            else -> {
              val refObject = field.get(o)
              val refObjectId = addObject(refObject)
              when (idSize) {
                8 -> dos.writeLong(refObjectId)
                4 -> dos.writeInt(refObjectId.toInt())
                else -> throw IllegalArgumentException()
              }
            }
          }
        }
        oClass = oClass.superclass ?: break
      }
      while (true)
    }
    val bytes = baos.toByteArray()
    writer.writeInstanceDump(id, 0, classObjectId, bytes)
  }

  private fun addClass(id: Long, oClass: Class<*>) {
    val superClassObjectId = addObject(oClass.superclass)

    val mappedClassName = classNameMapping?.let { it(oClass) } ?: oClass.name
    val className = mappedClassName.replace('.', '/')
    val classNameStringId = addString(className)

    objectToIdMap.put(oClass, id)

    val classSerialNumber = nextClassSerialNumber()
    classObjectIdToClassSerialNumber.put(id, classSerialNumber)
    writer.writeLoadClass(classSerialNumber, id, 0, classNameStringId)

    var instanceSize = 0
    val instanceFields = mutableListOf<InstanceFieldEntry>()
    oClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.forEach { field ->
      field.isAccessible = true
      when (field.type) {
        java.lang.Long.TYPE -> instanceSize += 8
        Integer.TYPE -> instanceSize += 4
        Short.TYPE -> instanceSize += 2
        Character.TYPE -> instanceSize += 2
        Byte.TYPE -> instanceSize += 1
        Boolean.TYPE -> instanceSize += 1
        Double.TYPE -> instanceSize += 8
        Float.TYPE -> instanceSize += 4
        else -> instanceSize += idSize
      }
      when (field.type) {
        java.lang.Long.TYPE -> instanceFields.add(InstanceFieldEntry(addString(field.name), Type.LONG))
        Integer.TYPE -> instanceFields.add(InstanceFieldEntry(addString(field.name), Type.INT))
        Short.TYPE -> instanceFields.add(InstanceFieldEntry(addString(field.name), Type.SHORT))
        Character.TYPE -> instanceFields.add(InstanceFieldEntry(addString(field.name), Type.CHAR))
        Byte.TYPE -> instanceFields.add(InstanceFieldEntry(addString(field.name), Type.BYTE))
        Boolean.TYPE -> instanceFields.add(InstanceFieldEntry(addString(field.name), Type.BOOLEAN))
        Double.TYPE -> instanceFields.add(InstanceFieldEntry(addString(field.name), Type.DOUBLE))
        Float.TYPE -> instanceFields.add(InstanceFieldEntry(addString(field.name), Type.FLOAT))
        else -> instanceFields.add(InstanceFieldEntry(addString(field.name), Type.OBJECT))
      }
    }

    // Constants and static fields not supported yet.
    val constantPool = arrayOf<ConstantPoolEntry>()
    val staticFields = arrayOf<StaticFieldEntry>()
    writer.writeClassDump(id, 0, superClassObjectId, 0, 0, 0,
                          instanceSize,
                          constantPool,
                          staticFields,
                          instanceFields.toTypedArray())
  }

  private fun nextObjectID() = nextObjectId++
  private fun nextStackFrameID() = nextStackFrameId++
  private fun nextStackTraceSerialNumberID() = nextStackTraceSerialNumberId++
  private fun nextClassSerialNumber() = nextClassSerialNumber++

  private fun addString(string: String): Long {
    if (stringToIdMap.contains(string)) return stringToIdMap[string]
    val id = nextStringID()
    writer.writeStringInUTF8(id, string)
    return id
  }

  private fun getClassSerialNumber(classObjectId: Long) = classObjectIdToClassSerialNumber[classObjectId]

  private fun nextStringID() = nextStringId++

  fun create() {
    writer.flushHeapObjects()
  }
}
