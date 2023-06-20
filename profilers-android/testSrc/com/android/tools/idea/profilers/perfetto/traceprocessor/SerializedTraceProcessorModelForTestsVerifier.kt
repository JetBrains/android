/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.android.tools.profilers.cpu.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.cpu.systemtrace.SystemTraceSurfaceflingerManager
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * This test verifies that our serialized TPD model used for unit tests and stored in profilers/testData
 * are updated and consistent with what is produced with the real execution of TPD.
 *
 * If you're seeing failures from this test, run `regen TPD model files` test method from inside the IDE and
 * models inside testData will be updated.
 */
class SerializedTraceProcessorModelForTestsVerifier {
  private val fakeIdeProfilerServices = FakeIdeProfilerServices()
  private val fakeProcess = ProcessModel(1, "", emptyMap(), emptyMap())
  private lateinit var service: TraceProcessorServiceImpl

  @Before
  fun setUp() {
    service = TraceProcessorServiceImpl()
  }

  @After
  fun tearDown() {
    // Make sure we dispose the whole service, so we shutdown the daemon.
    Disposer.dispose(service)
  }

  @Test
  fun `test perfetto trace`() {
    val loadOk = service.loadTrace(1, CpuProfilerTestUtils.getTraceFile("perfetto.trace"), fakeIdeProfilerServices)
    assertThat(loadOk).isTrue()
    val realProcessList = service.getProcessMetadata(1, fakeIdeProfilerServices)
    val serializedProcesssList = loadSerializedProcessList(CpuProfilerTestUtils.getTraceFile("perfetto.trace_process_list"))
    assertThat(realProcessList).containsExactlyElementsIn(serializedProcesssList).inOrder()

    val sfProcessId = realProcessList.find {
      it.getSafeProcessName().endsWith(SystemTraceSurfaceflingerManager.SURFACEFLINGER_PROCESS_NAME)
    }

    // We load the serialized model map and check that all processes are present.
    val serializedModelMap = loadSerializedModelMap(CpuProfilerTestUtils.getTraceFile("perfetto.trace_tpd_model"))

    for (process in realProcessList) {
      val pid = process.id
      val processesToQuery = mutableListOf(process)
      sfProcessId?.let(processesToQuery::add)

      val realModel = service.loadCpuData(1, processesToQuery, fakeProcess, fakeIdeProfilerServices)
      val serializedModel = serializedModelMap[pid] ?: error("$pid should be present perfetto.trace_tpd_model")
      assertThat(realModel.getCaptureStartTimestampUs()).isEqualTo(serializedModel.getCaptureStartTimestampUs())
      assertThat(realModel.getCaptureEndTimestampUs()).isEqualTo(serializedModel.getCaptureEndTimestampUs())
      assertThat(realModel.getProcesses()).isEqualTo(serializedModel.getProcesses())
      assertThat(realModel.getCpuCores()).isEqualTo(serializedModel.getCpuCores())
    }
  }

  @Test
  fun `test perfetto_cpu_usage trace`() {
    val loadOk = service.loadTrace(1, CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage.trace"), fakeIdeProfilerServices)
    assertThat(loadOk).isTrue()
    val realProcessList = service.getProcessMetadata(1, fakeIdeProfilerServices)
    val serializedProcesssList = loadSerializedProcessList(CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage.trace_process_list"))
    assertThat(realProcessList).containsExactlyElementsIn(serializedProcesssList).inOrder()

    val sfProcessId = realProcessList.find {
      it.getSafeProcessName().endsWith(SystemTraceSurfaceflingerManager.SURFACEFLINGER_PROCESS_NAME)
    }

    // We load the serialized model map and check that all processes are present.
    val serializedModelMap = loadSerializedModelMap(CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage.trace_tpd_model"))

    for (process in realProcessList) {
      val pid = process.id
      val processesToQuery = mutableListOf(process)
      sfProcessId?.let(processesToQuery::add)

      val realModel = service.loadCpuData(1, processesToQuery, fakeProcess, fakeIdeProfilerServices)
      val serializedModel = serializedModelMap[pid] ?: error("$pid should be present perfetto_cpu_usage.trace_tpd_model")
      assertThat(realModel.getCaptureStartTimestampUs()).isEqualTo(serializedModel.getCaptureStartTimestampUs())
      assertThat(realModel.getCaptureEndTimestampUs()).isEqualTo(serializedModel.getCaptureEndTimestampUs())
      assertThat(realModel.getProcesses()).isEqualTo(serializedModel.getProcesses())
      assertThat(realModel.getCpuCores()).isEqualTo(serializedModel.getCpuCores())
    }
  }

  @Test
  fun `test perfetto_frame_lifecycle trace`() {
    val loadOk = service.loadTrace(1, CpuProfilerTestUtils.getTraceFile("perfetto_frame_lifecycle.trace"), fakeIdeProfilerServices)
    assertThat(loadOk).isTrue()

    val realModel = service.loadCpuData(1, emptyList(), ProcessModel(1, "android.com.java.profilertester", emptyMap(), emptyMap()),
                                        fakeIdeProfilerServices)

    // We load the serialized model map and verify there's only one element.
    val serializedModelMap = loadSerializedModelMap(CpuProfilerTestUtils.getTraceFile("perfetto_frame_lifecycle.trace_tpd_model"))
    assertThat(serializedModelMap).hasSize(1)

    // Check that the frame layers match.
    val serializedModel = serializedModelMap.values.iterator().next()
    assertThat(realModel.getAndroidFrameLayers()).isEqualTo(serializedModel.getAndroidFrameLayers())
  }

  private fun loadSerializedProcessList(serializedProcessModelList: File): List<ProcessModel> {
    val ois = ObjectInputStream(FileInputStream(serializedProcessModelList))

    @Suppress("UNCHECKED_CAST")
    val processList = ois.readObject() as List<ProcessModel>
    ois.close()

    return processList
  }

  private fun loadSerializedModelMap(serializedModelMap: File): Map<Int, SystemTraceModelAdapter> {
    val ois = ObjectInputStream(FileInputStream(serializedModelMap))
    @Suppress("UNCHECKED_CAST")
    val modelMap = ois.readObject() as Map<Int, SystemTraceModelAdapter>
    ois.close()

    return modelMap
  }

  @Test
  @Ignore("To be invoked manually to regenerate _process_list and _tpd_model for perfetto traces in testData")
  fun `regen TPD model files`() {
    // Use different trace IDs to keep one from overwriting another in TPD. These IDs don't persist outside of this method.
    produceAndWriteModelsFor(CpuProfilerTestUtils.getTraceFile("perfetto.trace"), 1)
    produceAndWriteModelsFor(CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage.trace"), 2)
    produceAndWriteModelsFor(CpuProfilerTestUtils.getTraceFile("perfetto_frame_lifecycle.trace"), 3, "android.com.java.profilertester")
    produceAndWriteModelsFor(CpuProfilerTestUtils.getTraceFile("perfetto_cpu_compose.trace"), 4,
                             "com.google.samples.apps.nowinandroid.demo.debug")
    produceAndWriteModelsFor(CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage_with_power.trace"), 5,
                             "com.android.systemui")
  }

  private fun produceAndWriteModelsFor(traceFile: File, traceId: Long, selectedProcessName: String = "") {
    val loadOk = service.loadTrace(traceId, traceFile, fakeIdeProfilerServices)
    assertThat(loadOk).isTrue()

    val processList = service.getProcessMetadata(traceId, fakeIdeProfilerServices)

    val sfProcessId = processList.find {
      it.getSafeProcessName().endsWith(SystemTraceSurfaceflingerManager.SURFACEFLINGER_PROCESS_NAME)
    }

    val modelMapBuilder = ImmutableMap.builder<Int, SystemTraceModelAdapter>()
    if (selectedProcessName.isEmpty()) {
      // Generate model for every process.
      for (process in processList) {
        val pid = process.id
        val processesToQuery = mutableListOf(process)
        sfProcessId?.let(processesToQuery::add)

        val model = service.loadCpuData(traceId, processesToQuery, fakeProcess, fakeIdeProfilerServices)
        modelMapBuilder.put(pid, model)
      }
    }
    else {
      // Only generate model for the selected process.
      val selectedProcess = processList.first { processModel -> processModel.name == selectedProcessName }
      val model = service.loadCpuData(traceId, listOf(selectedProcess), selectedProcess, fakeIdeProfilerServices)
      modelMapBuilder.put(selectedProcess.id, model)
    }

    val processListModelFile = File(traceFile.parentFile, "${traceFile.name}_process_list")
    writeObjectToFile(processListModelFile, processList)
    val modelMapFile = File(traceFile.parentFile, "${traceFile.name}_tpd_model")
    writeObjectToFile(modelMapFile, modelMapBuilder.build())
  }

  private fun writeObjectToFile(file: File, serializableObject: Any) {
    file.delete()
    file.createNewFile()
    val oos = ObjectOutputStream(FileOutputStream(file))
    oos.writeObject(serializableObject)
    oos.flush()
    oos.close()
  }
}