/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.ide.common.rendering.api.ILayoutLog
import com.android.tools.idea.diagnostics.ExceptionTestUtils.createExceptionFromDesc
import com.android.tools.idea.rendering.HtmlLinkManager
import com.android.tools.idea.rendering.RenderErrorContributorTest.stripImages
import com.android.tools.idea.rendering.RenderLogger
import com.android.tools.idea.rendering.errors.ComposeRenderErrorContributor.isHandledByComposeContributor
import com.android.tools.idea.rendering.errors.ComposeRenderErrorContributor.reportComposeErrors
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.lang.annotation.HighlightSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import javax.swing.event.HyperlinkListener

class ComposeRenderErrorContributorTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.inMemory()

  private val linkManager = HtmlLinkManager()
  private val nopLinkHandler = HyperlinkListener { }

  @Test
  fun `composition local stack trace is found`() {
    val throwable = createExceptionFromDesc("""
      java.lang.IllegalStateException: Not provided
      	at com.google.adux.shrine.ExpandedCartKt${'$'}provider${'$'}1.invoke(ExpandedCart.kt:219)
      	at com.google.adux.shrine.ExpandedCartKt${'$'}provider${'$'}1.invoke(ExpandedCart.kt:219)
      	at _layoutlib_._internal_.kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:74)
      	at androidx.compose.runtime.LazyValueHolder.getCurrent(ValueHolders.kt:29)
      	at androidx.compose.runtime.LazyValueHolder.getValue(ValueHolders.kt:31)
      	at androidx.compose.runtime.ComposerImpl.resolveCompositionLocal(Composer.kt:1780)
      	at androidx.compose.runtime.ComposerImpl.consume(Composer.kt:1750)
      	at com.google.adux.shrine.ExpandedCartKt.ShortCardPreview(ExpandedCart.kt:679)
      	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
      	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
      	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
      	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
      	at androidx.compose.ui.tooling.CommonPreviewUtils.invokeComposableMethod(CommonPreviewUtils.kt:149)
      	at androidx.compose.ui.tooling.CommonPreviewUtils.invokeComposableViaReflection${'$'}ui_tooling_release(CommonPreviewUtils.kt:188)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1${'$'}composable${'$'}1.invoke(ComposeViewAdapter.kt:571)
      	at androidx.compose.ui.platform.AndroidComposeView.onAttachedToWindow(AndroidComposeView.android.kt:820)
      	at android.view.View.dispatchAttachedToWindow(View.java:20753)
      	at android.view.ViewGroup.dispatchAttachedToWindow(ViewGroup.java:3490)
      	at android.view.ViewGroup.dispatchAttachedToWindow(ViewGroup.java:3497)
      	at android.view.AttachInfo_Accessor.setAttachInfo(AttachInfo_Accessor.java:57)
      	at com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:362)
      	at com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:436)
      	at com.android.tools.idea.layoutlib.LayoutLibrary.createSession(LayoutLibrary.java:121)
      	at com.android.tools.idea.rendering.RenderTask.createRenderSession(RenderTask.java:714)
      	at com.android.tools.idea.rendering.RenderTask.lambda${'$'}inflate${'$'}7(RenderTask.java:870)
      	at com.android.tools.idea.rendering.RenderExecutor${'$'}runAsyncActionWithTimeout${'$'}2.run(RenderExecutor.kt:187)
      	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
      	at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:628)
      	at java.base/java.lang.Thread.run(Thread.java:834)

      """.trimIndent())
    val logger = RenderLogger(androidProjectRule.project).apply {
      error(ILayoutLog.TAG_INFLATE, "Error", throwable, null, null)
    }
    assertTrue(isHandledByComposeContributor(throwable))
    val issues = reportComposeErrors(logger, linkManager, nopLinkHandler)
    assertEquals(1, issues.size)
    assertEquals(HighlightSeverity.INFORMATION, issues[0].severity)
    assertEquals("Failed to instantiate Composition Local", issues[0].summary)
    assertEquals(
      "This preview was unable to find a <A HREF=\"https://developer.android.com/jetpack/compose/compositionlocal\">CompositionLocal</A>. " +
      "You might need to define it so it can render correctly.<BR/>" +
      "<A HREF=\"runnable:0\">Show Exception</A>", issues[0].htmlContent)
  }

  @Test
  fun `preview element not found`() {
    val throwable = createExceptionFromDesc("""
      java.lang.NoSuchMethodException: Not provided
      	at androidx.compose.ui.tooling.CommonPreviewUtils.invokeComposableMethod(CommonPreviewUtils.kt:149)
      	at androidx.compose.ui.tooling.CommonPreviewUtils.invokeComposableViaReflection${'$'}ui_tooling_release(CommonPreviewUtils.kt:188)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1${'$'}composable${'$'}1.invoke(ComposeViewAdapter.kt:571)
      	at androidx.compose.ui.platform.AndroidComposeView.onAttachedToWindow(AndroidComposeView.android.kt:820)
      	at android.view.View.dispatchAttachedToWindow(View.java:20753)
      	at android.view.ViewGroup.dispatchAttachedToWindow(ViewGroup.java:3490)
      	at android.view.ViewGroup.dispatchAttachedToWindow(ViewGroup.java:3497)
      	at android.view.AttachInfo_Accessor.setAttachInfo(AttachInfo_Accessor.java:57)
      	at com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:362)
      	at com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:436)
      	at com.android.tools.idea.layoutlib.LayoutLibrary.createSession(LayoutLibrary.java:121)
      	at com.android.tools.idea.rendering.RenderTask.createRenderSession(RenderTask.java:714)
      	at com.android.tools.idea.rendering.RenderTask.lambda${'$'}inflate${'$'}7(RenderTask.java:870)
      	at com.android.tools.idea.rendering.RenderExecutor${'$'}runAsyncActionWithTimeout${'$'}2.run(RenderExecutor.kt:187)
      	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
      	at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:628)
      	at java.base/java.lang.Thread.run(Thread.java:834)

      """.trimIndent())
    val logger = RenderLogger(androidProjectRule.project).apply {
      error(ILayoutLog.TAG_INFLATE, "Error", throwable, null, null)
    }

    assertTrue(isHandledByComposeContributor(throwable))
    val issues = reportComposeErrors(logger, linkManager, nopLinkHandler)
    assertEquals(1, issues.size)
    assertEquals(HighlightSeverity.WARNING, issues[0].severity)
    assertEquals("Unable to find @Preview 'Not provided'", issues[0].summary)
    assertEquals("The preview will display after rebuilding the project.<BR/><BR/>" +
                 "<A HREF=\"action:build\">Build</A> the project.<BR/>", stripImages(issues[0].htmlContent))
  }

  @Test
  fun `view model usage`() {
    val throwable = createExceptionFromDesc("""
      java.lang.Throwable: lateinit property okHttpClient has not been initialized
      	at androidx.compose.ui.tooling.CommonPreviewUtils.invokeComposableMethod(CommonPreviewUtils.kt:149)
      	at com.example.jetcaster.Graph.getOkHttpClient(Graph.kt:42)
      	at com.example.jetcaster.Graph${'$'}podcastRepository${'$'}2.invoke(Graph.kt:52)
      	at _layoutlib_._internal_.kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:74)
      	at com.example.jetcaster.Graph.getPodcastRepository(Graph.kt:52)
      	at com.example.jetcaster.ui.home.HomeViewModel.<init>(HomeViewModel.kt:33)
      	at androidx.lifecycle.ViewModelProvider${'$'}NewInstanceFactory.create(ViewModelProvider.java:219)
      	at androidx.lifecycle.ViewModelProvider.get(ViewModelProvider.java:187)
      	at androidx.lifecycle.viewmodel.compose.ViewModelKt.viewModel(ViewModel.kt:72)
      	at com.example.jetcaster.ui.home.HomeKt.Home(Home.kt:89)
      	at androidx.compose.ui.tooling.CommonPreviewUtils.invokeComposableMethod(CommonPreviewUtils.kt:150)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}{'${'$'}'}3${'$'}1.invoke(ComposeViewAdapter.kt:573)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:107)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:34)
      	at androidx.compose.runtime.CompositionLocalKt.CompositionLocalProvider(CompositionLocal.kt:215)
      	at androidx.compose.ui.tooling.InspectableKt.Inspectable(Inspectable.kt:61)
      	at androidx.lifecycle.LifecycleRegistry.addObserver(LifecycleRegistry.java:196)
      	at androidx.compose.ui.platform.WrappedComposition${'$'}setContent${'$'}1.invoke(Wrapper.android.kt:138)
      	at androidx.compose.ui.platform.AndroidComposeView.onAttachedToWindow(AndroidComposeView.android.kt:901)
      	at android.view.View.dispatchAttachedToWindow(View.java:20753)
      	at android.view.ViewGroup.dispatchAttachedToWindow(ViewGroup.java:3497)
      	at android.view.AttachInfo_Accessor.setAttachInfo(AttachInfo_Accessor.java:57)
      	at com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:368)
      	at com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:436)
      	at com.android.tools.idea.layoutlib.LayoutLibrary.createSession(LayoutLibrary.java:121)
      	at com.android.tools.idea.rendering.RenderTask.createRenderSession(RenderTask.java:730)
      	at com.android.tools.idea.rendering.RenderTask.lambda${'$'}inflate${'$'}{'${'$'}'}8(RenderTask.java:886)
      	at com.android.tools.idea.rendering.RenderExecutor${'$'}runAsyncActionWithTimeout${'$'}{'${'$'}'}2.run(RenderExecutor.kt:187)
      	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
      	at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:628)
      	at java.base/java.lang.Thread.run(Thread.java:829)

      """.trimIndent())
    val logger = RenderLogger(androidProjectRule.project).apply {
      error(ILayoutLog.TAG_INFLATE, "Error", throwable, null, null)
    }

    assertTrue(isHandledByComposeContributor(throwable))
    val issues = reportComposeErrors(logger, linkManager, nopLinkHandler)
    assertEquals(1, issues.size)
    assertEquals(HighlightSeverity.INFORMATION, issues[0].severity)
    assertEquals("Failed to instantiate a ViewModel", issues[0].summary)
    assertEquals("This preview uses a <A HREF=\"https://developer.android.com/topic/libraries/architecture/viewmodel\">ViewModel</A>. " +
                 "ViewModels often trigger operations not supported by Compose Preview, such as database access, I/O operations, or " +
                 "network requests. You can <A HREF=\"https://developer.android.com/jetpack/compose/tooling\">read more</A> about preview" +
                 " limitations in our external documentation.<BR/><A HREF=\"runnable:0\">Show Exception</A>",
                 stripImages(issues[0].htmlContent))
  }

  @Test
  fun `compose parameter provider mismatch`() {
    val throwable = createExceptionFromDesc("""
      java.lang.IllegalArgumentException: argument type mismatch
      	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
      	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
      	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
      	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
      	at androidx.compose.ui.tooling.ComposableInvoker.invokeComposableMethod(ComposableInvoker.kt:163)
      	at androidx.compose.ui.tooling.ComposableInvoker.invokeComposable(ComposableInvoker.kt:203)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1${'$'}composable${'$'}1.invoke(ComposeViewAdapter.kt:509)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1${'$'}composable${'$'}1.invoke(ComposeViewAdapter.kt:507)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1.invoke(ComposeViewAdapter.kt:544)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1.invoke(ComposeViewAdapter.kt:502)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:107)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:34)
      	at androidx.compose.runtime.CompositionLocalKt.CompositionLocalProvider(CompositionLocal.kt:228)
      	at androidx.compose.ui.tooling.InspectableKt.Inspectable(Inspectable.kt:61)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}WrapPreview${'$'}1.invoke(ComposeViewAdapter.kt:530)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}WrapPreview${'$'}1.invoke(ComposeViewAdapter.kt:529)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:107)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:34)
      	at androidx.compose.runtime.CompositionLocalKt.CompositionLocalProvider(CompositionLocal.kt:228)
      	at androidx.compose.ui.tooling.ComposeViewAdapter.WrapPreview(ComposeViewAdapter.kt:524)
      	at androidx.compose.ui.tooling.ComposeViewAdapter.access${'$'}WrapPreview(ComposeViewAdapter.kt:123)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3.invoke(ComposeViewAdapter.kt:581)
      	at androidx.lifecycle.LifecycleRegistry.addObserver(LifecycleRegistry.java:196)
      	at androidx.compose.ui.platform.WrappedComposition${'$'}setContent${'$'}1.invoke(Wrapper.android.kt:138)
      	at androidx.compose.ui.platform.WrappedComposition${'$'}setContent${'$'}1.invoke(Wrapper.android.kt:131)
      	at androidx.compose.ui.platform.AndroidComposeView.onAttachedToWindow(AndroidComposeView.android.kt:1085)
      	at android.view.View.dispatchAttachedToWindow(View.java:21291)
      	at android.view.ViewGroup.dispatchAttachedToWindow(ViewGroup.java:3491)
      	at android.view.ViewGroup.dispatchAttachedToWindow(ViewGroup.java:3498)
      	at android.view.AttachInfo_Accessor.setAttachInfo(AttachInfo_Accessor.java:58)
      	at com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:367)
      	at com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:443)
      	at com.android.tools.idea.layoutlib.LayoutLibrary.createSession(LayoutLibrary.java:121)
      	at com.android.tools.idea.rendering.RenderTask.createRenderSession(RenderTask.java:721)
      	at com.android.tools.idea.rendering.RenderTask.lambda${'$'}inflate${'$'}9(RenderTask.java:878)
      	at com.android.tools.idea.rendering.RenderExecutor${'$'}runAsyncActionWithTimeout${'$'}3.run(RenderExecutor.kt:194)
      	at com.android.tools.idea.rendering.RenderExecutor${'$'}PriorityRunnable.run(RenderExecutor.kt:285)
      	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
      	at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:628)
      	at java.base/java.lang.Thread.run(Thread.java:829)

      """.trimIndent())
    val logger = RenderLogger(androidProjectRule.project).apply {
      error(ILayoutLog.TAG_INFLATE, "Error", throwable, null, null)
    }

    assertTrue(isHandledByComposeContributor(throwable))
    val issues = reportComposeErrors(logger, linkManager, nopLinkHandler)
    assertEquals(1, issues.size)
    assertEquals(HighlightSeverity.ERROR, issues[0].severity)
    assertEquals("PreviewParameterProvider/@Preview type mismatch.", issues[0].summary)
    assertEquals("The type of the PreviewParameterProvider must match the @Preview input parameter type annotated with it." +
                 "<BR/><BR/><A HREF=\"action:build\">Build</A> the project.<BR/>",
                 stripImages(issues[0].htmlContent))
  }

  @Test
  fun `compose preview parameter provider fails to load`() {
    val throwable = createExceptionFromDesc("""
      java.lang.NoSuchMethodException: com.example.demo.DownloadPreviewParameterProvider.${'$'}FailToLoadPreviewParameterProvider
      	at androidx.compose.ui.tooling.ComposableInvoker.findComposableMethod(ComposableInvoker.kt:83)
      	at androidx.compose.ui.tooling.ComposableInvoker.invokeComposable(ComposableInvoker.kt:190)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1$${'$'}composable${'$'}1.invoke(ComposeViewAdapter.kt:590)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1${'$'}composable${'$'}1.invoke(ComposeViewAdapter.kt:588)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1.invoke(ComposeViewAdapter.kt:625)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3${'$'}1.invoke(ComposeViewAdapter.kt:583)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:107)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:34)
      	at androidx.compose.runtime.CompositionLocalKt.CompositionLocalProvider(CompositionLocal.kt:228)
      	at androidx.compose.ui.tooling.InspectableKt.Inspectable(Inspectable.kt:61)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}WrapPreview${'$'}1.invoke(ComposeViewAdapter.kt:531)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}WrapPreview${'$'}1.invoke(ComposeViewAdapter.kt:530)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:107)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:34)
      	at androidx.compose.runtime.CompositionLocalKt.CompositionLocalProvider(CompositionLocal.kt:228)
      	at androidx.compose.ui.tooling.ComposeViewAdapter.WrapPreview(ComposeViewAdapter.kt:525)
      	at androidx.compose.ui.tooling.ComposeViewAdapter.access${'$'}WrapPreview(ComposeViewAdapter.kt:124)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3.invoke(ComposeViewAdapter.kt:583)
      	at androidx.compose.ui.tooling.ComposeViewAdapter${'$'}init${'$'}3.invoke(ComposeViewAdapter.kt:580)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:107)
      	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(ComposableLambda.jvm.kt:34)
      	at androidx.compose.ui.platform.ComposeView.Content(ComposeView.android.kt:402)

      """.trimIndent())
    val logger = RenderLogger(androidProjectRule.project).apply {
      error(ILayoutLog.TAG_INFLATE, "Error", throwable, null, null)
    }

    assertTrue(isHandledByComposeContributor(throwable))
    val issues = reportComposeErrors(logger, linkManager, nopLinkHandler)
    assertEquals(1, issues.size)
    assertEquals(HighlightSeverity.ERROR, issues[0].severity)
    assertEquals("Fail to load PreviewParameterProvider", issues[0].summary)
    assertEquals(
      "There was problem to load the PreviewParameterProvider defined. Please double-check its constructor and the values property " +
      "implementation. The IDE logs should contain the full exception stack trace.",
      stripImages(issues[0].htmlContent)
    )
  }
}
