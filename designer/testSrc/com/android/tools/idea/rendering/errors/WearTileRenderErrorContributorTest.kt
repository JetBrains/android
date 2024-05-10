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
package com.android.tools.idea.rendering.errors

import com.android.tools.idea.diagnostics.ExceptionTestUtils.createExceptionFromDesc
import com.android.tools.idea.rendering.StudioHtmlLinkManager
import com.android.tools.idea.rendering.errors.WearTileRenderErrorContributor.isHandledByWearTileContributor
import com.android.tools.idea.rendering.errors.WearTileRenderErrorContributor.reportWearTileErrors
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.rendering.RenderLogger
import com.intellij.lang.annotation.HighlightSeverity
import javax.swing.event.HyperlinkListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WearTileRenderErrorContributorTest {
  @get:Rule val androidProjectRule = AndroidProjectRule.inMemory()

  private val linkManager = StudioHtmlLinkManager()
  private val nopLinkHandler = HyperlinkListener {}

  @Test
  fun `invalid context usage`() {
    val throwable =
      createExceptionFromDesc(
        """
      java.lang.NullPointerException: Cannot invoke "android.content.Context.checkPermission(String, int, int)" because "this.mBase" is null
      	at android.content.ContextWrapper.checkPermission(ContextWrapper.java:944)
      	at androidx.core.content.ContextCompat.checkSelfPermission(ContextCompat.java:604)
      	at androidx.wear.tiles.tooling.TileServiceViewAdapter.previewTile(TileServiceViewAdapter.kt:127)
      	at androidx.wear.tiles.tooling.TileServiceViewAdapter.previewTile${'$'}default(TileServiceViewAdapter.kt:114)
      	at androidx.wear.tiles.tooling.TileServiceViewAdapter.init${'$'}tiles_tooling_release(TileServiceViewAdapter.kt:111)
      	at androidx.wear.tiles.tooling.TileServiceViewAdapter.init(TileServiceViewAdapter.kt:94)
      	at androidx.wear.tiles.tooling.TileServiceViewAdapter.<init>(TileServiceViewAdapter.kt:87)
      	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
      	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:77)
      	at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
      	at java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:499)
      	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:480)
      	at com.android.tools.rendering.ViewLoader.createNewInstance(ViewLoader.java:292)
      	at com.android.tools.rendering.ViewLoader.loadClass(ViewLoader.java:155)
      	at com.android.tools.rendering.ViewLoader.loadView(ViewLoader.java:116)
      	at com.android.tools.rendering.LayoutlibCallbackImpl.loadView(LayoutlibCallbackImpl.java:280)
      	at android.view.BridgeInflater.loadCustomView(BridgeInflater.java:429)
      	at android.view.BridgeInflater.loadCustomView(BridgeInflater.java:440)
      	at android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:344)
      	at android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:973)
      	at android.view.LayoutInflater.inflate(LayoutInflater.java:667)
      	at android.view.LayoutInflater.inflate(LayoutInflater.java:505)
      	at com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:365)
      	at com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:454)
      	at com.android.tools.idea.layoutlib.LayoutLibrary.createSession(LayoutLibrary.java:120)
      	at com.android.tools.rendering.RenderTask.createRenderSession(RenderTask.java:776)
      	at com.android.tools.rendering.RenderTask.lambda${'$'}inflate${'$'}6(RenderTask.java:924)
      	at com.android.tools.rendering.RenderExecutor${'$'}runAsyncActionWithTimeout${'$'}3.run(RenderExecutor.kt:203)
      	at com.android.tools.rendering.RenderExecutor${'$'}PriorityRunnable.run(RenderExecutor.kt:317)
      	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
      	at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:635)
      	at java.base/java.lang.Thread.run(Thread.java:840)

      """
          .trimIndent()
      )
    val logger =
      RenderLogger(androidProjectRule.project).apply {
        addBrokenClass("androidx.wear.tiles.tooling.TileServiceViewAdapter", throwable)
      }
    assertTrue(isHandledByWearTileContributor(throwable))
    val issues = reportWearTileErrors(logger, linkManager, nopLinkHandler)
    assertEquals(1, issues.size)
    assertEquals(HighlightSeverity.ERROR, issues[0].severity)
    assertEquals("Invalid Context used", issues[0].summary)
    assertEquals(
      "It seems like the Tile Preview failed to render due to the <A HREF=\"https://developer.android.com/reference/android/content/Context\">Context</A>." +
        " Any Context used within a preview must come from the preview method's parameter, otherwise it will not be properly initialised.<BR/>" +
        "<A HREF=\"runnable:0\">Show Exception</A>",
      issues[0].htmlContent,
    )
  }

  @Test
  fun `invalid context usage outside of TileServiceViewAdapter is not reported`() {
    val throwable =
      createExceptionFromDesc(
        """
      java.lang.NullPointerException: Cannot invoke "android.content.Context.checkPermission(String, int, int)" because "this.mBase" is null
      	at android.content.ContextWrapper.checkPermission(ContextWrapper.java:944)
      	at androidx.core.content.ContextCompat.checkSelfPermission(ContextCompat.java:604)
      	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
      	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:77)
      	at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
      	at java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:499)
      	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:480)
      	at com.android.tools.rendering.ViewLoader.createNewInstance(ViewLoader.java:292)
      	at com.android.tools.rendering.ViewLoader.loadClass(ViewLoader.java:155)
      	at com.android.tools.rendering.ViewLoader.loadView(ViewLoader.java:116)
      	at com.android.tools.rendering.LayoutlibCallbackImpl.loadView(LayoutlibCallbackImpl.java:280)
      	at android.view.BridgeInflater.loadCustomView(BridgeInflater.java:429)
      	at android.view.BridgeInflater.loadCustomView(BridgeInflater.java:440)
      	at android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:344)
      	at android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:973)
      	at android.view.LayoutInflater.inflate(LayoutInflater.java:667)
      	at android.view.LayoutInflater.inflate(LayoutInflater.java:505)
      	at com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:365)
      	at com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:454)
      	at com.android.tools.idea.layoutlib.LayoutLibrary.createSession(LayoutLibrary.java:120)
      	at com.android.tools.rendering.RenderTask.createRenderSession(RenderTask.java:776)
      	at com.android.tools.rendering.RenderTask.lambda${'$'}inflate${'$'}6(RenderTask.java:924)
      	at com.android.tools.rendering.RenderExecutor${'$'}runAsyncActionWithTimeout${'$'}3.run(RenderExecutor.kt:203)
      	at com.android.tools.rendering.RenderExecutor${'$'}PriorityRunnable.run(RenderExecutor.kt:317)
      	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
      	at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:635)
      	at java.base/java.lang.Thread.run(Thread.java:840)

      """
          .trimIndent()
      )
    val logger =
      RenderLogger(androidProjectRule.project).apply {
        addBrokenClass("androidx.some.ClassName", throwable)
      }
    assertFalse(isHandledByWearTileContributor(throwable))
    val issues = reportWearTileErrors(logger, linkManager, nopLinkHandler)
    assertEquals(0, issues.size)
  }
}
