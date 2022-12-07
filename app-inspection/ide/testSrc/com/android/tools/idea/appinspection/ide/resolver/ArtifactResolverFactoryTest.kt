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

import com.android.flags.junit.FlagRule
import com.android.tools.idea.appinspection.ide.resolver.blaze.BlazeArtifactResolver
import com.android.tools.idea.appinspection.ide.resolver.http.HttpArtifactResolver
import com.android.tools.idea.appinspection.ide.resolver.moduleSystem.ModuleSystemArtifactResolver
import com.android.tools.idea.appinspection.inspector.api.service.TestFileService
import com.android.tools.idea.flags.StudioFlags.APP_INSPECTION_USE_SNAPSHOT_JAR
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.IdeBrand
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@RunWith(Parameterized::class)
class ArtifactResolverFactoryTest(private val ideBrand: IdeBrand) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val variations = listOf(IdeBrand.ANDROID_STUDIO, IdeBrand.ANDROID_STUDIO_WITH_BLAZE)
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val FlagRule = FlagRule(APP_INSPECTION_USE_SNAPSHOT_JAR)

  @Test
  fun createResolver() {
    when (ideBrand) {
      IdeBrand.ANDROID_STUDIO -> run {
        assertThat(ArtifactResolverFactory(TestFileService()) { ideBrand }.getArtifactResolver(projectRule.project))
          .isInstanceOf(HttpArtifactResolver::class.java)

        APP_INSPECTION_USE_SNAPSHOT_JAR.override(true)

        assertThat(ArtifactResolverFactory(TestFileService()) { ideBrand }.getArtifactResolver(projectRule.project))
          .isInstanceOf(ModuleSystemArtifactResolver::class.java)
      }

      IdeBrand.ANDROID_STUDIO_WITH_BLAZE -> run {
        assertThat(ArtifactResolverFactory(TestFileService()) { ideBrand }.getArtifactResolver(projectRule.project))
          .isInstanceOf(BlazeArtifactResolver::class.java)
      }
    }
  }
}