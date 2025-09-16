/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.tools.idea.logcat.messages

import com.android.testutils.TestResources
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.util.logcatEvents
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.StackRetraceEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.guessModuleDir
import com.intellij.testFramework.RuleChain
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.delete
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.fail
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private val MESSAGE =
  """
    java.lang.RuntimeException
      at java.util.Objects.checkIndex(Objects.java:385)
      at c1.e.d(r8-map-id-MAP_ID:5)
      at m.j.k(r8-map-id-MAP_ID:139)
      at k1.a.r(r8-map-id-MAP_ID:9)
      at A1.x.n(r8-map-id-MAP_ID:83)
      at A1.f.p(r8-map-id-MAP_ID:114)
  """
    .trimIndent()

private val CLEAR_MESSAGE =
  """
  java.lang.RuntimeException (Show original)
    at java.util.Objects.checkIndex(Objects.java:385)
    at com.example.proguardedapp.MainActivityKt.logStackTrace(MainActivity.kt:43)
    at com.example.proguardedapp.MainActivityKt.access${'$'}logStackTrace(MainActivity.kt:1)
    at com.example.proguardedapp.MainActivityKt${'$'}App$1$1$1$1.invoke(MainActivity.kt:38)
    at com.example.proguardedapp.MainActivityKt${'$'}App$1$1$1$1.invoke(MainActivity.kt:38)
    at androidx.compose.foundation.ClickableNode${'$'}clickPointerInput$3.invoke-k-4lQ0M(ClickableNode.java:639)
    at androidx.compose.foundation.ClickableNode${'$'}clickPointerInput$3.invoke(ClickableNode.java:633)
    at androidx.compose.foundation.gestures.TapGestureDetectorKt${'$'}detectTapAndPress$2$1.invokeSuspend(TapGestureDetector.kt:255)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
    at kotlinx.coroutines.DispatchedTaskKt.resume(DispatchedTask.kt:179)
    at kotlinx.coroutines.DispatchedTaskKt.dispatch(DispatchedTask.kt:168)
    at kotlinx.coroutines.CancellableContinuationImpl.dispatchResume(CancellableContinuationImpl.kt:474)
  """
    .trimIndent()

/** Tests for [AutoProguardMessageRewriter] */
@RunWith(JUnit4::class)
class AutoProguardMessageRewriterTest {
  private val projectRule =
    AndroidProjectRule.withAndroidModels(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        ":app1",
        "release",
        AndroidProjectBuilder(applicationIdFor = { "app1" }),
      ),
      AndroidModuleModelBuilder(
        ":app2",
        "release",
        AndroidProjectBuilder(applicationIdFor = { "app2" }),
      ),
      AndroidModuleModelBuilder(
        ":app3",
        "release",
        AndroidProjectBuilder(applicationIdFor = { "app2" }),
      ),
    )

  private val usageTrackerRule = UsageTrackerRule()

  @get:Rule val rule = RuleChain(projectRule, usageTrackerRule)

  @After
  fun tearDown() {
    StudioFlags.LOGCAT_AUTO_DEOBFUSCATE_CACHE_TIME_MS.clearOverride()
  }

  @Test
  fun rewrite_success() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id-12345"

    val mappingFile = copyMapToModule(findModules("app1").first(), "release", mapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(mapId), "app1")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent(mappingFile.fileSize(), isCached = false))
  }

  @Test
  fun rewrite_cachesValue() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id-12345"
    val mappingFile = copyMapToModule(findModules("app1").first(), "release", mapId)
    val mappingFileSize = mappingFile.fileSize()
    val message = MESSAGE.withMapId(mapId)
    rewriter.rewrite(message, "app1")
    mappingFile.delete()

    val text = rewriter.rewrite(message, "app1")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(
        stackRetraceEvent(mappingFileSize, isCached = false),
        stackRetraceEvent(mappingFileSize, isCached = true),
      )
      .inOrder()
  }

  @Test
  fun rewrite_multipleVariants() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val incorrectMapId = "incorrect-map-id"
    val correctMapId = "correct-map-id"
    val module = findModules("app1").first()
    copyMapToModule(module, "release1", incorrectMapId)
    val mappingFile = copyMapToModule(module, "release2", correctMapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(correctMapId), "app1")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent(mappingFile.fileSize(), isCached = false))
  }

  @Test
  fun rewrite_multipleModules() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id"
    val module = findModules("app2").first()
    copyMapToModule(module, "release", mapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(mapId), "app2")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
  }

  @Test
  fun rewrite_multipleModulesOfSameAppId() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val incorrectMapId = "incorrect-map-id"
    val correctMapId = "correct-map-id"
    val module1 = findModules("app2")[0]
    val module2 = findModules("app2")[1]
    copyMapToModule(module1, "release", incorrectMapId)
    val mappingFile = copyMapToModule(module2, "release", correctMapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(correctMapId), "app2")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent(mappingFile.fileSize(), isCached = false))
  }

  @Test
  fun rewrite_noMatchingMapping() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    copyMapToModule(findModules("app1").first(), "release", "map-id-12345")
    val message = MESSAGE.withMapId("map-id-missing")

    val text = rewriter.rewrite(message, "app1")

    assertThat(text).isEqualTo(message)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent("MATCHING_MAPPING_NOT_FOUND"))
  }

  @Test
  fun rewrite_noBuildDir() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val message = MESSAGE.withMapId("map-id-missing")

    val text = rewriter.rewrite(message, "app1")

    assertThat(text).isEqualTo(message)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent("BUILD_DIR_NOT_FOUND"))
  }

  @Test
  fun rewrite_noMappingsDir() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val message = MESSAGE.withMapId("map-id-missing")
    val module = findModules("app1").first()
    val moduleDir = module.guessModuleDir()?.toNioPath() ?: fail("Failed to prepare module dir")
    val mappingDir = moduleDir.resolve("build")
    mappingDir.createDirectories()

    val text = rewriter.rewrite(message, "app1")

    assertThat(text).isEqualTo(message)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent("MAPPINGS_DIR_NOT_FOUND"))
  }

  @Test
  fun rewrite_noMappingsFile() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val message = MESSAGE.withMapId("map-id-missing")
    val module = findModules("app1").first()
    val moduleDir = module.guessModuleDir()?.toNioPath() ?: fail("Failed to prepare module dir")
    val mappingDir = moduleDir.resolve("build/outputs/mapping")
    mappingDir.createDirectories()

    val text = rewriter.rewrite(message, "app1")

    assertThat(text).isEqualTo(message)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent("MAPPINGS_FILE_NOT_FOUND"))
  }

  @Test
  fun rewrite_noMappingId() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val message = MESSAGE.withMapId("map-id-missing")
    copyMapToModule(findModules("app1").first(), "release", mapId = null)

    val text = rewriter.rewrite(message, "app1")

    assertThat(text).isEqualTo(message)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent("MAPPINGS_HAVE_NO_MAP_ID"))
  }

  @Test
  fun rewrite_noApp() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id-12345"
    copyMapToModule(findModules("app1").first(), "release", mapId)
    val message = MESSAGE.withMapId(mapId)

    val text = rewriter.rewrite(message, "unknown-app")

    assertThat(text).isEqualTo(message)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent("MODULES_NOT_FOUND"))
  }

  @Test
  fun cacheIsPurged() {
    StudioFlags.LOGCAT_AUTO_DEOBFUSCATE_CACHE_TIME_MS.override(0)
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id-12345"
    copyMapToModule(findModules("app1").first(), "release", mapId)
    val text = rewriter.rewrite(MESSAGE.withMapId(mapId), "app1")
    assertThat(text).isEqualTo(CLEAR_MESSAGE)

    waitForCondition { rewriter.autoRetracer == null }
  }

  private fun findModules(applicationId: String): List<Module> {
    return projectRule.project.getProjectSystem().findModulesWithApplicationId(applicationId).map {
      it.getModuleSystem().getHolderModule()
    }
  }
}

private fun copyMapToModule(module: Module?, variant: String, mapId: String?): Path {
  val moduleDir = module?.guessModuleDir()?.toNioPath() ?: fail("Failed to prepare module dir")
  val mappingFile = moduleDir.resolve("build/outputs/mapping/$variant/mapping.txt")
  mappingFile.createParentDirectories()
  val text =
    if (mapId == null) {
      TestResources.getFile("/proguard/mapping-without-id.txt").toPath().readText()
    } else {
      TestResources.getFile("/proguard/mapping-with-id.txt")
        .toPath()
        .readText()
        .replace("MAP_ID", mapId)
    }
  mappingFile.writeText(text)
  return mappingFile
}

private fun String.withMapId(mapId: String) = replace("MAP_ID", mapId)

private fun UsageTrackerRule.retraceEvents() =
  logcatEvents()
    .mapNotNull { it.stackRetrace }
    .map { it.takeIf { !it.hasRetraceTimeMs() } ?: it.toBuilder().setRetraceTimeMs(0).build() }

private fun stackRetraceEvent(mappingFileSize: Long, isCached: Boolean): StackRetraceEvent {
  return StackRetraceEvent.newBuilder()
    .setResultString("SUCCESS")
    .setMappingFileSize(mappingFileSize)
    .setIsMappingCached(isCached)
    .setRetraceTimeMs(0) // We override this to zero in `UsageTrackerRule.retraceEvents`
    .build()
}

private fun stackRetraceEvent(result: String): StackRetraceEvent {
  return StackRetraceEvent.newBuilder().setResultString(result).build()
}
