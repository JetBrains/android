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
package com.android.tools.idea.appinspection.ide.resolver

import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.appinspection.ide.resolver.blaze.BlazeArtifactResolver
import com.android.tools.idea.appinspection.ide.resolver.http.HttpArtifactResolver
import com.android.tools.idea.appinspection.inspector.api.service.TestFileService
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

enum class Variation {
  GRADLE,
  APK,
  BLAZE,
}

@RunWith(Parameterized::class)
class ArtifactResolverFactoryTest(private val variation: Variation) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val variations = listOf(Variation.APK, Variation.GRADLE, Variation.BLAZE)
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun createResolver() {
    when (variation) {
      Variation.GRADLE -> run {
        projectRule.addFacet(GradleFacet.getFacetType(), GradleFacet.getFacetName())
        assertThat(ArtifactResolverFactory(TestFileService()).getArtifactResolver(projectRule.project))
          .isInstanceOf(HttpArtifactResolver::class.java)
      }
      Variation.APK -> run {
        projectRule.addFacet(ApkFacet.getFacetType(), ApkFacet.getFacetName())
        assertThat(ArtifactResolverFactory(TestFileService()).getArtifactResolver(projectRule.project))
          .isInstanceOf(HttpArtifactResolver::class.java)
      }
      Variation.BLAZE -> run {
        assertThat(ArtifactResolverFactory(TestFileService()).getArtifactResolver(projectRule.project))
          .isInstanceOf(BlazeArtifactResolver::class.java)
      }
    }
  }
}