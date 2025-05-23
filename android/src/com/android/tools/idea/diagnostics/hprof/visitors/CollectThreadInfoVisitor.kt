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

import com.android.tools.idea.diagnostics.hprof.classstore.ThreadInfo
import com.android.tools.idea.diagnostics.hprof.parser.HProfVisitor
import com.android.tools.idea.diagnostics.hprof.parser.RecordType
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap

class CollectThreadInfoVisitor(private val threadsMap: Long2ObjectOpenHashMap<ThreadInfo>,
                               private val stringIdMap: Long2ObjectOpenHashMap<String>) : HProfVisitor() {

  private val stackFrameIdToStringMap = Long2ObjectOpenHashMap<String>()
  private val classSerialNumberToNameMap = Long2ObjectOpenHashMap<String>()

  override fun preVisit() {
    disableAll()
    enable(RecordType.StackFrame)
    enable(RecordType.StackTrace)
    enable(RecordType.LoadClass)
  }

  override fun visitLoadClass(classSerialNumber: Long, classObjectId: Long, stackSerialNumber: Long, classNameStringId: Long) {
    classSerialNumberToNameMap.put(classSerialNumber, stringIdMap[classNameStringId].replace("/", "."))
  }

  override fun visitStackFrame(stackFrameId: Long,
                               methodNameStringId: Long,
                               methodSignatureStringId: Long,
                               sourceFilenameStringId: Long,
                               classSerialNumber: Long,
                               lineNumber: Int) {
    stackFrameIdToStringMap.put(stackFrameId, getStackFrameString(methodNameStringId, sourceFilenameStringId,
                                                                  classSerialNumber, lineNumber))
  }

  private fun getStackFrameString(methodNameStringId: Long,
                                  sourceFilenameStringId: Long,
                                  classSerialNumber: Long,
                                  lineNumber: Int): String = buildString {
    if (classSerialNumber != 0L) {
      append(classSerialNumberToNameMap[classSerialNumber])
      append(".")
      if (methodNameStringId != 0L) {
        append(stringIdMap[methodNameStringId])
      }
      else {
        append("<unknown method>")
      }
    }
    else {
      append("<unknown location>")
    }
    if (lineNumber == -1) {
      append("(Native method)")
    }
    else if (sourceFilenameStringId != 0L) {
      append("(${stringIdMap[sourceFilenameStringId]}")
      if (lineNumber > 0) {
        append(":$lineNumber")
      }
      append(")")
    }
  }

  override fun visitStackTrace(stackTraceSerialNumber: Long, threadSerialNumber: Long, numberOfFrames: Int, stackFrameIds: LongArray) {
    val frames = ArrayList<String>(stackFrameIds.size)
    for (i in 0 until stackFrameIds.size) {
      frames.add(stackFrameIdToStringMap[stackFrameIds[i]])
    }
    threadsMap.put(threadSerialNumber, ThreadInfo(frames))
  }
}