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
package com.android.tools.idea.diagnostics.hprof.util

import com.android.tools.idea.diagnostics.hprof.parser.ConstantPoolEntry
import com.android.tools.idea.diagnostics.hprof.parser.HeapDumpRecordType
import com.android.tools.idea.diagnostics.hprof.parser.InstanceFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.RecordType
import com.android.tools.idea.diagnostics.hprof.parser.StaticFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.Type
import gnu.trove.TLongObjectHashMap
import gnu.trove.TObjectLongHashMap
import org.jetbrains.kotlin.idea.core.util.writeString
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutput
import java.io.DataOutputStream

class HprofWriter(
  private val dos: DataOutputStream,
  private val idSize: Int,
  timestamp: Long
) : Closeable {

  init {
    if (idSize != 4 && idSize != 8) {
      throw IllegalArgumentException("idSize can only be 4 or 8")
    }
    dos.writeString("JAVA PROFILE 1.0.1")
    dos.write(idSize)
    dos.writeLong(timestamp)
  }

  private val stringToIdMap = TObjectLongHashMap<String>()
  private val idToStringMap = TLongObjectHashMap<String>()
  private var nextStringId = 1L
  private var inHeapDumpTag = false
  private val subtagsBaos = ByteArrayOutputStream()
  private var subtagsStream = DataOutputStream(subtagsBaos)

  override fun close() {
    dos.close()
    assert(!inHeapDumpTag)
  }

  fun DataOutput.writeNullTerminatedString(s: String) {
    this.write(s.toByteArray())
    this.write(0)
  }

  fun writeStringInUTF8(id: Long, s: String) {
    assert(!inHeapDumpTag)
    val recordType = RecordType.StringInUTF8
    val bytes = s.toByteArray()
    writeRecordHeader(recordType, bytes.size + 4)
    dos.writeId(id)
    dos.write(bytes)
  }

  fun writeLoadClass(serialNumber: Int, classObjectId: Long, stackTraceSerialNumber: Int, className: String) {
    assert(!inHeapDumpTag)
    val classNameId = getOrCreateStringId(className)
    writeRecordHeader(RecordType.LoadClass, 8 + idSize * 2)
    dos.writeInt(serialNumber)
    dos.writeId(classObjectId)
    dos.writeInt(stackTraceSerialNumber)
    dos.writeId(classNameId)
  }

  fun beginHeapDump() {
    assert(!inHeapDumpTag)
    inHeapDumpTag = true
  }

  fun endHeapDump() {
    assert(inHeapDumpTag)
    inHeapDumpTag = false
    writeHeapDumpRecords()
  }

  private fun writeHeapDumpRecords() {
    subtagsStream.close()
    writeRecordHeader(RecordType.HeapDump, subtagsBaos.size())
    subtagsBaos.writeTo(dos)
    subtagsBaos.reset()
    subtagsStream = DataOutputStream(subtagsBaos)
  }

  private fun writeRootUnknown(id: Long) {
    assert(inHeapDumpTag)
    with (subtagsStream) {
      write(HeapDumpRecordType.RootUnknown.value)
      writeId(id)
    }
  }

  fun writeClassDump(
    classObjectId: Long,
    stackTraceSerialNumber: Int,
    superClassObjectId: Long,
    classLoaderObjectId: Long,
    signersObjectId: Long,
    protectionDomainObjectId: Long,
    instanceSize: Int,
    constantPool: Array<ConstantPoolEntry>,
    staticFields: Array<StaticFieldEntry>,
    instanceFields: Array<InstanceFieldEntry>
  ) {
    assert(inHeapDumpTag)
    with(subtagsStream) {
      writeId(classObjectId)
      writeInt(stackTraceSerialNumber)
      writeId(superClassObjectId)
      writeId(classLoaderObjectId)
      writeId(signersObjectId)
      writeId(protectionDomainObjectId)
      writeId(0)
      writeId(0)
      writeInt(instanceSize)
      writeShort(constantPool.size)
      for (entry in constantPool) {
        writeShort(entry.constantPoolIndex)
        writeByte(entry.type.typeId)
        writeValue(entry.value, entry.type)
      }
      writeShort(staticFields.size)
      for (entry in staticFields) {
        writeId(entry.fieldNameStringId)
        writeByte(entry.type.typeId)
        writeValue(entry.value, entry.type)
      }
      writeShort(instanceFields.size)
      for (entry in instanceFields) {
        writeId(entry.fieldNameStringId)
        writeByte(entry.type.typeId)
      }
    }
  }

  fun writeInstanceDump(
    objectId: Long,
    stackTraceSerialNumber: Int,
    classObjectId: Long,
    bytes: ByteArray
  ) {
    assert(inHeapDumpTag)
    with(subtagsStream) {
      writeId(objectId)
      writeInt(stackTraceSerialNumber)
      writeId(classObjectId)
      writeInt(bytes.count())
      write(bytes)
    }
  }

  fun writeObjectArrayDump(
    arrayObjectId: Long,
    stackTraceSerialNumber: Int,
    arrayClassObjectId: Long,
    elementIds: LongArray
  ) {
    assert(inHeapDumpTag)
    with(subtagsStream) {
      writeId(arrayObjectId)
      writeInt(stackTraceSerialNumber)
      writeInt(elementIds.count())
      writeId(arrayClassObjectId)
      elementIds.forEach { id ->
        writeId(id)
      }
    }
  }

  fun writePrimitiveArrayDump(
    arrayObjectId: Long,
    stackTraceSerialNumber: Int,
    elementType: Type,
    elements: ByteArray,
    elementsCount: Int
  ) {
    assert(inHeapDumpTag)
    with(subtagsStream) {
      writeId(arrayObjectId)
      writeInt(stackTraceSerialNumber)
      writeInt(elementsCount)
      assert(elementType != Type.OBJECT)
      writeByte(elementType.typeId)
      assert(elements.size == elementsCount * elementType.size)
      write(elements)
    }
  }

  private fun getOrCreateStringId(s: String): Long {
    val id = stringToIdMap.get(s)
    if (id == 0L) {
      val newId = nextStringId++
      writeStringInUTF8(newId, s)
      idToStringMap.put(newId, s)
      stringToIdMap.put(s, newId)
      return newId
    }
    return id
  }


  private fun writeRecordHeader(recordType: RecordType, length: Int) {
    assert(!inHeapDumpTag)
    with (dos) {
      write(recordType.value)
      writeInt(0) // timestamp, not supported
      writeInt(length)
    }
  }

  private fun DataOutputStream.writeId(id: Long) {
    when (idSize) {
      4 -> this.writeInt(id.toInt())
      8 -> this.writeLong(id)
      else -> assert(false)
    }
  }

  private fun DataOutputStream.writeValue(value: Long, type: Type) {
    if (type == Type.OBJECT) {
      writeId(value)
    }
    else {
      when (type.size) {
        1 -> writeByte(value.toInt())
        2 -> writeShort(value.toInt())
        4 -> writeInt(value.toInt())
        8 -> writeLong(value)
        else -> assert(false)
      }
    }
  }
}

