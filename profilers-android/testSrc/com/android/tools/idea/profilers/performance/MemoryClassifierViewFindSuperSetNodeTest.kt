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
package com.android.tools.idea.profilers.performance

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.perflogger.Benchmark
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.CaptureDurationData
import com.android.tools.profilers.memory.CaptureEntry
import com.android.tools.profilers.memory.ClassGrouping
import com.android.tools.profilers.memory.FakeCaptureObjectLoader
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MemoryClassifierView
import com.android.tools.profilers.memory.MemoryObjectTreeNode
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.system.measureTimeMillis

// Context: b/166815924
class MemoryClassifierViewFindSuperSetNodeTest {

  private val timingBenchmark = Benchmark.Builder("Smallest Superset Node Finding Time (millis)")
    .setProject("Android Studio Profilers")
    .build()

  private val ideServices = FakeIdeProfilerServices()
  private val timer = FakeTimer()
  private lateinit var profilers: StudioProfilers
  private lateinit var stage: MainMemoryProfilerStage
  private val capture = FakeCaptureObject.Builder().build()
  private lateinit var view: MemoryClassifierView

  @get:Rule
  val grpcChannel = FakeGrpcChannel(javaClass.simpleName, FakeTransportService(timer))

  @Before
  fun init() {
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideServices, timer)
    stage = MainMemoryProfilerStage(profilers, FakeCaptureObjectLoader().apply {
      setReturnImmediateFuture(true)
    })
  }

  @Test
  fun `find node in flat view`() = benchmark("Flat", ClassGrouping.ARRANGE_BY_CLASS, makeInstances(4, 10, 1, {100}))

  @Test
  fun `find node in hierarchical view`() = benchmark("Hier", ClassGrouping.ARRANGE_BY_PACKAGE, makeInstances(4, 10, 10))

  @Test
  fun `find node in deep hierarchical view`() = benchmark("Deep", ClassGrouping.ARRANGE_BY_PACKAGE, makeInstances(19, 2, 1))

  @Test
  fun `find many instances in deep stack`() = benchmark("XDeep", ClassGrouping.ARRANGE_BY_PACKAGE, makeInstances(200, 1, 1) { (200-it) * 10 });

  private fun benchmark(tag: String, grouping: ClassGrouping, instances: List<InstanceObject>) {
    capture.addInstanceObjects(instances.toSet())
    stage.selectCaptureDuration(CaptureDurationData(1, false, false, CaptureEntry<CaptureObject>(Any()) { capture }),
                                null)
    stage.captureSelection.selectHeapSet(capture.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID))
    stage.captureSelection.classGrouping = grouping

    // create view late to avoid the significant overhead of UI reacting during setup
    view = MemoryClassifierView(stage.captureSelection, FakeIdeProfilerComponents()).apply {
      refreshCapture()
      refreshGrouping()
    }

    val root = view.tree!!.model.root as MemoryObjectTreeNode<ClassifierSet>
    val targetInstance = instances.last()
    val targetSet = root.adapter.findContainingClassifierSet(targetInstance)!!

    var result: MemoryObjectTreeNode<ClassifierSet>?
    gc()
    val elapsedMillis = measureTimeMillis { result = MemoryClassifierView.findSmallestSuperSetNode(root, targetSet) }
    assertThat(result).isNotNull()
    assertThat(result!!.adapter).isInstanceOf(ClassSet::class.java)
    assertThat(result!!.adapter.name).isEqualTo(targetInstance.classEntry.simpleClassName)
    timingBenchmark.log("Find-SuperSet-Node-$tag", elapsedMillis)
  }

  private fun makeInstances(depth: Int, packagesPerNode: Int, classesPerNode: Int, instancesPerClass: (Int) -> Int = {1}): List<InstanceObject> {
    val nodes = mutableListOf<InstanceObject>()
    var id = 0L;
    fun makeInstancesAt(prefix: String, depth: Int) {
      (0 until classesPerNode).forEach {
        val className = "$prefix.c$it"
        (0 until instancesPerClass(depth)).forEach {
          nodes.add(FakeInstanceObject.Builder(capture, id++, className).setName("instance_${className}_$it").build())
        }
      }
      if (depth > 0) {
        (0 until packagesPerNode).forEach {
          makeInstancesAt("$prefix.p$it", depth - 1)
        }
      }
    }
    makeInstancesAt("root", depth)
    return nodes
  }
}

private fun gc() {
  repeat (10) { System.gc() }
  System.runFinalization()
  repeat (10) { ManagementFactory.getMemoryMXBean().gc() }
}