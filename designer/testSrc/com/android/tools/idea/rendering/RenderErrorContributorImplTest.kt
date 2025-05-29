/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.sdklib.IAndroidTarget
import com.android.testutils.TestUtils
import com.android.tools.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.diagnostics.ExceptionTestUtils
import com.android.tools.idea.rendering.errors.ui.MessageTip
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutlib.isLayoutLibTarget
import com.android.tools.rendering.ProblemSeverity
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderProblem
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderTask
import com.android.tools.rendering.security.RenderSecurityException
import com.google.common.truth.Truth
import com.google.common.util.concurrent.Futures
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DumbModeTestUtils.runInDumbModeSynchronously
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.ThrowableRunnable
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import junit.framework.TestCase
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.getInstance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class RenderErrorContributorImplTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  @get:Rule val testNameRule = TestName()

  val module: Module
    get() = projectRule.module

  val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData/").toString()
    RenderTestUtil.beforeRenderTestCase()
    RenderLogger.ignoreFidelityWarning(
      "The current rendering only supports APIs up to 34. You may encounter " +
        "crashes if using with higher APIs. To avoid, you can set a lower API for " +
        "your previews."
    )
  }

  @After
  fun tearDown() {
    RenderLogger.resetFidelityErrorsFilters()
    RenderTestUtil.afterRenderTestCase()
  }

  /**
   * Obtains the render issues for the given layout
   *
   * @param file the layout file
   * @param logOperation optional [LogOperation] to intercept rendering errors
   * @param useDumbMode when true, the render error model will be generated using the IntelliJ
   *   [com.intellij.openapi.project.DumbService]
   */
  private fun getRenderOutput(
    file: VirtualFile,
    logOperation: LogOperation?,
    useDumbMode: Boolean = false,
  ): List<RenderErrorModel.Issue?> {
    val facet = AndroidFacet.getInstance(module)
    assertNotNull(facet)
    val configurationManager = ConfigurationManager.getOrCreateInstance(module)
    val target: IAndroidTarget? = getTestTarget(configurationManager)
    assertNotNull(target)
    configurationManager.target = target
    val configuration: Configuration = configurationManager.getConfiguration(file)
    assertSame(target, configuration.getRealTarget())

    val logger = RenderLogger(module.project)
    val issues: MutableList<RenderErrorModel.Issue?> = ArrayList<RenderErrorModel.Issue?>()

    RenderTestUtil.withRenderTask(
      facet!!,
      file,
      configuration,
      logger,
      Consumer { task: RenderTask? ->
        val render = RenderTestUtil.renderOnSeparateThread(task!!)
        assertNotNull(render)

        logOperation?.addErrors(logger, render!!)

        val errorModelTask = Runnable {
          // The error model must be created on a background thread.
          val errorModel =
            ApplicationManager.getApplication()
              .executeOnPooledThread(
                Callable { RenderErrorModelFactory.createErrorModel(null, render!!) }
              )
          Futures.getUnchecked<RenderErrorModel?>(errorModel)!!
            .issues
            .stream()
            .sorted()
            .forEachOrdered { e: RenderErrorModel.Issue? -> issues.add(e) }
        }
        if (useDumbMode) {
          runInDumbModeSynchronously(fixture.project, ThrowableRunnable { errorModelTask.run() })
        } else {
          errorModelTask.run()
        }
      },
    )
    return issues
  }

  @Test
  fun testPanel() {
    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml"),
        null,
      )
    assertSize(2, issues)
    assertHtmlEquals(
      "The following classes could not be found:<DL>" +
        "<DD>-&nbsp;LinerLayout (<A HREF=\"replaceTags:LinerLayout/LinearLayout\">Change to LinearLayout</A>" +
        ", <A HREF=\"action:classpath\">Fix Build Path</A>" +
        ", <A HREF=\"showTag:LinerLayout\">Edit XML</A>)</DL>",
      issues[0]!!,
    )
    assertHtmlEquals(
      "<B>NOTE: One or more layouts are missing the layout_width or layout_height attributes. These are required in most layouts.</B><BR/>" +
        "&lt;LinearLayout> does not set the required layout_width attribute: <BR/>" +
        "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"\">Set to wrap_content</A>, <A HREF=\"\">Set to match_parent</A><BR/>" +
        "&lt;LinearLayout> does not set the required layout_height attribute: <BR/>" +
        "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"\">Set to wrap_content</A>, <A HREF=\"\">Set to match_parent</A><BR/>" +
        "<BR/>" +
        "Or: <A HREF=\"\">Automatically add all missing attributes</A><BR/><BR/><BR/>",
      issues.get(1)!!,
    )
    assertBottomPanelEquals(
      listOf(
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"action:buildForRendering\">Build</A> the module.",
        ),
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"action:build\">Build</A> the project.",
        ),
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
        ),
      ),
      issues[0]!!,
    )
  }

  @Test
  fun testDataBindingAttributes() {
    val issues =
      getRenderOutput(fixture.copyFileToProject(BASE_PATH + "db.xml", "res/layout/db.xml"), null)
    assertSize(2, issues)
    assertHtmlEquals(
      "The following classes could not be found:<DL>" +
        "<DD>-&nbsp;LinerLayout (<A HREF=\"replaceTags:LinerLayout/LinearLayout\">Change to LinearLayout</A>" +
        ", <A HREF=\"action:classpath\">Fix Build Path</A>" +
        ", <A HREF=\"showTag:LinerLayout\">Edit XML</A>)</DL>",
      issues[0]!!,
    )
    assertHtmlEquals(
      "<B>NOTE: One or more layouts are missing the layout_width or layout_height attributes. These are required in most layouts.</B><BR/>" +
        "&lt;LinearLayout> does not set the required layout_width attribute: <BR/>" +
        "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"\">Set to wrap_content</A>, <A HREF=\"\">Set to match_parent</A><BR/>" +
        "&lt;LinearLayout> does not set the required layout_height attribute: <BR/>" +
        "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"\">Set to wrap_content</A>, <A HREF=\"\">Set to match_parent</A><BR/>" +
        "<BR/>" +
        "Or: <A HREF=\"\">Automatically add all missing attributes</A><BR/><BR/><BR/>",
      issues.get(1)!!,
    )
    assertBottomPanelEquals(
      listOf(
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"action:buildForRendering\">Build</A> the module.",
        ),
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"action:build\">Build</A> the project.",
        ),
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
        ),
      ),
      issues[0]!!,
    )
  }

  @Test
  fun testTypo() {
    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml"),
        null,
      )
    assertSize(1, issues)
    assertHtmlEquals(
      "The following classes could not be found:<DL>" +
        "<DD>-&nbsp;Bitton (<A HREF=\"replaceTags:Bitton/Button\">Change to Button</A>" +
        ", <A HREF=\"action:classpath\">Fix Build Path</A>" +
        ", <A HREF=\"showTag:Bitton\">Edit XML</A>)</DL>",
      issues[0]!!,
    )

    assertBottomPanelEquals(
      listOf(
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"action:buildForRendering\">Build</A> the module.",
        ),
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"action:build\">Build</A> the project.",
        ),
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
        ),
      ),
      issues[0]!!,
    )
  }

  @Test
  fun testBrokenCustomView() {
    val target = AtomicReference<IAndroidTarget?>()
    val operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      val throwable =
        ExceptionTestUtils.createExceptionFromDesc(
          "java.lang.ArithmeticException: / by zero\n" +
            "\tat com.example.myapplication574.MyCustomView.<init>(MyCustomView.java:13)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:39)\n" +
            "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
            "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
            "\tat org.jetbrains.android.uipreview.ViewLoader.createNewInstance(ViewLoader.java:365)\n" +
            "\tat org.jetbrains.android.uipreview.ViewLoader.loadView(ViewLoader.java:97)\n" +
            "\tat com.android.tools.idea.rendering.LayoutlibCallback.loadView(LayoutlibCallback.java:121)\n" +
            "\tat android.view.BridgeInflater.loadCustomView(BridgeInflater.java:207)\n" +
            "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:135)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:373)\n" +
            "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:385)\n" +
            "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:332)\n" +
            "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:325)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:525)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:518)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:958)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.createRenderSession(RenderService.java:518)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.render(RenderService.java:555)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:418)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:969)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.doRender(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.access$1100(AndroidLayoutPreviewToolWindowManager.java:79)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8$1.run(AndroidLayoutPreviewToolWindowManager.java:373)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl$2.run(ProgressManagerImpl.java:178)\n" +
            "\tat com.intellij.openapi.progress.ProgressManager.executeProcessUnderProgress(ProgressManager.java:207)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:212)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.runProcess(ProgressManagerImpl.java:171)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8.run(AndroidLayoutPreviewToolWindowManager.java:368)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:320)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:310)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue$2.run(MergingUpdateQueue.java:254)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:269)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:227)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.run(MergingUpdateQueue.java:217)\n" +
            "\tat com.intellij.util.concurrency.QueueProcessor.runSafely(QueueProcessor.java:237)\n" +
            "\tat com.intellij.util.Alarm\$Request$1.run(Alarm.java:297)\n" +
            "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:439)\n" +
            "\tat java.util.concurrent.FutureTask\$Sync.innerRun(FutureTask.java:303)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:918)\n" +
            "\tat java.lang.Thread.run(Thread.java:680)\n"
        )
      logger.error(null, null, throwable, null, null)
      target.set(render.getRenderContext()!!.configuration.getRealTarget())
      assertTrue(logger.hasProblems())
    }

    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(1, issues)
    assertNotNull(target.get())
    val havePlatformSources = AndroidSdks.getInstance().findPlatformSources(target.get()!!) != null
    if (havePlatformSources) {
      assertHtmlEquals(
        "java.lang.ArithmeticException: / by zero<BR/>" +
          "&nbsp;&nbsp;at com.example.myapplication574.MyCustomView.&lt;init>(<A HREF=\"open:com.example.myapplication574.MyCustomView#<init>;MyCustomView.java:13\">MyCustomView.java:13</A>)<BR/>" +
          "&nbsp;&nbsp;at java.lang.reflect.Constructor.newInstance(Constructor.java:513)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate(LayoutInflater.java:727)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(LayoutInflater.java:492)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(LayoutInflater.java:373)<BR/>" +
          "<A HREF=\"\">Copy stack to clipboard</A>",
        issues[0]!!,
      )
      assertBottomPanelEquals(
        listOf(
          MessageTip(
            AllIcons.General.Information,
            "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
          )
        ),
        issues[0]!!,
      )
    } else {
      assertHtmlEquals(
        "java.lang.ArithmeticException: / by zero<BR/>" +
          "&nbsp;&nbsp;at com.example.myapplication574.MyCustomView.&lt;init>(<A HREF=\"open:com.example.myapplication574.MyCustomView#<init>;MyCustomView.java:13\">MyCustomView.java:13</A>)<BR/>" +
          "&nbsp;&nbsp;at java.lang.reflect.Constructor.newInstance(Constructor.java:513)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate(LayoutInflater.java:727)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(LayoutInflater.java:492)<BR/>" +
          "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(LayoutInflater.java:373)<BR/>" +
          "<A HREF=\"\">Copy stack to clipboard</A>",
        issues[0]!!,
      )
      assertBottomPanelEquals(
        listOf(
          MessageTip(
            AllIcons.General.Information,
            "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
          )
        ),
        issues[0]!!,
      )
    }
  }

  @Test
  fun testMismatchedBinary() {
    val path = FileUtil.toSystemDependentName("/foo/bar/baz.png")
    val operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      val throwable =
        ExceptionTestUtils.createExceptionFromDesc(
          "org.xmlpull.v1.XmlPullParserException: unterminated entity ref (position:TEXT \u0050PNG\u001A\u0000\u0000\u0000" +
            "IHDR\u0000...@8:38 in java.io.InputStreamReader@12caea1b)\n" +
            "\tat org.kxml2.io.KXmlParser.exception(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.error(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.pushEntity(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.pushText(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.nextImpl(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.next(Unknown Source)\n" +
            "\tat com.android.layoutlib.bridge.android.BridgeXmlBlockParser.next(BridgeXmlBlockParser.java:301)\n" +
            "\tat android.content.res.ColorStateList.createFromXml(ColorStateList.java:122)\n" +
            "\tat android.content.res.BridgeTypedArray.getColorStateList(BridgeTypedArray.java:373)\n" +
            "\tat android.widget.TextView.<init>(TextView.java:956)\n" +
            "\tat android.widget.Button.<init>(Button.java:107)\n" +
            "\tat android.widget.Button.<init>(Button.java:103)\n" +
            "\tat sun.reflect.GeneratedConstructorAccessor53.newInstance(Unknown Source)\n" +
            "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
            "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
            "\tat android.view.LayoutInflater.createView(LayoutInflater.java:594)\n" +
            "\tat android.view.BridgeInflater.onCreateView(BridgeInflater.java:86)\n" +
            "\tat android.view.LayoutInflater.onCreateView(LayoutInflater.java:669)\n" +
            "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:694)\n" +
            "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:131)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:758)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:758)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:373)\n" +
            "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:400)\n" +
            "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:336)\n" +
            "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:332)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:527)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:520)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:957)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.createRenderSession(RenderService.java:520)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.render(RenderService.java:557)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:418)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:968)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.doRender(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.access$1100(AndroidLayoutPreviewToolWindowManager.java:79)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8$1.run(AndroidLayoutPreviewToolWindowManager.java:373)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl$2.run(ProgressManagerImpl.java:178)\n" +
            "\tat com.intellij.openapi.progress.ProgressManager.executeProcessUnderProgress(ProgressManager.java:209)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:212)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.runProcess(ProgressManagerImpl.java:171)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8.run(AndroidLayoutPreviewToolWindowManager.java:368)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:320)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:310)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue$2.run(MergingUpdateQueue.java:254)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:269)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:227)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.run(MergingUpdateQueue.java:217)\n" +
            "\tat com.intellij.util.concurrency.QueueProcessor.runSafely(QueueProcessor.java:237)\n" +
            "\tat com.intellij.util.Alarm\$Request$1.run(Alarm.java:297)\n" +
            "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:439)\n" +
            "\tat java.util.concurrent.FutureTask\$Sync.innerRun(FutureTask.java:303)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:918)\n" +
            "\tat java.lang.Thread.run(Thread.java:680)\n"
        )
      logger.error(null, "Failed to configure parser for " + path, throwable, null, null)
    }

    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(1, issues)
    assertHtmlEquals(
      "Resource error: Attempted to load a bitmap as a color state list.<BR/>" +
        "Verify that your style/theme attributes are correct, and make sure layouts are using the right attributes.<BR/>" +
        "<BR/>" +
        "The relevant image is " +
        path +
        "<BR/>" +
        "<BR/>" +
        "Widgets possibly involved: Button, TextView<BR/>",
      issues[0]!!,
    )

    assertBottomPanelEquals(
      listOf(
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
        )
      ),
      issues[0]!!,
    )
  }

  @Test
  fun testSecurity() {
    val target = AtomicReference<IAndroidTarget?>()
    val operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      val throwable =
        ExceptionTestUtils.createExceptionFromDesc(
          "Read access not allowed during rendering\n" +
            "\tat com.android.ide.common.rendering.RenderSecurityException.create(RenderSecurityException.java:52)\n" +
            "\tat com.android.ide.common.rendering.RenderSecurityManager.checkRead(RenderSecurityManager.java:204)\n" +
            "\tat java.io.File.list(File.java:971)\n" +
            "\tat java.io.File.listFiles(File.java:1051)\n" +
            "\tat com.example.app.MyButton.onDraw(MyButton.java:70)\n" +
            "\tat android.view.View.draw(View.java:14433)\n" +
            "\tat android.view.View.draw(View.java:14318)\n" +
            "\tat android.view.ViewGroup.drawChild(ViewGroup.java:3103)\n" +
            "\tat android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)\n" +
            "\tat android.view.View.draw(View.java:14316)\n" +
            "\tat android.view.ViewGroup.drawChild(ViewGroup.java:3103)\n" +
            "\tat android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)\n" +
            "\tat android.view.View.draw(View.java:14316)\n" +
            "\tat android.view.ViewGroup.drawChild(ViewGroup.java:3103)\n" +
            "\tat android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)\n" +
            "\tat android.view.View.draw(View.java:14436)\n" +
            "\tat android.view.View.draw(View.java:14318)\n" +
            "\tat android.view.ViewGroup.drawChild(ViewGroup.java:3103)\n" +
            "\tat android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)\n" +
            "\tat android.view.View.draw(View.java:14436)\n" +
            "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.render(RenderSessionImpl.java:584)\n" +
            "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:338)\n" +
            "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:332)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:546)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:536)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:934)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.createRenderSession(RenderService.java:536)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.render(RenderService.java:599)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$7.compute(AndroidLayoutPreviewToolWindowManager.java:577)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$7.compute(AndroidLayoutPreviewToolWindowManager.java:570)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:945)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.doRender(AndroidLayoutPreviewToolWindowManager.java:570)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.access$1700(AndroidLayoutPreviewToolWindowManager.java:83)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$6$1.run(AndroidLayoutPreviewToolWindowManager.java:518)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl$2.run(ProgressManagerImpl.java:178)\n" +
            "\tat com.intellij.openapi.progress.ProgressManager.executeProcessUnderProgress(ProgressManager.java:209)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:212)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.runProcess(ProgressManagerImpl.java:171)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$6.run(AndroidLayoutPreviewToolWindowManager.java:513)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:320)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:310)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue$2.run(MergingUpdateQueue.java:254)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:269)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:227)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.run(MergingUpdateQueue.java:217)\n" +
            "\tat com.intellij.util.concurrency.QueueProcessor.runSafely(QueueProcessor.java:238)\n" +
            "\tat com.intellij.util.Alarm\$Request$1.run(Alarm.java:297)\n" +
            "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:439)\n" +
            "\tat java.util.concurrent.FutureTask\$Sync.innerRun(FutureTask.java:303)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:918)\n" +
            "\tat java.lang.Thread.run(Thread.java:695)\n",
          RenderSecurityException.create("Read", null),
        )
      logger.error(null, null, throwable, null, null)
      target.set(render.getRenderContext()!!.configuration.getRealTarget())
    }

    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(1, issues)

    assertNotNull(target.get())
    val havePlatformSources = AndroidSdks.getInstance().findPlatformSources(target.get()!!) != null
    if (havePlatformSources) {
      assertHtmlEquals(
        "<A HREF=\"disableSandbox:\">Turn off custom view rendering sandbox</A><BR/>" +
          "<BR/>" +
          "Read access not allowed during rendering (/)<BR/>" +
          "&nbsp;&nbsp;at com.android.ide.common.rendering.RenderSecurityException.create(RenderSecurityException.java:52)<BR/>" +
          "&nbsp;&nbsp;at com.android.ide.common.rendering.RenderSecurityManager.checkRead(RenderSecurityManager.java:204)<BR/>" +
          "&nbsp;&nbsp;at java.io.File.list(File.java:971)<BR/>" +
          "&nbsp;&nbsp;at java.io.File.listFiles(File.java:1051)<BR/>" +
          "&nbsp;&nbsp;at com.example.app.MyButton.onDraw(<A HREF=\"open:com.example.app.MyButton#onDraw;MyButton.java:70\">MyButton.java:70</A>)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://\$SDK_HOME/sources/android-XX/android/view/View.java:14433\">View.java:14433</A>)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://\$SDK_HOME/sources/android-XX/android/view/View.java:14318\">View.java:14318</A>)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://\$SDK_HOME/sources/android-XX/android/view/View.java:14316\">View.java:14316</A>)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://\$SDK_HOME/sources/android-XX/android/view/View.java:14316\">View.java:14316</A>)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://\$SDK_HOME/sources/android-XX/android/view/View.java:14436\">View.java:14436</A>)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://\$SDK_HOME/sources/android-XX/android/view/View.java:14318\">View.java:14318</A>)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://\$SDK_HOME/sources/android-XX/android/view/View.java:14436\">View.java:14436</A>)<BR/>" +
          "<A HREF=\"\">Copy stack to clipboard</A>",
        issues[0]!!,
      )
      assertBottomPanelEquals(
        listOf(
          MessageTip(
            AllIcons.General.Information,
            "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
          )
        ),
        issues[0]!!,
      )
    } else {
      assertHtmlEquals(
        "<A HREF=\"disableSandbox:\">Turn off custom view rendering sandbox</A><BR/>" +
          "<BR/>" +
          "Read access not allowed during rendering<BR/>" +
          "&nbsp;&nbsp;at com.android.ide.common.rendering.RenderSecurityException.create(RenderSecurityException.java:52)<BR/>" +
          "&nbsp;&nbsp;at com.android.ide.common.rendering.RenderSecurityManager.checkRead(RenderSecurityManager.java:204)<BR/>" +
          "&nbsp;&nbsp;at java.io.File.list(File.java:971)<BR/>" +
          "&nbsp;&nbsp;at java.io.File.listFiles(File.java:1051)<BR/>" +
          "&nbsp;&nbsp;at com.example.app.MyButton.onDraw(<A HREF=\"open:com.example.app.MyButton#onDraw;MyButton.java:70\">MyButton.java:70</A>)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(View.java:14433)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(View.java:14318)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(View.java:14316)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(View.java:14316)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(View.java:14436)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(View.java:14318)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
          "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
          "&nbsp;&nbsp;at android.view.View.draw(View.java:14436)<BR/>" +
          "<A HREF=\"\">Copy stack to clipboard</A>",
        issues[0]!!,
      )
      assertBottomPanelEquals(
        listOf(
          MessageTip(
            AllIcons.General.Information,
            "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
          )
        ),
        issues[0]!!,
      )
    }
  }

  @Test
  fun testFidelityErrors() {
    var operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      logger.fidelityWarning("Fidelity", "Fidelity issue", null, null, null)
    }
    var issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(1, issues)
    assertHtmlEquals(
      "The graphics preview in the layout editor may not be accurate:<BR/>" +
        "<DL><DD>-&nbsp;Fidelity issue <A HREF=\"\">(Ignore for this session)</A>" +
        "<BR/></DL><A HREF=\"\">Ignore all fidelity warnings for this session</A><BR/>",
      issues[0]!!,
    )

    operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      logger.fidelityWarning("Fidelity", "Fidelity issue", null, null, null)
      logger.error("Error", "An error", null, null)
    }
    issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(2, issues)
    // The ERROR should go first in the list (higher priority)
    assertHtmlEquals(
      "The graphics preview in the layout editor may not be accurate:<BR/>" +
        "<DL><DD>-&nbsp;Fidelity issue <A HREF=\"\">(Ignore for this session)</A>" +
        "<BR/></DL><A HREF=\"\">Ignore all fidelity warnings for this session</A><BR/>",
      issues.get(1)!!,
    )
    assertBottomPanelEquals(
      listOf(
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
        )
      ),
      issues[0]!!,
    )
  }

  @Test
  fun testRefreshOnInstantiationFailure() {
    val operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      val throwable =
        ExceptionTestUtils.createExceptionFromDesc(
          "java.lang.ArithmeticException: / by zero\n" +
            "\tat com.example.myapplication.MyButton.<init>(MyButton.java:14)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)\n" +
            "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)\n" + /* Removed to avoid including source links in the annotated output
                                                                                                                            "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:423)\n" +
                                                                                                                            */
            "\tat org.jetbrains.android.uipreview.ViewLoader.createNewInstance(ViewLoader.java:465)\n" +
            "\tat org.jetbrains.android.uipreview.ViewLoader.loadClass(ViewLoader.java:172)\n" +
            "\tat org.jetbrains.android.uipreview.ViewLoader.loadView(ViewLoader.java:105)\n" +
            "\tat com.android.tools.idea.rendering.LayoutlibCallbackImpl.loadView(LayoutlibCallbackImpl.java:186)\n" +
            "\tat android.view.BridgeInflater.loadCustomView(BridgeInflater.java:312)\n" +
            "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:233)\n" + /* Removed to avoid including source links in the annotated output
                                                                                              "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:727)\n" +
                                                                                              "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:495)\n" +
                                                                                              "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:397)\n" +
                                                                                              */
            "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:324)\n" +
            "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:429)\n" +
            "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:389)\n" +
            "\tat com.android.tools.idea.rendering.RenderTask$2.compute(RenderTask.java:548)\n" +
            "\tat com.android.tools.idea.rendering.RenderTask$2.compute(RenderTask.java:533)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:966)\n" +
            "\tat com.android.tools.idea.rendering.RenderTask.createRenderSession(RenderTask.java:533)\n" +
            "\tat com.android.tools.idea.rendering.RenderTask.lambda\$inflate$1(RenderTask.java:659)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:617)\n" +
            "\tat java.lang.Thread.run(Thread.java:745)\n"
        )
      logger.addBrokenClass("com.example.myapplication.MyButton", throwable)
    }

    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(1, issues)
    assertHtmlEquals(
      "The following classes could not be instantiated:<DL><DD>-&nbs" +
        "p;com.example.myapplication.MyButton (<A HREF=\"openClass:com." +
        "example.myapplication.MyButton\">Open Class</A>, <A HREF=\"" +
        "\">Show Exception</A>, <A HREF=\"clearCacheAndNotify\">Clear Cac" +
        "he</A>)<BR/><BR/><font style=\"font-weight:bold; color:#005" +
        "555;\">Exception Details</font><BR/>java.lang.ArithmeticExcept" +
        "ion: / by zero<BR/>&nbsp;&nbsp;at com.example.myapplication.M" +
        "yButton.&lt;init>(<A HREF=\"open:com.example.myapplication.MyB" +
        "utton#<init>;MyButton.java:14\">MyButton.java:14</A>)<BR/><A H" +
        "REF=\"\">Copy stack to clipboard</A><BR/><BR/>",
      issues[0]!!,
    )
    assertBottomPanelEquals(
      listOf(
        MessageTip(
          AllIcons.General.Information,
          "Tip: Use <A HREF=\"http://developer.android.com/reference/android/view/View.html#isInEditMode()\">View.isInEditMode()</A> in your custom views to skip code or show sample data when shown in the IDE.<BR/>If this is an unexpected error you can also try to <A HREF=\"action:build\">build the project</A>, then manually <A HREF=\"refreshRender\">refresh the layout</A>.",
        )
      ),
      issues[0]!!,
    )
  }

  @Test
  fun testAppCompatException() {
    val operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      val throwable =
        ExceptionTestUtils.createExceptionFromDesc(
          "java.lang.IllegalArgumentException: You need to use a Theme.AppCompat theme (or descendant) with the design library.\n" +
            "\tat android.support.design.widget.ThemeUtils.checkAppCompatTheme(ThemeUtils.java:36)\n" +
            "\tat android.support.design.widget.FloatingActionButton.<init>(FloatingActionButton.java:159)\n" +
            "\tat android.support.design.widget.FloatingActionButton.<init>(FloatingActionButton.java:153)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)\n" +
            "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)\n" +
            "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:423)\n" +
            "\tat org.jetbrains.android.uipreview.ViewLoader.createNewInstance(ViewLoader.java:488)\n" +
            "\tat org.jetbrains.android.uipreview.ViewLoader.loadClass(ViewLoader.java:266)\n" +
            "\tat org.jetbrains.android.uipreview.ViewLoader.loadView(ViewLoader.java:224)\n" +
            "\tat com.android.tools.idea.rendering.LayoutlibCallbackImpl.loadView(LayoutlibCallbackImpl.java:189)\n" +
            "\tat android.view.BridgeInflater.loadCustomView(BridgeInflater.java:337)\n" +
            "\tat android.view.BridgeInflater.loadCustomView(BridgeInflater.java:348)\n" +
            "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:248)\n" +
            "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:860)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:72)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:834)\n" +
            "\tat android.view.LayoutInflater.rInflateChildren(LayoutInflater.java:821)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:518)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:397)\n" +
            "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:345)\n" +
            "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:431)\n" +
            "\tat com.android.tools.idea.layoutlib.LayoutLibrary.createSession(LayoutLibrary.java:193)\n" +
            "\tat com.android.tools.idea.rendering.RenderTask.createRenderSession(RenderTask.java:591)\n" +
            "\tat com.android.tools.idea.rendering.RenderTask.lambda\$inflate$3(RenderTask.java:739)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:617)\n" +
            "\tat java.lang.Thread.run(Thread.java:745)\n"
        )
      logger.addBrokenClass("com.example.myapplication.MyButton", throwable)
    }

    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(1, issues)
    assertHtmlEquals(
      "Select <I>Theme.AppCompat</I> or a descendant in the theme selector.",
      issues[0]!!,
    )
  }

  /**
   * Regression test for b/149357583
   *
   * The [RenderErrorContributor] should not throw an
   * [com.intellij.openapi.project.IndexNotReadyException] when executed in dumb mode.
   */
  @Test
  fun testDumbModeRenderErrorContributor() {
    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml"),
        null,
        true,
      )
    assertSize(1, issues)
    assertHtmlEquals(
      "The following classes could not be found:<DL>" +
        "<DD>-&nbsp;Bitton (<A HREF=\"action:classpath\">Fix Build Path</A>" +
        ", <A HREF=\"showTag:Bitton\">Edit XML</A>)</DL>",
      issues[0]!!,
    )
    assertBottomPanelEquals(
      listOf(
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"action:buildForRendering\">Build</A> the module.",
        ),
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"action:build\">Build</A> the project.",
        ),
        MessageTip(
          AllIcons.General.Information,
          "Tip: <A HREF=\"refreshRender\">Build &amp; Refresh</A> the preview.",
        ),
      ),
      issues[0]!!,
    )
  }

  /** Duplicate errors are ignored. */
  @Test
  fun testNoDuplicateIssues() {
    val operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      // MANUALLY register errors
      logger.addMessage(RenderProblem.createPlain(ProblemSeverity.ERROR, "Error 1"))
      logger.addMessage(RenderProblem.createPlain(ProblemSeverity.WARNING, "Warning 1"))
      logger.addMessage(RenderProblem.createPlain(ProblemSeverity.WARNING, "Warning 1"))
      logger.addMessage(RenderProblem.createPlain(ProblemSeverity.ERROR, "Error 1"))
    }

    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(2, issues)
  }

  /** Tests that the RenderErrorContributor builds issues using the correct severity */
  @Test
  fun testIssueSeverity() {
    val operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      // MANUALLY register errors
      logger.addMessage(RenderProblem.createPlain(ProblemSeverity.ERROR, "Error"))
      logger.addMessage(RenderProblem.createPlain(ProblemSeverity.WARNING, "Warning"))
    }

    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(2, issues)
    assertEquals(HighlightSeverity.ERROR, issues[0]!!.severity)
    assertEquals(HighlightSeverity.WARNING, issues[1]!!.severity)
  }

  /**
   * Tests that the RenderErrorContributor builds the correct help message for errors with data
   * binding.
   */
  @Test
  fun testDataBindingIssue() {
    val operation = LogOperation { logger: RenderLogger, render: RenderResult ->
      val throwable =
        ExceptionTestUtils.createExceptionFromDesc(
          "java.lang.ClassNotFoundException: androidx.databinding.DataBinderMapperImpl\n" +
            "\tat com.android.tools.rendering.classloading.loaders.DelegatingClassLoader.findClass(DelegatingClassLoader.kt:76)\n" +
            "\tat java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:592)\n" +
            "\tat java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:525)\n" +
            "\tat com.android.tools.rendering.classloading.loaders.DelegatingClassLoader.loadClass(DelegatingClassLoader.kt:62)\n" +
            "\tat com.example.module.databinding.ViewTestBinding.inflate(ViewTestBinding.java:23)\n" +
            "\tat com.example.module.TestCustomView.<init>(TestCustomView.kt:15)\n" +
            "\tat com.example.module.TestCustomView.<init>(TestCustomView.kt:10)\n" +
            "\tat java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
            "\tat java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:77)\n" +
            "\tat java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)\n" +
            "\tat java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:499)\n" +
            "\tat java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:480)\n" +
            "\tat com.android.tools.rendering.ViewLoader.createNewInstance(ViewLoader.java:292)\n" +
            "\tat com.android.tools.rendering.ViewLoader.loadClass(ViewLoader.java:155)\n" +
            "\tat com.android.tools.rendering.ViewLoader.loadView(ViewLoader.java:116)\n" +
            "\tat com.android.tools.rendering.LayoutlibCallbackImpl.loadView(LayoutlibCallbackImpl.java:280)\n" +
            "\tat android.view.BridgeInflater.loadCustomView(BridgeInflater.java:429)\n" +
            "\tat android.view.BridgeInflater.loadCustomView(BridgeInflater.java:440)\n" +
            "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:344)\n" +
            "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:973)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:1135)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:72)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:1109)\n" +
            "\tat android.view.LayoutInflater.rInflateChildren(LayoutInflater.java:1096)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:694)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:505)\n" +
            "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:365)\n" +
            "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:454)\n" +
            "\tat com.android.tools.idea.layoutlib.LayoutLibrary.createSession(LayoutLibrary.java:120)\n" +
            "\tat com.android.tools.rendering.RenderTask.createRenderSession(RenderTask.java:777)\n" +
            "\tat com.android.tools.rendering.RenderTask.lambda\$inflate$6(RenderTask.java:925)\n" +
            "\tat com.android.tools.rendering.RenderExecutor\$runAsyncActionWithTimeout$3.run(RenderExecutor.kt:203)\n" +
            "\tat com.android.tools.rendering.RenderExecutor\$PriorityRunnable.run(RenderExecutor.kt:317)\n" +
            "\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)\n" +
            "\tat java.base/java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:635)\n" +
            "\tat java.base/java.lang.Thread.run(Thread.java:840)\n"
        )
      logger.addBrokenClass("com.example.module.TestCustomView", throwable)
    }

    val issues =
      getRenderOutput(
        fixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"),
        operation,
      )
    assertSize(1, issues)
    Truth.assertThat(issues[0]!!.htmlContent)
      .startsWith(
        "<I>androidx.databinding.DataBinderMapperImpl</I> class could not be found." +
          "<BR/>This is likely caused by trying to render a layout using data binding directly " +
          "from a library module.<BR/><BR/>The following classes"
      )
  }

  private fun stripSdkHome(html: String): String {
    var html = html
    val platform = getInstance(module)
    assertNotNull(platform)
    var location = platform!!.getSdkData().getLocation().toString()
    location = FileUtil.toSystemIndependentName(location)
    html =
      html
        .replace(location, "\$SDK_HOME")
        .replace("file:///", "file://") // On Windows JavaDoc source may start with /
    return html
  }

  private fun stripSdkVersion(html: String): String {
    return html.replace("android-\\d\\d".toRegex(), "android-XX")
  }

  // We should just use assertEquals(String,String) here, wch works well when running unit tests
  // in the IDE; you can double click on the failed assertion and see a full diff of the two
  // strings. However, the junit result shown from Jenkins shows a spectacularly bad diff of
  // the two strings, so we print our own diff first two to help diagnose server side failures.
  private fun assertHtmlEquals(expected: String, issue: RenderErrorModel.Issue) {
    var expected = expected
    var actual = issue.getHtmlContent()
    actual = stripSdkHome(actual)
    actual = stripSdkVersion(actual)

    if (expected != actual) {
      println("Render unit test failed: " + testNameRule.methodName)
      println("Output diff:\n" + TestUtils.getDiff(expected, actual))
    }

    // Visually diffing very long lines is hard. Let's insert some newlines in the deltas that we're
    // comparing (also helps with junit diff output)
    val newlineModulo = 60
    expected = injectNewlines(expected, newlineModulo)
    actual = injectNewlines(actual, newlineModulo)

    TestCase.assertEquals(expected, actual)
  }

  private fun assertBottomPanelEquals(
    expectedMessageTips: List<MessageTip>,
    issue: RenderErrorModel.Issue,
  ) {
    val actualMessageTips = issue.messageTip

    TestCase.assertEquals(expectedMessageTips.size, actualMessageTips.size)
    for (i in actualMessageTips.indices) {
      val actual = actualMessageTips[i]
      val expected = expectedMessageTips[i]
      assertEquals(expected.icon, actual.icon)
      TestCase.assertEquals(expected.htmlText, actual.htmlText)
    }
  }

  private fun interface LogOperation {
    fun addErrors(logger: RenderLogger, render: RenderResult)
  }

  companion object {
    const val BASE_PATH: String = "render/"

    @Suppress("SameParameterValue")
    private fun injectNewlines(s: String, newlineModulo: Int): String {
      val sb = StringBuilder()
      var i = 0
      var count = 0
      while (i < s.length) {
        sb.append(s.get(i))
        if (count == newlineModulo) {
          sb.append('\n')
          count = -1
        }
        i++
        count++
      }
      return sb.toString()
    }

    private fun getTestTarget(configurationManager: ConfigurationManager): IAndroidTarget? {
      val platformDir = TestUtils.getLatestAndroidPlatform()
      for (target in configurationManager.targets) {
        if (!target.isLayoutLibTarget) {
          continue
        }
        val path = target.getPath(IAndroidTarget.ANDROID_JAR)
        if (path.getParent() != null && path.getParent().getFileName().toString() == platformDir) {
          return target
        }
      }

      return null
    }
  }
}
