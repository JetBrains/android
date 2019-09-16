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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.application.runInAllowSaveMode
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FrameworkDrawableRendererTest {

  @get:Rule
  var rule = AndroidProjectRule.withSdk()

  @Before
  fun setup() {
    val facet = rule.module.androidFacet
    assertNotNull(facet)
    FrameworkDrawableRenderer.setInstance(facet, null)
  }

  @Test
  fun testNotNullInstance() {
    runInEdtAndWait {
      runInAllowSaveMode {
        rule.project.save() // Save project to guarantee a project file.
      }
    }
    val facet = rule.module.androidFacet!!
    val rendererFuture = FrameworkDrawableRenderer.getInstance(facet)
    assertNotNull(rendererFuture)
    assertFalse(rendererFuture.isDone)
    // Check that consecutive calls to getInstance yield the same pending future.
    assertSame(rendererFuture, FrameworkDrawableRenderer.getInstance(facet))
    val renderer = rendererFuture.get()
    assertNotNull(renderer)
    val completedFuture = FrameworkDrawableRenderer.getInstance(facet)
    // Once the initial future has finished, consecutive calls should yield a CompletedFuture.
    assertTrue(completedFuture.isDone)
    assertNotSame(rendererFuture, completedFuture)
    // The returned instance should be the same as long as the given facet is the same.
    assertSame(renderer, completedFuture.get())
  }

  @Test
  fun testGetInstanceWithNoProjectFile() {
    val facet = rule.module.androidFacet!!
    val e = assertFailsWith<ExecutionException> { FrameworkDrawableRenderer.getInstance(facet).get() }
    assertEquals(e.cause!!.message, "ProjectFile should not be null to obtain Configuration")
  }
}