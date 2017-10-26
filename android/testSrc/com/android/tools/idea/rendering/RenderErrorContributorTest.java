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

import com.android.sdklib.IAndroidTarget;
import com.android.testutils.TestUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.utils.StringHelper;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.android.tools.idea.rendering.RenderErrorContributor.isBuiltByJdk7OrHigher;


public class RenderErrorContributorTest extends AndroidTestCase {
  public static final String BASE_PATH = "render/";

  // Image paths will include full resource urls which depends on the test environment
  private static String stripImages(@NotNull String html) {
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

  /**
   * Attempts to create an exception object that matches the given description, which
   * is in the form of the output of an exception stack dump ({@link Throwable#printStackTrace()})
   *
   * @param desc the description of an exception
   * @return a corresponding exception if possible
   */
  private static Throwable createExceptionFromDesc(String desc) {
    return createExceptionFromDesc(desc, null);
  }

  @SuppressWarnings("ThrowableInstanceNeverThrown")
  private static Throwable createExceptionFromDesc(String desc, @Nullable Throwable throwable) {
    // First line: description and type
    Iterator<String> iterator = Splitter.on('\n').split(desc).iterator();
    assertTrue(iterator.hasNext());
    final String first = iterator.next();
    assertTrue(iterator.hasNext());
    String message = null;
    String exceptionClass;
    int index = first.indexOf(':');
    if (index != -1) {
      exceptionClass = first.substring(0, index).trim();
      message = first.substring(index + 1).trim();
    }
    else {
      exceptionClass = first.trim();
    }

    if (throwable == null) {
      try {
        @SuppressWarnings("unchecked")
        Class<Throwable> clz = (Class<Throwable>)Class.forName(exceptionClass);
        if (message == null) {
          throwable = clz.newInstance();
        }
        else {
          Constructor<Throwable> constructor = clz.getConstructor(String.class);
          throwable = constructor.newInstance(message);
        }
      }
      catch (Throwable t) {
        if (message == null) {
          throwable = new Throwable() {
            @Override
            public String getMessage() {
              return first;
            }

            @Override
            public String toString() {
              return first;
            }
          };
        }
        else {
          throwable = new Throwable(message);
        }
      }
    }

    List<StackTraceElement> frames = Lists.newArrayList();
    Pattern outerPattern = Pattern.compile("\tat (.*)\\.([^.]*)\\((.*)\\)");
    Pattern innerPattern = Pattern.compile("(.*):(\\d*)");
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (line.isEmpty()) {
        break;
      }
      Matcher outerMatcher = outerPattern.matcher(line);
      if (!outerMatcher.matches()) {
        fail("Line " + line + " does not match expected stactrace pattern");
      }
      else {
        String clz = outerMatcher.group(1);
        String method = outerMatcher.group(2);
        String inner = outerMatcher.group(3);
        switch (inner) {
          case "Native Method":
            frames.add(new StackTraceElement(clz, method, null, -2));
            break;
          case "Unknown Source":
            frames.add(new StackTraceElement(clz, method, null, -1));
            break;
          default:
            Matcher innerMatcher = innerPattern.matcher(inner);
            if (!innerMatcher.matches()) {
              fail("Trace parameter list " + inner + " does not match expected pattern");
            }
            else {
              String file = innerMatcher.group(1);
              int lineNum = Integer.parseInt(innerMatcher.group(2));
              frames.add(new StackTraceElement(clz, method, file, lineNum));
            }
            break;
        }
      }
    }

    throwable.setStackTrace(frames.toArray(new StackTraceElement[frames.size()]));

    // Dump stack back to string to make sure we have the same exception
    desc = StringHelper.toSystemLineSeparator(desc);
    assertEquals(desc, AndroidCommonUtils.getStackTrace(throwable));

    return throwable;
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
      String path = target.getPath(IAndroidTarget.ANDROID_JAR);
      if (path == null) {
        continue;
      }
      File f = new File(path);
      if (f.getParentFile() != null && f.getParentFile().getName().equals(platformDir)) {
        return target;
      }
    }

    return null;
  }

  private List<RenderErrorModel.Issue> getRenderOutput(@NotNull VirtualFile file, @Nullable LogOperation logOperation) {
    assertNotNull(file);
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    assertNotNull(facet);
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(configurationManager);
    IAndroidTarget target = getTestTarget(configurationManager);
    assertNotNull(target);
    configurationManager.setTarget(target);
    Configuration configuration = configurationManager.getConfiguration(file);
    assertSame(target, configuration.getRealTarget());

    // TODO: Remove this after http://b.android.com/203392 is released
    // If we are using the embedded layoutlib, use a recent theme to avoid missing styles errors.
    configuration.setTheme("android:Theme.Material");

    RenderService renderService = RenderService.getInstance(facet);
    RenderLogger logger = renderService.createLogger();
    final RenderTask task = renderService.createTask(psiFile, configuration, logger, null);
    assertNotNull(task);
    task.disableSecurityManager();
    RenderResult render = RenderTestUtil.renderOnSeparateThread(task);
    assertNotNull(render);

    if (logOperation != null) {
      logOperation.addErrors(logger, render);
    }

    return RenderErrorModelFactory.createErrorModel(render, null).getIssues().stream().sorted().collect(Collectors.toList());
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
      "</DL>Tip: Try to <A HREF=\"action:build\">build</A> the project.<BR/><BR/>" +
      "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/><BR/>" +
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
      "</DL>Tip: Try to <A HREF=\"action:build\">build</A> the project.<BR/><BR/>" +
      "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/><BR/>" +
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
      "</DL>Tip: Try to <A HREF=\"action:build\">build</A> the project.<BR/><BR/>" +
      "Tip: Try to <A HREF=\"refreshRender\">refresh</A> the layout.<BR/><BR/>" +
      "<BR/>",
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
      logger.error(null, null, throwable, null);
      //noinspection ConstantConditions
      target.set(render.getRenderTask().getConfiguration().getRealTarget());

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
      logger.error(null, "Failed to configure parser for " + path, throwable, null);
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

  public void testWrongClassFormat() {
    LogOperation operation = (logger, render) -> {
      // MANUALLY register errors
      logger.addIncorrectFormatClass("com.example.unit.test.R",
                                     new InconvertibleClassError(null, "com.example.unit.test.R", 51, 0));
      logger.addIncorrectFormatClass("com.example.unit.test.MyButton",
                                     new InconvertibleClassError(null, "com.example.unit.test.MyButton", 52, 0));
    };

    List<RenderErrorModel.Issue> issues =
      getRenderOutput(myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml"), operation);
    assertSize(1, issues);

    String incompatible = "";
    String modules = "";
    if (isBuiltByJdk7OrHigher(myModule)) {
      incompatible = "The following modules are built with incompatible JDK:<BR/>" +
                     myModule.getName() + "<BR/>";
      modules = "<A HREF=\"runnable:1\">Change Java SDK to 1.6</A><BR/>";
    }

    assertHtmlEquals(
      "Preview might be incorrect: unsupported class version.<BR/>" +
      "Tip: You need to run the IDE with the highest JDK version that you are compiling custom views with. " +
      "For example, if you are compiling with sourceCompatibility 1.7, you must run the IDE with JDK 1.7. " +
      "Running on a higher JDK is necessary such that these classes can be run in the layout renderer. (Or, extract your custom views " +
      "into a library which you compile with a lower JDK version.)<BR/>" +
      "<BR/>" +
      "If you have just accidentally built your code with a later JDK, try to <A HREF=\"action:build\">build</A> the project.<BR/>" +
      "<BR/>" +
      "Classes with incompatible format:<DL>" +
      "<DD>-&NBSP;com.example.unit.test.MyButton (Compiled with 1.8)" +
      "<DD>-&NBSP;com.example.unit.test.R (Compiled with 1.7)" +
      "</DL>" +
      incompatible +
      "<A HREF=\"runnable:0\">Rebuild project with '-target 1.6'</A><BR/>" +
      modules, issues.get(0));
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

      logger.error(null, null, throwable, null);

      //noinspection ConstantConditions
      target.set(render.getRenderTask().getConfiguration().getRealTarget());
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
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-27/android/view/View.java:14433\">View.java:14433</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-27/android/view/View.java:14318\">View.java:14318</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-27/android/view/View.java:14316\">View.java:14316</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-27/android/view/View.java:14316\">View.java:14316</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-27/android/view/View.java:14436\">View.java:14436</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-27/android/view/View.java:14318\">View.java:14318</A>)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.drawChild(ViewGroup.java:3103)<BR/>" +
        "&nbsp;&nbsp;at android.view.ViewGroup.dispatchDraw(ViewGroup.java:2940)<BR/>" +
        "&nbsp;&nbsp;at android.view.View.draw(<A HREF=\"file://$SDK_HOME/sources/android-27/android/view/View.java:14436\">View.java:14436</A>)<BR/>" +
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
      logger.error("Error", "An error", null);
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

  private String stripSdkHome(@NotNull String html) {
    AndroidPlatform platform = AndroidPlatform.getInstance(myModule);
    assertNotNull(platform);
    String location = platform.getSdkData().getLocation().getPath();
    location = FileUtil.toSystemIndependentName(location);
    html = html.replace(location, "$SDK_HOME")
      .replace("file:///", "file://"); // On Windows JavaDoc source may start with /
    return html;
  }

  // We should just use assertEquals(String,String) here, wch works well when running unit tests
  // in the IDE; you can double click on the failed assertion and see a full diff of the two
  // strings. However, the junit result shown from Jenkins shows a spectacularly bad diff of
  // the two strings, so we print our own diff first two to help diagnose server side failures.
  private void assertHtmlEquals(String expected, RenderErrorModel.Issue issue) {
    String actual = issue.getHtmlContent();
    actual = stripSdkHome(actual);
    actual = stripImages(actual);

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
