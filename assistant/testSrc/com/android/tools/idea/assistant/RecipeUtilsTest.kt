/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.assistant

import com.android.SdkConstants
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.templates.recipe.RenderingContext
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RecipeUtilsTest {
  @Test
  fun metadataDependenciesHaveImplementationAndApiConfigurations() {
    val dependencies: SetMultimap<String, String> = LinkedHashMultimap.create()
    val renderingContext = mock<RenderingContext>()
    whenever(renderingContext.dependencies).thenReturn(dependencies)
    dependencies.put(
      SdkConstants.GRADLE_IMPLEMENTATION_CONFIGURATION,
      "my.implementation.dependency",
    )
    dependencies.put(
      SdkConstants.GRADLE_ANDROID_TEST_IMPLEMENTATION_CONFIGURATION,
      "my.test.dependency",
    )
    dependencies.put(SdkConstants.GRADLE_API_CONFIGURATION, "my.api.dependency")

    val metadata = RecipeMetadata(mock(), mock())
    RecipeUtils.setUpMetadata(renderingContext, metadata)

    assertTrue(metadata.dependencies.contains("my.implementation.dependency"))
    assertTrue(metadata.dependencies.contains("my.api.dependency"))
    assertFalse(metadata.dependencies.contains("my.test.dependency"))
  }
}
