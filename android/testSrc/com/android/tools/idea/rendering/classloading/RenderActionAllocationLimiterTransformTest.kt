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
package com.android.tools.idea.rendering.classloading

import com.android.tools.idea.rendering.RenderService
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import junit.framework.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.StringWriter
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

interface AllocationTestInterface {
  val allocations: Long

  fun call(maxIterations: Long)
}

class AllocationTestClass : AllocationTestInterface {
  override var allocations = 0L

  private fun methodCall() {
    Object()
    allocations++
  }

  override fun call(maxIterations: Long) {
    var iterationCount = maxIterations
    while (!Thread.currentThread().isInterrupted && iterationCount-- > 0) {
      methodCall()
    }
  }
}


class RenderActionAllocationLimiterTransformTest {
  /** [StringWriter] that stores the decompiled classes after they've been transformed. */
  private val afterTransformTrace = StringWriter()

  /** [StringWriter] that stores the decompiled classes before they've been transformed. */
  private val beforeTransformTrace = StringWriter()

  // This will will log to the stdout logging information that might be useful debugging failures.
  // The logging only happens if the test fails.
  @get:Rule
  val onFailureRule = object : TestWatcher() {
    override fun failed(e: Throwable?, description: Description?) {
      super.failed(e, description)

      println("\n---- Classes before transformation ----")
      println(beforeTransformTrace)
      println("\n---- Classes after transformation ----")
      println(afterTransformTrace)
    }
  }

  @Test
  fun `verify allocation limit`() {
    val testClassLoader = setupTestClassLoaderWithTransformation(
      mapOf(AllocationTestClass::class.simpleName!! to AllocationTestClass::class.java),
      beforeTransformTrace, afterTransformTrace) { visitor ->
      RenderActionAllocationLimiterTransform(visitor, 100, maxAllocationsPerRenderAction = 100)
    }
    val loopTestInstance = testClassLoader.loadClass(
      AllocationTestClass::class.simpleName!!).getDeclaredConstructor().newInstance() as AllocationTestInterface

    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction {
        loopTestInstance.call(102)
      }.get(10, TimeUnit.SECONDS)
      fail("Expected TooManyAllocationsException")
    }
    catch (e: ExecutionException) {
      assertTrue(e.cause is TooManyAllocationsException)
      assertEquals(101, loopTestInstance.allocations)
    }
  }

  @Test
  fun `verify allocation limit sampling`() {
    val testClassLoader = setupTestClassLoaderWithTransformation(
      mapOf(AllocationTestClass::class.simpleName!! to AllocationTestClass::class.java),
      beforeTransformTrace, afterTransformTrace) { visitor ->
      RenderActionAllocationLimiterTransform(visitor, 1, maxAllocationsPerRenderAction = 1)
    }
    val loopTestInstance = testClassLoader.loadClass(
      AllocationTestClass::class.simpleName!!).getDeclaredConstructor().newInstance() as AllocationTestInterface

    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction {
        loopTestInstance.call(5000)
      }.get(10, TimeUnit.SECONDS)
      fail("Expected TooManyAllocationsException")
    }
    catch (e: ExecutionException) {
      assertTrue(e.cause is TooManyAllocationsException)
      assertTrue(loopTestInstance.allocations > 1)
    }
  }

  @Test
  fun `verify allocation limit is per action`() {
    val testClassLoader = setupTestClassLoaderWithTransformation(
      mapOf(AllocationTestClass::class.simpleName!! to AllocationTestClass::class.java),
      beforeTransformTrace, afterTransformTrace) { visitor ->
      RenderActionAllocationLimiterTransform(visitor, 100, maxAllocationsPerRenderAction = 100)
    }
    val loopTestInstance = testClassLoader.loadClass(
      AllocationTestClass::class.simpleName!!).getDeclaredConstructor().newInstance() as AllocationTestInterface

    // None of the 5 actions should hit the allocation limit since they run 90 times each
    repeat(5) {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction {
        loopTestInstance.call(90)
      }.get(10, TimeUnit.SECONDS)
    }
  }
}