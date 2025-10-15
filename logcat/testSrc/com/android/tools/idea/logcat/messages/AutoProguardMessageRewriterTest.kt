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
import com.android.tools.idea.logcat.LogcatR8MappingsToken
import com.android.tools.idea.logcat.LogcatR8MappingsToken.R8Mappings
import com.android.tools.idea.logcat.util.logcatEvents
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.projectsystem.AndroidProjectSystem
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
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.write
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.pathString
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

private val MESSAGE_PRT =
  """
    java.lang.Exception: Foo
      at a3.d.b(r8-map-id-MAP_ID:5)
      at m.j.l(r8-map-id-MAP_ID:139)
      at i3.a.h(r8-map-id-MAP_ID:9)
      at v3.x.m(r8-map-id-MAP_ID:83)
      at v3.g.q(r8-map-id-MAP_ID:114)
      at v3.g.D(r8-map-id-MAP_ID:33)
      at v3.g.h(r8-map-id-MAP_ID:17)
      at z0.q.f0(r8-map-id-MAP_ID:51)
      at z0.q.g(r8-map-id-MAP_ID:31)
      at l.g.g(r8-map-id-MAP_ID:105)
      at z0.e.v(r8-map-id-MAP_ID:152)
      at z0.e.v(r8-map-id-MAP_ID:131)
      at f1.w1.e(r8-map-id-MAP_ID:38)
      at u.w.c(r8-map-id-MAP_ID:144)
      at g1.u.E(r8-map-id-MAP_ID:81)
      at g1.u.l(r8-map-id-MAP_ID:387)
      at g1.u.dispatchTouchEvent(r8-map-id-MAP_ID:76)
  """
    .trimIndent()

private val CLEAR_MESSAGE_PRT =
  """
    java.lang.Exception: Foo (Show original)
      at com.example.proguardedapp.MainActivityKt.logStackTrace(MainActivity.kt:43)
      at com.example.proguardedapp.MainActivityKt.access${'$'}logStackTrace(MainActivity.kt:1)
      at com.example.proguardedapp.MainActivityKt${'$'}App$1$1$1$1.invoke(MainActivity.kt:38)
      at com.example.proguardedapp.MainActivityKt${'$'}App$1$1$1$1.invoke(MainActivity.kt:38)
      at androidx.compose.foundation.ClickableNode${'$'}clickPointerInput$3.invoke-k-4lQ0M(ClickableNode.java:639)
      at androidx.compose.foundation.ClickableNode${'$'}clickPointerInput$3.invoke(ClickableNode.java:633)
      at androidx.compose.foundation.gestures.TapGestureDetectorKt${'$'}detectTapAndPress$2$1.invokeSuspend(TapGestureDetector.kt:255)
      at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
      at kotlinx.coroutines.DispatchedTaskKt.resume(DispatchedTask.kt:175)
      at kotlinx.coroutines.DispatchedTaskKt.dispatch(DispatchedTask.kt:164)
      at kotlinx.coroutines.CancellableContinuationImpl.dispatchResume(CancellableContinuationImpl.kt:466)
      at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl(CancellableContinuationImpl.kt:500)
      at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl${'$'}default(CancellableContinuationImpl.kt:489)
      at kotlinx.coroutines.CancellableContinuationImpl.resumeWith(CancellableContinuationImpl.kt:364)
      at androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNodeImpl${'$'}PointerEventHandlerCoroutine.offerPointerEvent(SuspendingPointerInputFilter.kt:719)
      at androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNodeImpl.dispatchPointerEvent(SuspendingPointerInputFilter.kt:598)
      at androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNodeImpl.onPointerEvent-H0pRuoY(SuspendingPointerInputFilter.kt:620)
      at androidx.compose.foundation.AbstractClickableNode.onPointerEvent-H0pRuoY(AbstractClickableNode.java:1044)
      at androidx.compose.ui.input.pointer.Node.dispatchMainEventPass(HitPathTracker.kt:387)
      at androidx.compose.ui.input.pointer.Node.dispatchMainEventPass(HitPathTracker.kt:373)
      at androidx.compose.ui.input.pointer.NodeParent.dispatchMainEventPass(NodeParent.java:229)
      at androidx.compose.ui.input.pointer.HitPathTracker.dispatchChanges(HitPathTracker.java:144)
      at androidx.compose.ui.input.pointer.PointerInputEventProcessor.process-BIzXfog(PointerInputEventProcessor.java:120)
      at androidx.compose.ui.platform.AndroidComposeView.sendMotionEvent-8iAsVTc(AndroidComposeView.android.kt:1993)
      at androidx.compose.ui.platform.AndroidComposeView.handleMotionEvent-8iAsVTc(AndroidComposeView.android.kt:1944)
      at androidx.compose.ui.platform.AndroidComposeView.dispatchTouchEvent(AndroidComposeView.android.kt:1828)
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

    val mappings =
      copyMapToModule(
        "/proguard/mapping-with-id.txt",
        findModules("app1").first(),
        "release",
        mapId,
      )

    val text = rewriter.rewrite(MESSAGE.withMapId(mapId), "app1")

    assertThat(rewriter.getMapping()).isEqualTo(mappings.text)
    assertThat(text).isEqualTo(CLEAR_MESSAGE)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent(mappings.text.fileSize(), isCached = false))
  }

  @Test
  fun rewrite_successPartitioned() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id-12345"

    val mappings =
      copyMapToModule(
        "/proguard/partitioned/mapping.txt",
        findModules("app1").first(),
        "release",
        mapId,
      )

    val text = rewriter.rewrite(MESSAGE_PRT.withMapId(mapId), "app1")

    assertThat(rewriter.getMapping()).isEqualTo(mappings.partitioned)
    assertThat(text).isEqualTo(CLEAR_MESSAGE_PRT)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent(mappings.partitioned!!.fileSize(), isCached = false))
  }

  @Test
  fun rewrite_cachesValue() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id-12345"
    val mappings =
      copyMapToModule(
        "/proguard/mapping-with-id.txt",
        findModules("app1").first(),
        "release",
        mapId,
      )
    val mappingFileSize = mappings.text.fileSize()
    val message = MESSAGE.withMapId(mapId)
    rewriter.rewrite(message, "app1")
    mappings.text.delete()

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
    copyMapToModule("/proguard/mapping-with-id.txt", module, "release1", incorrectMapId)
    val mappings =
      copyMapToModule("/proguard/mapping-with-id.txt", module, "release2", correctMapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(correctMapId), "app1")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent(mappings.text.fileSize(), isCached = false))
  }

  @Test
  fun rewrite_multipleModules() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id"
    val module = findModules("app2").first()
    copyMapToModule("/proguard/mapping-with-id.txt", module, "release", mapId)

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
    copyMapToModule("/proguard/mapping-with-id.txt", module1, "release", incorrectMapId)
    val mappings =
      copyMapToModule("/proguard/mapping-with-id.txt", module2, "release", correctMapId)

    val text = rewriter.rewrite(MESSAGE.withMapId(correctMapId), "app2")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent(mappings.text.fileSize(), isCached = false))
  }

  @Test
  fun rewrite_noMatchingMapping() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    copyMapToModule(
      "/proguard/mapping-with-id.txt",
      findModules("app1").first(),
      "release",
      "map-id-12345",
    )
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
    copyMapToModule(
      "/proguard/mapping-without-id.txt",
      findModules("app1").first(),
      "release",
      mapId = null,
    )

    val text = rewriter.rewrite(message, "app1")

    assertThat(text).isEqualTo(message)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent("MAPPINGS_HAVE_NO_MAP_ID"))
  }

  @Test
  fun rewrite_noApp() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id-12345"
    copyMapToModule("/proguard/mapping-with-id.txt", findModules("app1").first(), "release", mapId)
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
    copyMapToModule("/proguard/mapping-with-id.txt", findModules("app1").first(), "release", mapId)
    val text = rewriter.rewrite(MESSAGE.withMapId(mapId), "app1")
    assertThat(text).isEqualTo(CLEAR_MESSAGE)

    waitForCondition { rewriter.autoRetracer == null }
  }

  @Test
  fun rewrite_usingLogcatR8MappingsToken() {
    val rewriter = projectRule.project.service<AutoProguardMessageRewriter>()
    val mapId = "map-id-12345"
    val path =
      copyMapToModule(
        "/proguard/mapping-with-id.txt",
        findModules("app1").first(),
        "release",
        mapId,
      )
    registerLogcatR8MappingsTokenExtension(path.text)

    // This would fail if we didn't register the LogcatR8MappingsTokenExtension
    val text = rewriter.rewrite(MESSAGE.withMapId(mapId), "random-app")

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
  }

  private fun findModules(applicationId: String): List<Module> {
    return projectRule.project.getProjectSystem().findModulesWithApplicationId(applicationId).map {
      it.getModuleSystem().getHolderModule()
    }
  }

  private fun registerLogcatR8MappingsTokenExtension(path: Path) {
    val element: LogcatR8MappingsToken<AndroidProjectSystem> =
      FakeLogcatR8MappingsToken(
        listOf(R8Mappings(path, Path.of(path.pathString.replace(".txt", ".prt"))))
      )
    val extensionsToAdd: List<LogcatR8MappingsToken<AndroidProjectSystem>> = listOf(element)
    ExtensionTestUtil.addExtensions(
      LogcatR8MappingsToken.EP_NAME,
      extensionsToAdd,
      projectRule.testRootDisposable,
    )
  }

  private class FakeLogcatR8MappingsToken(private val mappings: List<R8Mappings>) :
    LogcatR8MappingsToken<AndroidProjectSystem> {
    override fun getR8Mappings(projectSystem: AndroidProjectSystem): List<R8Mappings> = mappings

    override fun isApplicable(projectSystem: AndroidProjectSystem) = true
  }
}

private fun copyMapToModule(
  path: String,
  module: Module?,
  variant: String,
  mapId: String?,
): R8Mappings {
  val moduleDir = module?.guessModuleDir()?.toNioPath() ?: fail("Failed to prepare module dir")
  val mappingFile = moduleDir.resolve("build/outputs/mapping/$variant/mapping.txt")
  mappingFile.createParentDirectories()
  val text =
    if (mapId == null) {
      TestResources.getFile(path).toPath().readText()
    } else {
      TestResources.getFile(path).toPath().readText().replace("MAP_ID", mapId)
    }
  mappingFile.writeText(text)
  val prtMappingFile = mappingFile.withExtension("prt")
  try {
    val prtContents = TestResources.getFile(path.replaceAfterLast('.', "prt")).readBytes()
    prtMappingFile.write(prtContents)
  } catch (_: Throwable) {
    // No prt file, ignore
  }
  return R8Mappings(mappingFile, prtMappingFile)
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
