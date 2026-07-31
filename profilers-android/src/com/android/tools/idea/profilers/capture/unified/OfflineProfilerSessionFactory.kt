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
package com.android.tools.idea.profilers.capture.unified

import com.android.tools.idea.profilers.IntellijProfilerServices
import com.android.tools.nativeSymbolizer.ProjectSymbolSource
import com.android.tools.nativeSymbolizer.SymbolFilesLocator
import com.android.tools.nativeSymbolizer.SymbolSource
import com.android.tools.profiler.proto.Memory.AllocationsInfo
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto.ProfilerTaskMetadata
import com.android.tools.profiler.proto.Trace.TraceInfo
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerContext
import com.android.tools.profilers.ProfilerFormat
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StageWithToolbarView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CpuCaptureStage
import com.android.tools.profilers.cpu.CpuCaptureStageView
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration
import com.android.tools.profilers.memory.CaptureDurationData
import com.android.tools.profilers.memory.CaptureEntry
import com.android.tools.profilers.memory.CaptureObjectLoader
import com.android.tools.profilers.memory.MemoryCaptureStage
import com.android.tools.profilers.memory.MemoryCaptureStageView
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject
import com.android.tools.profilers.memory.adapters.NativeAllocationSampleCaptureObject
import com.android.tools.profilers.utils.ProfilerHashUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

data class OfflineProfilerSession(val profilers: StudioProfilers, val profilersView: StudioProfilersView, val stageView: StageView<*>?)

/**
 * A factory object responsible for constructing and initializing [OfflineProfilerSession] instances from local recording files (such as CPU
 * traces, HPROF heap dumps, or allocation profiles).
 *
 * Provides utility methods to map file formats to their corresponding stages and views.
 */
object OfflineProfilerSessionFactory {

  /**
   * Builds and initializes a new [OfflineProfilerSession] asynchronously for the given recording file.
   *
   * This method:
   * * Resolves the appropriate symbol directories.
   * * Initializes the [IntellijProfilerServices] and core [ProfilerClient].
   * * Loads cached [ProfilerTaskMetadata] on a background thread.
   * * Maps the capture file format to its corresponding capture stage (e.g., [CpuCaptureStage] or [MemoryCaptureStage]) on the EDT.
   * * Configures the placeholder views.
   *
   * @param project The current IntelliJ project context.
   * @param file The [VirtualFile] pointing to the recording file.
   * @param parentComponent The [JPanel] component where the session UI will be populated.
   * @param componentsProvider A function to create [IdeProfilerComponents] configured with profilers services.
   * @param onComplete Callback invoked on the EDT when the [OfflineProfilerSession] is fully initialized.
   */
  fun buildSessionAsync(
    project: Project,
    file: VirtualFile,
    parentComponent: JPanel,
    parentDisposable: Disposable,
    componentsProvider: (IntellijProfilerServices) -> IdeProfilerComponents,
    onComplete: (OfflineProfilerSession) -> Unit,
  ) {
    val symbolSource: SymbolSource = ProjectSymbolSource(project)
    val symbolLocator = SymbolFilesLocator(symbolSource)
    val ideServices = IntellijProfilerServices(project, symbolLocator)
    Disposer.register(parentDisposable, ideServices)

    ideServices.poolExecutor.execute {
      val localFile = File(file.path)
      val offlineMetadata =
        try {
          val hash = ProfilerHashUtils.hashTracePath(localFile)
          val tempDir = FileUtil.getTempDirectory()
          val cacheFile = File(tempDir, "${file.nameWithoutExtension}-$hash.metadata")
          if (cacheFile.exists()) {
            cacheFile.inputStream().use { input -> ProfilerTaskMetadata.parseFrom(input) }
          } else {
            ProfilerTaskMetadata.newBuilder().setTaskDataOrigin(ProfilerTaskMetadata.TaskDataOrigin.IMPORTED).build()
          }
        } catch (e: Exception) {
          Logger.getInstance(OfflineProfilerSessionFactory::class.java).warn("Failed to read task metadata for ${file.name}", e)
          null
        }

      val localFileExists = localFile.exists()
      val localFileEmpty = if (localFileExists) localFile.length() == 0L else true

      ideServices.mainExecutor.execute {
        if (Disposer.isDisposed(parentDisposable)) return@execute

        val client = ProfilerClient("OfflineProfiler")
        val profilers = StudioProfilers(client, ideServices, true)
        Disposer.register(parentDisposable) { profilers.stop() }
        ideServices.codeNavigator.cpuArchSource = Supplier {
          val metadataAbi = offlineMetadata?.sessionMetadata?.processAbi
          if (!metadataAbi.isNullOrEmpty()) {
            metadataAbi
          } else {
            profilers.sessionsManager.selectedSessionMetaData.processAbi
          }
        }

        val context = OfflineProfilerContext(ideServices, profilers, parentComponent, offlineMetadata)
        val configuration = UnspecifiedConfiguration("")

        val view = createPlaceholderProfilersView(profilers, componentsProvider(ideServices))

        var stageView: StageView<*>? = null

        if (!localFileExists || localFileEmpty) {
          val errorMessage = if (!localFileExists) "The trace file could not be found." else "The trace file is empty."
          context.onParseFailure(errorMessage)
        } else {
          val traceId = System.nanoTime()
          val stage =
            if (ProfilerFormat.isMemoryFormat(ProfilerFormat.find(file.extension, getLazyTraceType(file)))) {
              createMemoryCaptureStage(profilers, context, ideServices, file, traceId)
            } else {
              CpuCaptureStage(profilers, context, configuration, localFile, traceId, null, 0)
            }
          Disposer.register(parentDisposable) { stage.exit() }

          stageView =
            if (stage is MemoryCaptureStage) {
              MemoryCaptureStageView(view, stage)
            } else {
              CpuCaptureStageView(view, stage as CpuCaptureStage)
            }
          stage.enter()
        }

        onComplete(OfflineProfilerSession(profilers, view, stageView))
      }
    }
  }

  private fun createMemoryCaptureStage(
    profilers: StudioProfilers,
    context: ProfilerContext,
    ideServices: IntellijProfilerServices,
    file: VirtualFile,
    traceId: Long,
  ): MemoryCaptureStage {
    val loader = CaptureObjectLoader()
    val extension = file.extension?.lowercase()
    val localFile = File(file.path)

    val captureObject =
      when (extension) {
        "hprof",
        "prof",
        "perfetto-java-heap-dump" -> {
          val info = HeapDumpInfo.newBuilder().setStartTime(traceId).setEndTime(Long.MAX_VALUE).build()
          HeapDumpCaptureObject(profilers.client, profilers.session, info, null, ideServices.featureTracker, ideServices) { localFile }
        }
        "heapprofd" -> {
          val info = TraceInfo.newBuilder().setFromTimestamp(traceId).setToTimestamp(Long.MAX_VALUE).build()
          NativeAllocationSampleCaptureObject(profilers.client, profilers.session, info, ideServices, "") { localFile }
        }
        else -> {
          val info = AllocationsInfo.newBuilder().setStartTime(traceId).setEndTime(Long.MAX_VALUE).setSuccess(true).build()
          LegacyAllocationCaptureObject(profilers.client, profilers.session, info, ideServices.featureTracker) { localFile }
        }
      }

    val placeholderEntry = CaptureEntry<CaptureObject>(Any()) { captureObject }
    val durationData = CaptureDurationData(Long.MAX_VALUE, false, false, placeholderEntry, captureObject.javaClass)
    return MemoryCaptureStage(profilers, context, loader, durationData, ideServices.mainExecutor)
  }

  private fun createPlaceholderProfilersView(profilers: StudioProfilers, components: IdeProfilerComponents): StudioProfilersView {
    return object : StudioProfilersView {
      override val studioProfilers: StudioProfilers = profilers
      override val ideProfilerComponents: IdeProfilerComponents = components
      override val component: JComponent = JPanel()
      override val stageWithToolbarView: StageWithToolbarView
        get() = throw UnsupportedOperationException("Not implemented for placeholder view")

      override val stageComponent: JPanel = JPanel()
      override val stageView: StageView<*>? = null

      override fun installCommonMenuItems(component: JComponent) {}

      override fun dispose() {}
    }
  }
}
