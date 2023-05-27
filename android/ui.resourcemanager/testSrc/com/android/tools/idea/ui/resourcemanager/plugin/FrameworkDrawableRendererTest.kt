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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class FrameworkDrawableRendererTest {

  @get:Rule
  var rule = AndroidProjectRule.inMemory()

  @Before
  fun setup() {
    val facet = rule.module.androidFacet
    assertNotNull(facet)
    FrameworkDrawableRenderer.setInstance(facet, null)
  }

  @Test
  fun testNotNullInstance() {
    val facet = rule.module.androidFacet!!
    val renderer = FrameworkDrawableRenderer.getInstance(facet)
    assertNotNull(renderer)
    // Check that consecutive calls to getInstance yield the same renderer.
    assertSame(renderer, FrameworkDrawableRenderer.getInstance(facet))
  }
}