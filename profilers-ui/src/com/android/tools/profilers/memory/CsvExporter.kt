/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute
import com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet
import com.google.common.annotations.VisibleForTesting
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.stream.Stream
import javax.swing.JTree

class CsvExporter(private val getTree: () -> JTree?,
                  private val getCaptureObject: () -> CaptureObject,
                  private val ideProfilerComponents: IdeProfilerComponents,
                  private val ideServices: IdeProfilerServices) {

  fun makeClassExportItem(): ContextMenuItem {
    fun classStream(c: ClassifierSet): Stream<ClassSet> = when (c) {
      is ClassSet -> Stream.of(c)
      else -> c.childrenClassifierSets.stream().flatMap(::classStream)
    }
    return makeContextMenuItem("Export class entries under selection",
                               { getCaptureObject().classifierAttributes },
                               ::classStream,
                               mapOf(
                                 ClassifierAttribute.LABEL to ClassifierSet::getName,
                                 ClassifierAttribute.ALLOCATIONS to ClassifierSet::getDeltaAllocationCount,
                                 ClassifierAttribute.DEALLOCATIONS to ClassifierSet::getDeltaDeallocationCount,
                                 ClassifierAttribute.TOTAL_COUNT to ClassifierSet::getTotalObjectCount,
                                 ClassifierAttribute.NATIVE_SIZE to ClassifierSet::getTotalNativeSize,
                                 ClassifierAttribute.SHALLOW_SIZE to ClassifierSet::getTotalShallowSize,
                                 ClassifierAttribute.SHALLOW_DIFFERENCE to ClassifierSet::getDeltaShallowSize,
                                 ClassifierAttribute.RETAINED_SIZE to ClassifierSet::getTotalRetainedSize,
                                 ClassifierAttribute.ALLOCATIONS_SIZE to ClassifierSet::getAllocationSize,
                                 ClassifierAttribute.DEALLOCATIONS_SIZE to ClassifierSet::getDeallocationSize,
                                 ClassifierAttribute.REMAINING_SIZE to ClassifierSet::getTotalRemainingSize
                               ))
  }

  fun makeInstanceExportItem() =
    makeContextMenuItem("Export instance entries under selection",
                        { getCaptureObject().instanceAttributes },
                        { it.instancesStream },
                        mapOf(
                          InstanceAttribute.LABEL to InstanceObject::getValueText,
                          InstanceAttribute.DEPTH to InstanceObject::getDepth,
                          InstanceAttribute.ALLOCATION_TIME to InstanceObject::getAllocTime,
                          InstanceAttribute.DEALLOCATION_TIME to InstanceObject::getDeallocTime,
                          InstanceAttribute.NATIVE_SIZE to InstanceObject::getNativeSize,
                          InstanceAttribute.SHALLOW_SIZE to InstanceObject::getShallowSize,
                          InstanceAttribute.RETAINED_SIZE to InstanceObject::getRetainedSize,
                        ))

  @VisibleForTesting
  fun<E, A> makeContextMenuItem(title: String,
                                getAttributes: () -> List<A>,
                                getEntryStream: (ClassifierSet) -> Stream<E>,
                                formatters: Map<A, (E) -> Any>) = object : RunnableContextMenuItem {
    override fun getText() = title
    override fun getIcon() = null
    override fun isEnabled() = getTree()?.let { !it.isSelectionEmpty } ?: false
    override fun run() = ideProfilerComponents.createExportDialog().open(
      { "Export As" },
      MemoryProfiler::generateCaptureFileName,
      { "csv" },
      { ideServices.saveFile(it, ::exportEntries, null) }
    )

    override fun exportEntries(output: OutputStream) {
      val writer = BufferedWriter(OutputStreamWriter(output))
      getTree()?.selectionPath?.lastPathComponent?.let { selection ->
        val (initAttrs, lastAttr) = with(getAttributes()) {
          assert(isNotEmpty())
          Pair(subList(0, size - 1), last())
        }

        fun outputLine(onAttr: (A) -> Any) {
          initAttrs.forEach {
            writer.write(onAttr(it).toString())
            writer.write(",")
          }
          writer.write(onAttr(lastAttr).toString())
          writer.newLine()
        }

        outputLine {it.toString()}
        getEntryStream((selection as MemoryObjectTreeNode<*>).adapter as ClassifierSet).forEach { inst ->
          outputLine { formatters[it]!!(inst) }
        }
        writer.flush()
      }
    }
  }

  @VisibleForTesting
  interface RunnableContextMenuItem : ContextMenuItem {
    fun exportEntries(out: OutputStream)
  }
}