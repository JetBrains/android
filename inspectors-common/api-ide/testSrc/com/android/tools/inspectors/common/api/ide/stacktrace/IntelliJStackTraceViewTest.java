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
package com.android.tools.inspectors.common.api.ide.stacktrace;

import static com.android.tools.inspectors.common.api.stacktrace.ThreadId.INVALID_THREAD_ID;
import static com.google.common.truth.Truth.assertThat;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;

import com.android.testutils.AsyncTestUtils;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.swing.FakeMouse;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.adtui.swing.laf.HeadlessListUI;
import com.android.tools.idea.codenavigation.CodeLocation;
import com.android.tools.idea.codenavigation.CodeNavigator;
import com.android.tools.idea.codenavigation.FakeNavSource;
import com.android.tools.inspectors.common.api.stacktrace.CodeElement;
import com.android.tools.inspectors.common.api.stacktrace.StackElement;
import com.android.tools.inspectors.common.api.stacktrace.StackFrameParser;
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel;
import com.android.tools.inspectors.common.api.stacktrace.ThreadElement;
import com.android.tools.inspectors.common.api.stacktrace.ThreadId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.ProjectRule;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class IntelliJStackTraceViewTest {
  private static final String STACK_STRING =
    """
      com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)
      com.example.android.displayingbitmaps.util.ImageFetcher.processBitmap(ImageFetcher.java:214)
      com.example.android.displayingbitmaps.util.ImageFetcher.processBitmap(ImageFetcher.java:257)
      com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask.doInBackground(ImageWorker.java:312)
      com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask.doInBackground(ImageWorker.java:257)
      com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)
      java.util.concurrent.FutureTask.run(FutureTask.java:237)
      java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1133)
      java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:607)
      java.lang.Thread.run(Thread.java:761)""";
  private static final List<CodeLocation> CODE_LOCATIONS = StackFrameParser.parseStack(STACK_STRING);

  @Rule
  public final ProjectRule myProjectRule = new ProjectRule();

  @Rule
  public final DisposableRule myDisposableRule = new DisposableRule();

  private IntelliJStackTraceView myStackView;

  public static StackTraceModel createStackTraceModel() {
    return new StackTraceModel(
        new CodeNavigator(new FakeNavSource(), CodeNavigator.Companion.getTestExecutor()));
  }

  public static IntelliJStackTraceView createStackTraceView(Project project, StackTraceModel model, Disposable disposable) {
    return new IntelliJStackTraceView(project, model, disposable, (p, location) -> new FakeCodeElement(location));
  }

  @Before
  public void before() {
    // Arbitrary size just so we can click on it
    StackTraceModel model = createStackTraceModel();
    IntelliJStackTraceView view = createStackTraceView(myProjectRule.getProject(), model, myDisposableRule.getDisposable());
    view.getComponent().setLocation(0, 0);
    view.getComponent().setSize(100, 400);
    view.getListView().setUI(new HeadlessListUI());

    myStackView = view;
  }

  @Test
  public void equalityTest() {
    final String duplicateTestString = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)";
    CodeLocation positiveTest = StackFrameParser.parseFrame(duplicateTestString);
    assertThat(positiveTest).isNotNull();
    CodeLocation negativeTest1 = CodeLocation.stub();
    CodeLocation negativeTest2 = new CodeLocation.Builder(positiveTest).setMethodName(positiveTest.getFileName()).build();
    CodeLocation negativeTest3 = new CodeLocation.Builder(positiveTest).setLineNumber(-1).build();
    assertThat(CODE_LOCATIONS.get(0)).isEqualTo(positiveTest);
    assertThat(negativeTest1).isNotEqualTo(positiveTest);
    assertThat(negativeTest2).isNotEqualTo(positiveTest);
    assertThat(negativeTest3).isNotEqualTo(positiveTest);
    assertThat(negativeTest2).isNotEqualTo(negativeTest1);
    assertThat(negativeTest3).isNotEqualTo(negativeTest1);
  }

  @Test
  public void nativeEqualityTest() {
    CodeLocation loc = new CodeLocation.Builder("type").setMethodName("method").setNativeModuleName("module1")
      .setMethodParameters(Arrays.asList("int", "std::string &"))
      .setFileName("file").setLineNumber(1).setNativeCode(true).build();
    CodeLocation positive = new CodeLocation.Builder(loc).build();
    assertThat(loc).isEqualTo(positive);

    CodeLocation negativeTest1 = new CodeLocation.Builder(positive).setNativeCode(false).build();
    assertThat(loc).isNotEqualTo(negativeTest1);

    CodeLocation negativeTest2 = new CodeLocation.Builder(positive).setNativeModuleName("module2").build();
    assertThat(loc).isNotEqualTo(negativeTest2);
  }

  @Test
  public void setStackFramesTest() {
    myStackView.getModel().setStackFrames(STACK_STRING);
    waitForModel();

    List<CodeLocation> viewLocations = myStackView.getModel().getCodeLocations();
    assertThat(viewLocations).isEqualTo(CODE_LOCATIONS);

    myStackView.getModel().setStackFrames(INVALID_THREAD_ID, CODE_LOCATIONS);
    waitForModel();

    viewLocations = myStackView.getModel().getCodeLocations();
    assertThat(viewLocations).isEqualTo(CODE_LOCATIONS);

    myStackView.getModel().setStackFrames(new ThreadId(5), CODE_LOCATIONS);
    waitForModel();

    viewLocations = myStackView.getModel().getCodeLocations();
    assertThat(viewLocations).isEqualTo(CODE_LOCATIONS);
    ListModel<StackElement> model = myStackView.getListView().getModel();

    Object threadElement = model.getElementAt(model.getSize() - 1);
    assertThat(threadElement).isInstanceOf(ThreadElement.class);
    assertThat(((ThreadElement)threadElement).getThreadId()).isEqualTo(new ThreadId(5));
  }

  @Test
  public void setSelectedRowByModel() {
    final int[] invocationCount = {0};
    myStackView.getModel().setStackFrames(STACK_STRING);
    waitForModel();

    myStackView.getListView().addListSelectionListener(e -> invocationCount[0]++);

    JList<StackElement> list = myStackView.getListView();
    assertThat(invocationCount[0]).isEqualTo(0);
    assertThat(list.getSelectedIndex()).isEqualTo(-1);

    myStackView.getModel().setSelectedIndex(3);
    assertThat(list.getSelectedValue()).isInstanceOf(FakeCodeElement.class);
    assertThat(invocationCount[0]).isEqualTo(1);
    assertThat(list.getSelectedIndex()).isEqualTo(3);

    myStackView.getModel().setSelectedIndex(-2);
    assertThat(invocationCount[0]).isEqualTo(2);
    assertThat(list.getSelectedValue()).isNull();
  }

  @Test
  public void doubleClickingStackViewNavigatesToSelectedElement() {
    FakeUi fakeUi = new FakeUi(myStackView.getComponent());
    AspectObserver observer = new AspectObserver();
    final int[] invocationCount = {0};
    myStackView.getModel().addDependency(observer).onChange(StackTraceModel.Aspect.SELECTED_LOCATION, () -> invocationCount[0]++);

    JList<StackElement> list = myStackView.getListView();
    assertThat(list.getSelectionModel().getSelectionMode()).isEqualTo(ListSelectionModel.SINGLE_SELECTION);
    assertThat(invocationCount[0]).isEqualTo(0);
    assertThat(list.getSelectedIndex()).isEqualTo(-1);

    myStackView.getModel().setStackFrames(STACK_STRING);
    waitForModel();

    assertThat(myStackView.getModel().getCodeLocations().size()).isEqualTo(CODE_LOCATIONS.size());

    fakeUi.mouse.doubleClick(5, 5); // First row
    assertThat(list.getSelectedValue()).isInstanceOf(FakeCodeElement.class);
    assertThat(invocationCount[0]).isEqualTo(1);
  }

  @Test
  public void rightClickingStackTraceView() {
    FakeUi fakeUi = new FakeUi(myStackView.getComponent());
    AspectObserver observer = new AspectObserver();
    final int[] invocationCount = {0};
    myStackView.getModel().addDependency(observer).onChange(StackTraceModel.Aspect.SELECTED_LOCATION, () -> invocationCount[0]++);

    JList<StackElement> list = myStackView.getListView();
    assertThat(list.getSelectionModel().getSelectionMode()).isEqualTo(ListSelectionModel.SINGLE_SELECTION);
    assertThat(invocationCount[0]).isEqualTo(0);
    assertThat(list.getSelectedIndex()).isEqualTo(-1);

    myStackView.getModel().setStackFrames(STACK_STRING);
    waitForModel();

    assertThat(myStackView.getModel().getCodeLocations().size()).isEqualTo(CODE_LOCATIONS.size());

    fakeUi.mouse.click(5, 5, FakeMouse.Button.RIGHT); // First row
    assertThat(list.getSelectedValue()).isInstanceOf(FakeCodeElement.class);
  }

  @Test
  public void pressingEnterOnStackViewNavigatesToSelectedElement() throws Exception {
    FakeUi fakeUi = new FakeUi(myStackView.getComponent());
    AspectObserver observer = new AspectObserver();
    final int[] invocationCount = {0};
    myStackView.getModel().addDependency(observer).onChange(StackTraceModel.Aspect.SELECTED_LOCATION, () -> invocationCount[0]++);

    JList<StackElement> list = myStackView.getListView();
    assertThat(list.getSelectionModel().getSelectionMode()).isEqualTo(ListSelectionModel.SINGLE_SELECTION);
    assertThat(invocationCount[0]).isEqualTo(0);
    assertThat(list.getSelectedIndex()).isEqualTo(-1);

    myStackView.getModel().setStackFrames(STACK_STRING);
    waitForModel();

    assertThat(myStackView.getModel().getCodeLocations().size()).isEqualTo(CODE_LOCATIONS.size());

    fakeUi.mouse.click(5, 5); // First row
    assertThat(list.getSelectedValue()).isInstanceOf(FakeCodeElement.class);
    assertThat(invocationCount[0]).isEqualTo(0);

    fakeUi.keyboard.setFocus(list);
    fakeUi.keyboard.press(VK_ENTER);
    assertThat(invocationCount[0]).isEqualTo(1);
  }

  private void waitForModel() {
    ListModel<StackElement> listModel = myStackView.getListView().getModel();
    List<CodeLocation> locations = myStackView.getModel().getCodeLocations();
    int expectedElements = locations.size() + (myStackView.getModel().getThreadId() == INVALID_THREAD_ID ? 0 : 1);

    try {
      AsyncTestUtils.waitForCondition(5, SECONDS, () -> listModel.getSize() == expectedElements);
    }
    catch (TimeoutException e) {
      fail(String.format("Expected %d frames but got %d", expectedElements, listModel.getSize()));
    }
  }

  private static class FakeCodeElement implements CodeElement {
    @NotNull private final CodeLocation myCodeLocation;

    public FakeCodeElement(@NotNull CodeLocation codeLocation) {
      myCodeLocation = codeLocation;
    }

    @NotNull
    @Override
    public CodeLocation getCodeLocation() {
      return myCodeLocation;
    }

    @NotNull
    @Override
    public String getPackageName() {
      return "";
    }

    @NotNull
    @Override
    public String getSimpleClassName() {
      return "";
    }

    @NotNull
    @Override
    public String getMethodName() {
      return "";
    }

    @Override
    public boolean isInUserCode() {
      return false;
    }
  }
}
