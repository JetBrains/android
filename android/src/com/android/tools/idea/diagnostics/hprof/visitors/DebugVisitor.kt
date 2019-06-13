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
package com.android.tools.idea.diagnostics.hprof.visitors

import com.android.tools.idea.diagnostics.hprof.parser.ConstantPoolEntry
import com.android.tools.idea.diagnostics.hprof.parser.HProfVisitor
import com.android.tools.idea.diagnostics.hprof.parser.InstanceFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.StaticFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.Type
import java.nio.ByteBuffer

class DebugVisitor(private val printer: (String) -> Unit) : HProfVisitor() {

  override fun visitStringInUTF8(id: Long, s: String) {
    printer.invoke("StringInUTF8: id=$id value=$s")
  }

  override fun visitLoadClass(classSerialNumber: Long, classObjectId: Long, stackSerialNumber: Long, classNameStringId: Long) {
    printer.invoke("LoadClass: " +
                   "classSerialNumber=$classSerialNumber, " +
                   "classObjectId=$classObjectId, " +
                   "stackSerialNumber=$stackSerialNumber, " +
                   "classNameStringId=$classNameStringId")
  }

  override fun visitStackFrame(stackFrameId: Long,
                               methodNameStringId: Long,
                               methodSignatureStringId: Long,
                               sourceFilenameStringId: Long,
                               classSerialNumber: Long,
                               lineNumber: Int) {
    printer.invoke("StackFrame: " +
                   "stackFrameId=$stackFrameId, " +
                   "methodNameStringId=$methodNameStringId, " +
                   "methodSignatureStringId=$methodSignatureStringId, " +
                   "sourceFilenameStringId=$sourceFilenameStringId, " +
                   "classSerialNumber=$classSerialNumber, " +
                   "lineNumber=$lineNumber")
  }

  override fun visitStackTrace(stackTraceSerialNumber: Long, threadSerialNumber: Long, numberOfFrames: Int, stackFrameIds: LongArray) {
    printer.invoke("StackTrace: " +
                   "stackTraceSerialNumber: $stackTraceSerialNumber, " +
                   "threadSerialNumber: $threadSerialNumber, " +
                   "numberOfFrames: $numberOfFrames, " +
                   "stackFrameIds: ${stackFrameIds.arrayToString()}")
  }

  override fun visitAllocSites() {
    printer.invoke("AllocSites")
  }

  override fun visitHeapSummary(totalLiveBytes: Long, totalLiveInstances: Long, totalBytesAllocated: Long, totalInstancesAllocated: Long) {
    printer.invoke("HeapSummary: " +
                   "totalLiveBytes: $totalLiveBytes, " +
                   "totalLiveInstances: $totalLiveInstances, " +
                   "totalBytesAllocated: $totalBytesAllocated, " +
                   "totalInstancesAllocated: $totalInstancesAllocated")
  }

  override fun visitStartThread() {
    printer.invoke("StartThread")
  }

  override fun visitEndThread(threadSerialNumber: Long) {
    printer.invoke("EndThread")
  }

  override fun visitHeapDump() {
    printer.invoke("HeapDump")
  }

  override fun visitHeapDumpEnd() {
    printer.invoke("HeapDumpEnd")
  }

  override fun visitCPUSamples() {
    printer.invoke("CPUSamples")
  }

  override fun visitControlSettings() {
    printer.invoke("ControlSettings")
  }

  override fun visitRootUnknown(objectId: Long) {
    printer.invoke("RootUnknown: objectId=$objectId")
  }

  override fun visitRootGlobalJNI(objectId: Long, jniGlobalRefId: Long) {
    printer.invoke("RootGlobalJNI: objectId=$objectId, jniGlobalRefId=$jniGlobalRefId")
  }

  override fun visitRootLocalJNI(objectId: Long, threadSerialNumber: Long, frameNumber: Long) {
    printer.invoke("RootLocalJNI: objectId=$objectId, threadSerialNumber=$threadSerialNumber, frameNumber=$frameNumber")
  }

  override fun visitRootJavaFrame(objectId: Long, threadSerialNumber: Long, frameNumber: Long) {
    printer.invoke("RootJavaFrame: objectId=$objectId, threadSerialNumber=$threadSerialNumber, frameNumber=$frameNumber")
  }

  override fun visitRootNativeStack(objectId: Long, threadSerialNumber: Long) {
    printer.invoke("RootNativeStack: objectId=$objectId, threadSerialNumber=$threadSerialNumber")
  }

  override fun visitRootStickyClass(objectId: Long) {
    printer.invoke("RootStickyClass: objectId=$objectId")
  }

  override fun visitRootThreadBlock(objectId: Long, threadSerialNumber: Long) {
    printer.invoke("RootThreadBlock: objectId=$objectId, threadSerialNumber=$threadSerialNumber")
  }

  override fun visitRootMonitorUsed(objectId: Long) {
    printer.invoke("RootMonitorUsed: objectId=$objectId")
  }

  override fun visitRootThreadObject(objectId: Long, threadSerialNumber: Long, stackTraceSerialNumber: Long) {
    printer.invoke("RootThreadObject: " +
                   "objectId=$objectId, " +
                   "threadSerialNumber=$threadSerialNumber, " +
                   "stackTraceSerialNumber=$stackTraceSerialNumber")
  }

  override fun visitPrimitiveArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, numberOfElements: Long, elementType: Type) {
    printer.invoke("PrimitiveArrayDump: " +
                   "arrayObjectId=$arrayObjectId, " +
                   "stackTraceSerialNumber=$stackTraceSerialNumber, " +
                   "numberOfElements=$numberOfElements, " +
                   "elementType=${elementType.name}")
  }

  override fun visitClassDump(classId: Long,
                              stackTraceSerialNumber: Long,
                              superClassId: Long,
                              classloaderClassId: Long,
                              instanceSize: Long,
                              constants: Array<ConstantPoolEntry>,
                              staticFields: Array<StaticFieldEntry>,
                              instanceFields: Array<InstanceFieldEntry>) {
    printer.invoke("ClassDump: " +
                   "classId=$classId, " +
                   "stackTraceSerialNumber=$stackTraceSerialNumber, " +
                   "superClassId=$superClassId, " +
                   "classloaderClassId=$classloaderClassId, " +
                   "instanceSize=$instanceSize")
  }

  override fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {
    printer.invoke("ObjectArrayDump: arrayObjectId=$arrayObjectId, " +
                   "stackTraceSerialNumber=$stackTraceSerialNumber, " +
                   "arrayClassObjectId=$arrayClassObjectId, " +
                   "objects=id[${objects.size}")
  }

  override fun visitInstanceDump(objectId: Long, stackTraceSerialNumber: Long, classObjectId: Long, bytes: ByteBuffer) {
    printer.invoke("InstanceDump: " +
                   "objectId=$objectId, " +
                   "stackTraceSerialNumber: $stackTraceSerialNumber, " +
                   "classObjectId=$classObjectId," +
                   "bytes=byte[${bytes.limit()}]")
  }

  override fun visitUnloadClass(classSerialNumber: Long) {
    printer.invoke("UnloadClass: classSerialNumber=$classSerialNumber")
  }

  private fun LongArray.arrayToString(): String = buildString {
    append('[')
    for (i in 0 until size) {
      if (i > 0)
        append(", ")
      append(this[i])
    }
    append(']')
  }
}