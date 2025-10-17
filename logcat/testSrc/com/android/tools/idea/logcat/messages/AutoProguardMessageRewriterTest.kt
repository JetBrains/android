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
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.StackRetraceEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.StackRetraceEvent.MappingType
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.StackRetraceEvent.MappingType.PARTITIONED
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.StackRetraceEvent.MappingType.TEXT
import com.intellij.openapi.components.service
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import java.nio.file.Path
import kotlin.io.path.fileSize
import org.junit.After
import org.junit.Before
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
  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val disposableRule = DisposableRule()
  private val disposable
    get() = disposableRule.disposable

  private val temporaryDirectoryRule = TemporaryDirectoryRule()
  private val usageTrackerRule = UsageTrackerRule()
  private val mappings = mutableListOf<R8Mappings>()

  @get:Rule
  val rule = RuleChain(projectRule, usageTrackerRule, temporaryDirectoryRule, disposableRule)

  @Before
  fun setUp() {
    registerLogcatR8MappingsTokenExtension()
  }

  @After
  fun tearDown() {
    StudioFlags.LOGCAT_AUTO_DEOBFUSCATE_CACHE_TIME_MS.clearOverride()
  }

  @Test
  fun rewrite_text() {
    val rewriter = project.service<AutoProguardMessageRewriter>()
    val textMapping = TestResources.getFile("/proguard/text/mapping.txt").toPath()
    mappings.add(R8Mappings(textMapping, Path.of("missing.prt")))

    val text = rewriter.rewrite(MESSAGE.withMapId("correct-map-id"))

    assertThat(rewriter.getMapping()).isEqualTo(textMapping)
    assertThat(text).isEqualTo(CLEAR_MESSAGE)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent(textMapping.fileSize(), isCached = false, TEXT))
  }

  @Test
  fun rewrite_partitioned() {
    val rewriter = project.service<AutoProguardMessageRewriter>()
    val textMapping = TestResources.getFile("/proguard/partitioned/mapping.txt").toPath()
    val partitionedMapping = TestResources.getFile("/proguard/partitioned/mapping.prt").toPath()
    mappings.add(R8Mappings(textMapping, partitionedMapping))

    val text = rewriter.rewrite(MESSAGE_PRT.withMapId("correct-map-id"))

    assertThat(rewriter.getMapping()).isEqualTo(partitionedMapping)
    assertThat(text).isEqualTo(CLEAR_MESSAGE_PRT)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(
        stackRetraceEvent(partitionedMapping.fileSize(), isCached = false, PARTITIONED)
      )
  }

  @Test
  fun rewrite_cachesValue() {
    val rewriter = project.service<AutoProguardMessageRewriter>()
    val textMapping = TestResources.getFile("/proguard/text/mapping.txt").toPath()
    mappings.add(R8Mappings(textMapping, Path.of("missing.prt")))
    val message = MESSAGE.withMapId("correct-map-id")

    rewriter.rewrite(message)

    mappings.clear()
    val text = rewriter.rewrite(message)

    assertThat(text).isEqualTo(CLEAR_MESSAGE)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(
        stackRetraceEvent(textMapping.fileSize(), isCached = false, TEXT),
        stackRetraceEvent(textMapping.fileSize(), isCached = true, TEXT),
      )
      .inOrder()
  }

  @Test
  fun rewrite_noMatchingMapping() {
    val rewriter = project.service<AutoProguardMessageRewriter>()
    val textMapping = TestResources.getFile("/proguard/partitioned/mapping.txt").toPath()
    val partitionedMapping = TestResources.getFile("/proguard/partitioned/mapping.prt").toPath()
    mappings.add(R8Mappings(textMapping, partitionedMapping))

    val message = MESSAGE.withMapId("incorrect-map-id")

    val text = rewriter.rewrite(message)

    assertThat(text).isEqualTo(message)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent("MATCHING_MAPPING_NOT_FOUND"))
  }

  @Test
  fun rewrite_noMappingId() {
    val rewriter = project.service<AutoProguardMessageRewriter>()
    val textMapping = TestResources.getFile("/proguard/mapping-without-id.txt").toPath()
    mappings.add(R8Mappings(textMapping, Path.of("")))
    val message = MESSAGE.withMapId("map-id-missing")

    val text = rewriter.rewrite(message)

    assertThat(text).isEqualTo(message)
    assertThat(usageTrackerRule.retraceEvents())
      .containsExactly(stackRetraceEvent("MAPPINGS_HAVE_NO_MAP_ID"))
  }

  @Test
  fun cacheIsPurged() {
    StudioFlags.LOGCAT_AUTO_DEOBFUSCATE_CACHE_TIME_MS.override(0)
    val rewriter = project.service<AutoProguardMessageRewriter>()
    val textMapping = TestResources.getFile("/proguard/text/mapping.txt").toPath()
    mappings.add(R8Mappings(textMapping, Path.of("missing.prt")))

    val text = rewriter.rewrite(MESSAGE.withMapId("correct-map-id"))
    assertThat(text).isEqualTo(CLEAR_MESSAGE)

    waitForCondition { rewriter.autoRetracer == null }
  }

  private fun registerLogcatR8MappingsTokenExtension() {
    val element: LogcatR8MappingsToken<AndroidProjectSystem> = FakeLogcatR8MappingsToken()
    val extensionsToAdd: List<LogcatR8MappingsToken<AndroidProjectSystem>> = listOf(element)
    ExtensionTestUtil.addExtensions(LogcatR8MappingsToken.EP_NAME, extensionsToAdd, disposable)
  }

  private inner class FakeLogcatR8MappingsToken() : LogcatR8MappingsToken<AndroidProjectSystem> {
    override fun getR8Mappings(projectSystem: AndroidProjectSystem): List<R8Mappings> = mappings

    override fun isApplicable(projectSystem: AndroidProjectSystem) = true
  }
}

private fun String.withMapId(mapId: String) = replace("MAP_ID", mapId)

private fun UsageTrackerRule.retraceEvents() =
  logcatEvents()
    .mapNotNull { it.stackRetrace }
    .map { it.takeIf { !it.hasRetraceTimeMs() } ?: it.toBuilder().setRetraceTimeMs(0).build() }

private fun stackRetraceEvent(
  mappingFileSize: Long,
  isCached: Boolean,
  mappingType: MappingType,
): StackRetraceEvent {
  return StackRetraceEvent.newBuilder()
    .setResultString("SUCCESS")
    .setMappingFileSize(mappingFileSize)
    .setIsMappingCached(isCached)
    .setMappingType(mappingType)
    .setRetraceTimeMs(0) // We override this to zero in `UsageTrackerRule.retraceEvents`
    .build()
}

private fun stackRetraceEvent(result: String): StackRetraceEvent {
  return StackRetraceEvent.newBuilder().setResultString(result).build()
}
