/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import static com.android.tools.idea.diagnostics.ExceptionTestUtils.createExceptionFromDesc;

import com.android.sdklib.IAndroidTarget;
import com.android.testutils.TestUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.util.concurrent.Futures;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RenderErrorContributorTest extends AndroidTestCase {
  public static final String BASE_PATH = "render/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    RenderTestUtil.beforeRenderTestCase();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderTestUtil.afterRenderTestCase();
    } finally {
      super.tearDown();
    }
  }

  // Image paths will include full resource urls which depends on the test environment
  public static String stripImages(@NotNull String html) {
    while (true) {
      int index = html.indexOf("<img");
      if (index == -1) {
        return html;
      }
      int end = html.indexOf('>', index);
      if (end == -1) {
        return html;
      }
      else {
        html = html.substring(0, index) + html.substring(end + 1);
      }
    }
  }

  private static String injectNewlines(String s, int newlineModulo) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0, count = 0; i < s.length(); i++, count++) {
      sb.append(s.charAt(i));
      if (count == newlineModulo) {
        sb.append('\n');
        //noinspection AssignmentToForLoopParameter
        count = -1;
      }
    }
    return sb.toString();
  }

  @Nullable
  private static IAndroidTarget getTestTarget(@NotNull ConfigurationManager configurationManager) {
    String platformDir = TestUtils.getLatestAndroidPlatform();
    for (IAndroidTarget target : configurationManager.getTargets()) {
      if (!ConfigurationManager.isLayoutLibTarget(target)) {
        continue;
      }
      Path path = target.getPath(IAndroidTarget.ANDROID_JAR);
      if (path.getParent() != null && path.getParent().getFileName().toString().equals(platformDir)) {
        return target;
      }
    }

    return null;
  }

  /**
   * Obtains the render issues for the given layout
   * @param file the layout file
   * @param logOperation optional {@link LogOperation} to intercept rendering errors
   */
  @NotNull
  private List<RenderErrorModel.Issue> getRenderOutput(@NotNull VirtualFile file, @Nullable LogOperation logOperation) {
    return getRenderOutput(file, logOperation, false);
  }

  /**
   * Obtains the render issues for the given layout
   * @param file the layout file
   * @param logOperation optional {@link LogOperation} to intercept rendering errors
   * @param useDumbMode when true, the render error model will be generated in dumb mode
   */
  @NotNull
  private List<RenderErrorModel.Issue> getRenderOutput(
    @NotNull VirtualFile file,
    @Nullable LogOperation logOperation,
    boolean useDumbMode) {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    IAndroidTarget target = getTestTarget(configurationManager);
    assertNotNull(target);
    configurationManager.setTarget(target);
    Configuration configuration = configurationManager.getConfiguration(file);
    assertSame(target, configuration.getRealTarget());

    RenderService renderService = RenderService.getInstance(myModule.getProject());
    RenderLogger logger = renderService.createLogger(myModule);
    List<RenderErrorModel.Issue> issues = new ArrayList<>();

    RenderTestUtil.withRenderTask(facet, file, configuration, logger, task -> {
      RenderResult render = RenderTestUtil.renderOnSeparateThread(task);
      assertNotNull(render);

      if (logOperation != null) {
        logOperation.addErrors(logger, render);
      }

      if (useDumbMode) {
        DumbServiceImpl.getInstance(myFixture.getProject()).setDumb(true);
      }

      try {
        // The error model must be created on a background thread.
        Future<RenderErrorModel> errorModel = ApplicationManager.getApplication().executeOnPooledThread(
          () -> RenderErrorModelFactory.createErrorModel(null, render, null));

        Futures.getUnchecked(errorModel).getIssues().stream().sorted().forEachOrdered(issues::add);
      }
      finally {
        if (useDumbMode) {
          DumbServiceImpl.getInstance(myFixture.getProject()).setDumb(false);
        }
      }
    });
    return issues;
  }

  public void testPanel() {
    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml"), null);
    assertSize(2, issues);
    assertHtmlEquals(
      "The following classes could not be found:<DL>" +
      "<DD>-&NBSP;LinerLayout (<A HREF=\"replaceTags:LinerLayout/LinearLayout\">Change to LinearLayout</A>" +
      ", <A HREF=\"action:classpath\">Fix Build Path</A>" +
      ", <A HREF=\"showTag:LinerLayout\">Edit XML</A>)" +
      "</DL>Tip: Try to <A HREF=\"action:build\">build</A> the project.<BR/>" +
      "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout." +
      "<BR/>", issues.get(0));
    assertHtmlEquals(
      "<B>NOTE: One or more layouts are missing the layout_width or layout_height attributes. These are required in most layouts.</B><BR/>" +
      "&lt;LinearLayout> does not set the required layout_width attribute: <BR/>" +
      "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"command:0\">Set to wrap_content</A>, <A HREF=\"command:1\">Set to match_parent</A><BR/>" +
      "&lt;LinearLayout> does not set the required layout_height attribute: <BR/>" +
      "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"command:2\">Set to wrap_content</A>, <A HREF=\"command:3\">Set to match_parent</A><BR/>" +
      "<BR/>" +
      "Or: <A HREF=\"command:4\">Automatically add all missing attributes</A><BR/><BR/><BR/>", issues.get(1));
  }

  public void testDataBindingAttributes() {
    List<RenderErrorModel.Issue> issues = getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "db.xml", "res/layout/db.xml"), null);
    assertSize(2, issues);
    assertHtmlEquals(
      "The following classes could not be found:<DL>" +
      "<DD>-&NBSP;LinerLayout (<A HREF=\"replaceTags:LinerLayout/LinearLayout\">Change to LinearLayout</A>" +
      ", <A HREF=\"action:classpath\">Fix Build Path</A>" +
      ", <A HREF=\"showTag:LinerLayout\">Edit XML</A>)" +
      "</DL>Tip: Try to <A HREF=\"action:build\">build</A> the project.<BR/>" +
      "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout." +
      "<BR/>", issues.get(0));
    assertHtmlEquals(
      "<B>NOTE: One or more layouts are missing the layout_width or layout_height attributes. These are required in most layouts.</B><BR/>" +
      "&lt;LinearLayout> does not set the required layout_width attribute: <BR/>" +
      "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"command:0\">Set to wrap_content</A>, <A HREF=\"command:1\">Set to match_parent</A><BR/>" +
      "&lt;LinearLayout> does not set the required layout_height attribute: <BR/>" +
      "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"command:2\">Set to wrap_content</A>, <A HREF=\"command:3\">Set to match_parent</A><BR/>" +
      "<BR/>" +
      "Or: <A HREF=\"command:4\">Automatically add all missing attributes</A><BR/><BR/><BR/>", issues.get(1));
  }

  public void testTypo() {
    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml"), null);
    assertSize(1, issues);
    assertHtmlEquals(
      "The following classes could not be found:<DL>" +
      "<DD>-&NBSP;Bitton (<A HREF=\"replaceTags:Bitton/Button\">Change to Button</A>" +
      ", <A HREF=\"action:classpath\">Fix Build Path</A>" +
      ", <A HREF=\"showTag:Bitton\">Edit XML</A>)" +
      "</DL>Tip: Try to <A HREF=\"action:build\">build</A> the project.<BR/>" +
      "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/>",
      issues.get(0));
  }

  public void testBrokenCustomView() {
    final AtomicReference<IAndroidTarget> target = new AtomicReference<>();
    LogOperation operation = (logger, render) -> {
      Throwable throwable = createExceptionFromDesc(
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
        "\tat com.intellij.util.Alarm$Request$1.run(Alarm.java:297)\n" +
        "\tat java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:439)\n" +
        "\tat java.util.concurrent.FutureTask$Sync.innerRun(FutureTask.java:303)\n" +
        "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
        "\tat java.lang.Thread.run(Thread.java:680)\n");
      logger.error(null, null, throwable, null, null);
      //noinspection ConstantConditions
      target.set(render.getRenderContext().getConfiguration().getRealTarget());

      assertTrue(logger.hasProblems());
    };

    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
    assertSize(1, issues);
    assertNotNull(target.get());
    boolean havePlatformSources = AndroidSdks.getInstance().findPlatformSources(target.get()) != null;
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
        "<A HREF=\"runnable:1\">Copy stack to clipboard</A><BR/>" +
        "<BR/>Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/>", issues.get(0));
    }
    else {
      assertHtmlEquals(
        "java.lang.ArithmeticException: / by zero<BR/>" +
        "&nbsp;&nbsp;at com.example.myapplication574.MyCustomView.&lt;init>(<A HREF=\"open:com.example.myapplication574.MyCustomView#<init>;MyCustomView.java:13\">MyCustomView.java:13</A>)<BR/>" +
        "&nbsp;&nbsp;at java.lang.reflect.Constructor.newInstance(Constructor.java:513)<BR/>" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)<BR/>" +
        "&nbsp;&nbsp;at android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)<BR/>" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.rInflate(LayoutInflater.java:727)<BR/>" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(LayoutInflater.java:492)<BR/>" +
        "&nbsp;&nbsp;at android.view.LayoutInflater.inflate(LayoutInflater.java:373)<BR/>" +
        "<A HREF=\"runnable:1\">Copy stack to clipboard</A><BR/><BR/>" +
        "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/>", issues.get(0));
    }
  }

  public void testMismatchedBinary() throws Exception {
    final String path = FileUtil.toSystemDependentName("/foo/bar/baz.png");
    LogOperation operation = (logger, render) -> {
      Throwable throwable = createExceptionFromDesc(
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
        "\tat com.intellij.util.Alarm$Request$1.run(Alarm.java:297)\n" +
        "\tat java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:439)\n" +
        "\tat java.util.concurrent.FutureTask$Sync.innerRun(FutureTask.java:303)\n" +
        "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
        "\tat java.lang.Thread.run(Thread.java:680)\n");
      logger.error(null, "Failed to configure parser for " + path, throwable, null, null);
    };

    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
    assertSize(1, issues);
    assertHtmlEquals(
      "Resource error: Attempted to load a bitmap as a color state list.<BR/>" +
      "Verify that your style/theme attributes are correct, and make sure layouts are using the right attributes.<BR/>" +
      "<BR/>" +
      "The relevant image is " + path + "<BR/>" +
      "<BR/>" +
      "Widgets possibly involved: Button, TextView<BR/>" +
      "<BR/>" +
      "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/>", issues.get(0));
  }

  public void testSecurity() throws Exception {
    final AtomicReference<IAndroidTarget> target = new AtomicReference<>();
    LogOperation operation = (logger, render) -> {
      Throwable throwable = createExceptionFromDesc(
        "Read access not allowed during rendering (/)\n" +
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
        "\tat com.intellij.util.Alarm$Request$1.run(Alarm.java:297)\n" +
        "\tat java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:439)\n" +
        "\tat java.util.concurrent.FutureTask$Sync.innerRun(FutureTask.java:303)\n" +
        "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
        "\tat java.lang.Thread.run(Thread.java:695)\n",
        RenderSecurityException.create("Read access not allowed during rendering (/)"));

      logger.error(null, null, throwable, null, null);

      //noinspection ConstantConditions
      target.set(render.getRenderContext().getConfiguration().getRealTarget());
    };

    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
    assertSize(1, issues);

    assertNotNull(target.get());
    boolean havePlatformSources = AndroidSdks.getInstance().findPlatformSources(target.get()) != null;
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
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-XX/android/view/View.java:14433\">View.java:14433</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-XX/android/view/View.java:14318\">View.java:14318</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-XX/android/view/View.java:14316\">View.java:14316</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-XX/android/view/View.java:14316\">View.java:14316</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-XX/android/view/View.java:14436\">View.java:14436</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-XX/android/view/View.java:14318\">View.java:14318</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-XX/android/view/View.java:14436\">View.java:14436</A>)<BR/>" +
        "<A HREF=\"runnable:1\">Copy stack to clipboard</A><BR/>" +
        "<BR/>" +
        "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/>", issues.get(0));
    }
    else {
      assertHtmlEquals(
        "<A HREF=\"disableSandbox:\">Turn off custom view rendering sandbox</A><BR/>" +
        "<BR/>" +
        "Read access not allowed during rendering (/)<BR/>" +
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
        "<A HREF=\"runnable:1\">Copy stack to clipboard</A><BR/><BR/>" +
        "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/>", issues.get(0));
    }
  }

  public void testFidelityErrors() {
    LogOperation operation = (logger, render) -> logger.fidelityWarning("Fidelity", "Fidelity issue", null, null, null);
    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
    assertSize(1, issues);
    assertHtmlEquals("The graphics preview in the layout editor may not be accurate:<BR/>" +
                     "<DL><DD>-&NBSP;Fidelity issue <A HREF=\"runnable:0\">(Ignore for this session)</A>" +
                     "<BR/></DL><A HREF=\"runnable:1\">Ignore all fidelity warnings for this session</A><BR/>", issues.get(0));

    operation = (logger, render) -> {
      logger.fidelityWarning("Fidelity", "Fidelity issue", null, null, null);
      logger.error("Error", "An error", null, null);
    };
    issues = getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
    assertSize(2, issues);
    // The ERROR should go first in the list (higher priority)
    assertHtmlEquals("An error<BR/><BR/>Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/>", issues.get(0));
    assertHtmlEquals("The graphics preview in the layout editor may not be accurate:<BR/>" +
                     "<DL><DD>-&NBSP;Fidelity issue <A HREF=\"runnable:0\">(Ignore for this session)</A>" +
                     "<BR/></DL><A HREF=\"runnable:1\">Ignore all fidelity warnings for this session</A><BR/>", issues.get(1));
  }

  //
  public void testRefreshOnInstantiationFailure() throws Exception {
    LogOperation operation = (logger, render) -> {
      Throwable throwable = createExceptionFromDesc(
        "java.lang.ArithmeticException: / by zero\n" +
        "\tat com.example.myapplication.MyButton.<init>(MyButton.java:14)\n" +
        "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
        "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)\n" +
        "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)\n" +
        /* Removed to avoid including source links in the annotated output
        "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:423)\n" +
        */
        "\tat org.jetbrains.android.uipreview.ViewLoader.createNewInstance(ViewLoader.java:465)\n" +
        "\tat org.jetbrains.android.uipreview.ViewLoader.loadClass(ViewLoader.java:172)\n" +
        "\tat org.jetbrains.android.uipreview.ViewLoader.loadView(ViewLoader.java:105)\n" +
        "\tat com.android.tools.idea.rendering.LayoutlibCallbackImpl.loadView(LayoutlibCallbackImpl.java:186)\n" +
        "\tat android.view.BridgeInflater.loadCustomView(BridgeInflater.java:312)\n" +
        "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:233)\n" +
        /* Removed to avoid including source links in the annotated output
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
        "\tat com.android.tools.idea.rendering.RenderTask.lambda$inflate$1(RenderTask.java:659)\n" +
        "\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)\n" +
        "\tat java.lang.Thread.run(Thread.java:745)\n");
      logger.addBrokenClass("com.example.myapplication.MyButton", throwable);
    };

    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
    assertSize(1, issues);
    assertHtmlEquals("The following classes could not be instantiated:<DL><DD>-&NBS" +
                     "P;com.example.myapplication.MyButton (<A HREF=\"openClass:com." +
                     "example.myapplication.MyButton\">Open Class</A>, <A HREF=\"runn" +
                     "able:0\">Show Exception</A>, <A HREF=\"clearCacheAndNotify\">Clear Cac" +
                     "he</A>)</DL>Tip: Use <A HREF=\"http://developer.android.com/re" +
                     "ference/android/view/View.html#isInEditMode()\">View.isInEditM" +
                     "ode()</A> in your custom views to skip code or show sample da" +
                     "ta when shown in the IDE.<BR/><BR/>If this is an unexpected e" +
                     "rror you can also try to <A HREF=\"action:build\">build the pro" +
                     "ject</A>, then manually <A HREF=\"refreshRender\">refresh the l" +
                     "ayout</A>.<BR/><BR/><font style=\"font-weight:bold; color:#005" +
                     "555;\">Exception Details</font><BR/>java.lang.ArithmeticExcept" +
                     "ion: / by zero<BR/>&nbsp;&nbsp;at com.example.myapplication.M" +
                     "yButton.&lt;init>(<A HREF=\"open:com.example.myapplication.MyB" +
                     "utton#<init>;MyButton.java:14\">MyButton.java:14</A>)<BR/><A H" +
                     "REF=\"runnable:1\">Copy stack to clipboard</A><BR/><BR/>", issues.get(0));
  }

  public void testAppCompatException() {
    LogOperation operation = (logger, render) -> {
      Throwable throwable = createExceptionFromDesc(
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
        "\tat com.android.tools.idea.rendering.RenderTask.lambda$inflate$3(RenderTask.java:739)\n" +
        "\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)\n" +
        "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)\n" +
        "\tat java.lang.Thread.run(Thread.java:745)\n");
      logger.addBrokenClass("com.example.myapplication.MyButton", throwable);
    };

    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
    assertSize(1, issues);
    assertHtmlEquals("Select <I>Theme.AppCompat</I> or a descendant in the theme selector.", issues.get(0));
  }

  /**
   * Regression test for b/149357583
   *
   * The {@link RenderErrorContributor} should not throw an {@link com.intellij.openapi.project.IndexNotReadyException} when executed in dumb mode.
   */
  public void testDumbModeRenderErrorContributor() {
    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout3.xml", "res/layout/layout3.xml"), null, true);
    assertSize(1, issues);
    assertHtmlEquals(
      "The following classes could not be found:<DL>" +
      "<DD>-&NBSP;Bitton (<A HREF=\"action:classpath\">Fix Build Path</A>" +
      ", <A HREF=\"showTag:Bitton\">Edit XML</A>)" +
      "</DL>Tip: Try to <A HREF=\"action:build\">build</A> the project.<BR/>" +
      "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/>",
      issues.get(0));
  }

  /**
   * Duplicate errors are ignored.
   */
  public void testNoDuplicateIssues() {
      LogOperation operation = (logger, render) -> {
        // MANUALLY register errors
        logger.addMessage(RenderProblem.createPlain(HighlightSeverity.ERROR, "Error 1"));
        logger.addMessage(RenderProblem.createPlain(HighlightSeverity.WARNING, "Warning 1"));
        logger.addMessage(RenderProblem.createPlain(HighlightSeverity.WARNING, "Warning 1"));
        logger.addMessage(RenderProblem.createPlain(HighlightSeverity.ERROR, "Error 1"));
      };

      List<RenderErrorModel.Issue> issues =
        getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
      assertSize(2, issues);
  }

  /**
   * Tests that the RenderErrorContributor builds issues using the correct severity
   */
  public void testIssueSeverity() {
    LogOperation operation = (logger, render) -> {
      // MANUALLY register errors
      logger.addMessage(RenderProblem.createPlain(HighlightSeverity.ERROR, "Error"));
      logger.addMessage(RenderProblem.createPlain(HighlightSeverity.WARNING, "Warning"));
    };

    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
    assertSize(2, issues);
    assertEquals(HighlightSeverity.ERROR, issues.get(0).getSeverity());
    assertEquals(HighlightSeverity.WARNING, issues.get(1).getSeverity());
  }

  private String stripSdkHome(@NotNull String html) {
    AndroidPlatform platform = AndroidPlatform.getInstance(myModule);
    assertNotNull(platform);
    String location = platform.getSdkData().getLocation().toString();
    location = FileUtil.toSystemIndependentName(location);
    html = html.replace(location, "$SDK_HOME")
      .replace("file:///", "file://"); // On Windows JavaDoc source may start with /
    return html;
  }

  private String stripSdkVersion(@NotNull String html) {
    return html.replaceAll("android-\\d\\d", "android-XX");
  }

  // We should just use assertEquals(String,String) here, wch works well when running unit tests
  // in the IDE; you can double click on the failed assertion and see a full diff of the two
  // strings. However, the junit result shown from Jenkins shows a spectacularly bad diff of
  // the two strings, so we print our own diff first two to help diagnose server side failures.
  private void assertHtmlEquals(String expected, RenderErrorModel.Issue issue) {
    String actual = issue.getHtmlContent();
    actual = stripSdkHome(actual);
    actual = stripImages(actual);
    actual = stripSdkVersion(actual);

    if (!expected.equals(actual)) {
      System.out.println("Render unit test failed: " + getName());
      System.out.println("Output diff:\n" + TestUtils.getDiff(expected, actual));
    }

    // Visually diffing very long lines is hard. Let's insert some newlines in the deltas that we're
    // comparing (also helps with junit diff output)
    int newlineModulo = 60;
    expected = injectNewlines(expected, newlineModulo);
    actual = injectNewlines(actual, newlineModulo);

    assertEquals(expected, actual);
  }

  private interface LogOperation {
    void addErrors(@NotNull RenderLogger logger, @NotNull RenderResult render);
  }
}
