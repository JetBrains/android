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
package com.android.tools.idea.diagnostics.hprof.visitors

import com.android.tools.idea.diagnostics.hprof.classstore.ClassDefinition
import com.android.tools.idea.diagnostics.hprof.classstore.ClassStore
import com.android.tools.idea.diagnostics.hprof.classstore.InstanceField
import com.android.tools.idea.diagnostics.hprof.classstore.StaticField
import com.android.tools.idea.diagnostics.hprof.parser.ConstantPoolEntry
import com.android.tools.idea.diagnostics.hprof.parser.HProfVisitor
import com.android.tools.idea.diagnostics.hprof.parser.HeapDumpRecordType
import com.android.tools.idea.diagnostics.hprof.parser.InstanceFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.RecordType
import com.android.tools.idea.diagnostics.hprof.parser.StaticFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.Type
import gnu.trove.TLongArrayList
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap

class CreateClassStoreVisitor(private val stringIdMap: Long2ObjectOpenHashMap<String>) : HProfVisitor() {
  private val classIDToNameStringID = Long2LongOpenHashMap()

  private val result = Long2ObjectOpenHashMap<ClassDefinition>()
  private var completed = false

  override fun preVisit() {
    disableAll()
    enable(RecordType.LoadClass)
    enable(HeapDumpRecordType.ClassDump)
    classIDToNameStringID.clear()
  }

  override fun postVisit() {
    completed = true
  }

  override fun visitLoadClass(classSerialNumber: Long, classObjectId: Long, stackSerialNumber: Long, classNameStringId: Long) {
    classIDToNameStringID.put(classObjectId, classNameStringId)
  }

  override fun visitClassDump(
    classId: Long,
    stackTraceSerialNumber: Long,
    superClassId: Long,
    classloaderClassId: Long,
    instanceSize: Long,
    constants: Array<ConstantPoolEntry>,
    staticFields: Array<StaticFieldEntry>,
    instanceFields: Array<InstanceFieldEntry>) {
    val refInstanceFields = mutableListOf<InstanceField>()
    val primitiveInstanceFields = mutableListOf<InstanceField>()
    val staticFieldList = mutableListOf<StaticField>()
    var currentOffset = 0
    instanceFields.forEach {
      val fieldName = stringIdMap.get(it.fieldNameStringId)
      val field = InstanceField(fieldName, currentOffset, it.type)
      if (it.type != Type.OBJECT) {
        primitiveInstanceFields.add(field)
        currentOffset += it.type.size
      }
      else {
        refInstanceFields.add(field)
        currentOffset += visitorContext.idSize
      }
    }
    val constantsArray = TLongArrayList(constants.size)
    constants.filter { it.type == Type.OBJECT }.forEach { constantsArray.add(it.value) }
    val objectStaticFields = staticFields.filter { it.type == Type.OBJECT }
    objectStaticFields.forEach {
      val field = StaticField(stringIdMap[it.fieldNameStringId], it.value)
      staticFieldList.add(field)
    }
    result.put(classId,
               ClassDefinition(
                 stringIdMap[classIDToNameStringID.get(classId)].replace('/', '.'),
                 classId,
                 superClassId,
                 instanceSize.toInt(),
                 currentOffset,
                 refInstanceFields.toTypedArray(),
                 primitiveInstanceFields.toTypedArray(),
                 constantsArray.toNativeArray(),
                 staticFieldList.toTypedArray()
               ))
  }

  fun getClassStore(): ClassStore {
    assert(completed)
    return ClassStore(result)
  }
}