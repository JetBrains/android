/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers.stacktrace;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.swing.FakeKeyboard;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.profilers.common.*;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.picocontainer.PicoContainer;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class IntelliJStackTraceViewTest {
  private static final String STACK_STRING =
    "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)\n" +
    "com.example.android.displayingbitmaps.util.ImageFetcher.processBitmap(ImageFetcher.java:214)\n" +
    "com.example.android.displayingbitmaps.util.ImageFetcher.processBitmap(ImageFetcher.java:257)\n" +
    "com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask.doInBackground(ImageWorker.java:312)\n" +
    "com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask.doInBackground(ImageWorker.java:257)\n" +
    "com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)\n" +
    "java.util.concurrent.FutureTask.run(FutureTask.java:237)\n" +
    "java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1133)\n" +
    "java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:607)\n" +
    "java.lang.Thread.run(Thread.java:761)";
  private static final List<CodeLocation> CODE_LOCATIONS = Arrays.stream(STACK_STRING.split("\\n")).map(
    line -> {
      StackFrameParser parser = new StackFrameParser(line);
      return new CodeLocation(parser.getClassName(), parser.getFileName(), parser.getMethodName(), parser.getLineNumber() - 1);
    })
    .collect(Collectors.toList());

  private final Project myProject = ProjectStub.getInstance();

  private IntelliJStackTraceView myStackView;

  @Before
  public void before() {
    StackTraceModel model = new StackTraceModel(new CodeNavigator() {
      @Override
      protected void handleNavigate(@NotNull CodeLocation location) {
      }
    });
    myStackView = new IntelliJStackTraceView(myProject, model, (project, location) -> new FakeCodeElement(location));
    myStackView.getComponent().setSize(100, 400); // Arbitrary size just so we can click on it
  }

  @Test
  public void equalityTest() {
    final String duplicateTestString = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)";
    StackFrameParser parser = new StackFrameParser(duplicateTestString);
    CodeLocation positiveTest =
      new CodeLocation(parser.getClassName(), parser.getFileName(), parser.getMethodName(), parser.getLineNumber() - 1);
    CodeLocation negativeTest1 =
      new CodeLocation(null, null, null, -1);
    CodeLocation negativeTest2 =
      new CodeLocation(positiveTest.getClassName(), positiveTest.getMethodName(), positiveTest.getFileName(),
                       positiveTest.getLineNumber() - 1);
    CodeLocation negativeTest3 =
      new CodeLocation(positiveTest.getClassName(), positiveTest.getFileName(), positiveTest.getMethodName(), -1);
    assertEquals(positiveTest, CODE_LOCATIONS.get(0));
    assertNotEquals(positiveTest, negativeTest1);
    assertNotEquals(positiveTest, negativeTest2);
    assertNotEquals(positiveTest, negativeTest3);
    assertNotEquals(negativeTest1, negativeTest2);
    assertNotEquals(negativeTest1, negativeTest3);
  }

  @Test
  public void setStackFramesTest() {
    myStackView.getModel().setStackFrames(STACK_STRING);
    List<CodeLocation> viewLocations = myStackView.getModel().getCodeLocations();
    assertEquals(CODE_LOCATIONS, viewLocations);

    myStackView.getModel().setStackFrames(ThreadId.INVALID_THREAD_ID, CODE_LOCATIONS);
    viewLocations = myStackView.getModel().getCodeLocations();
    assertEquals(CODE_LOCATIONS, viewLocations);

    myStackView.getModel().setStackFrames(new ThreadId(5), CODE_LOCATIONS);
    viewLocations = myStackView.getModel().getCodeLocations();
    assertEquals(CODE_LOCATIONS, viewLocations);
    ListModel model = myStackView.getListView().getModel();
    assertEquals(CODE_LOCATIONS.size() + 1, model.getSize());
    Object threadElement = model.getElementAt(model.getSize() - 1);
    assertTrue(threadElement instanceof ThreadElement);
    assertEquals(new ThreadId(5), ((ThreadElement)threadElement).getThreadId());
  }

  @Test
  public void setSelectedRowByModel() throws InvocationTargetException, InterruptedException {
    final int[] invocationCount = {0};
    myStackView.getModel().setStackFrames(STACK_STRING);
    myStackView.getListView().addListSelectionListener(e -> invocationCount[0]++);

    JList list = myStackView.getListView();
    assertEquals(0, invocationCount[0]);
    assertEquals(-1, list.getSelectedIndex());

    myStackView.getModel().setSelectedIndex(3);
    assertTrue(list.getSelectedValue() instanceof FakeCodeElement);
    assertEquals(1, invocationCount[0]);
    assertEquals(3, list.getSelectedIndex());

    myStackView.getModel().setSelectedIndex(-2);
    assertEquals(2, invocationCount[0]);
    assertNull(list.getSelectedValue());
  }

  @Test
  public void setSelectedRowByUiInput() throws InvocationTargetException, InterruptedException {
    FakeUi fakeUi = new FakeUi(myStackView.getComponent());
    AspectObserver observer = new AspectObserver();
    final int[] invocationCount = {0};
    myStackView.getModel().addDependency(observer).onChange(StackTraceModel.Aspect.SELECTED_LOCATION, () -> invocationCount[0]++);

    JList list = myStackView.getListView();
    assertEquals(ListSelectionModel.SINGLE_SELECTION, list.getSelectionModel().getSelectionMode());
    assertEquals(0, invocationCount[0]);
    assertEquals(-1, list.getSelectedIndex());

    myStackView.getModel().setStackFrames(STACK_STRING);
    assertEquals(CODE_LOCATIONS.size(), myStackView.getModel().getCodeLocations().size());

    // fakeUi.mouse.click(5, 5); <- Can't click as the ListView is a heavyweight component and
    // can't be interacted with in headless mode. Instead, just set programmatically:
    list.setSelectedIndex(0);
    assertTrue(list.getSelectedValue() instanceof FakeCodeElement);
    assertEquals(1, invocationCount[0]);

    fakeUi.keyboard.setFocus(myStackView.getListView());
    fakeUi.keyboard.press(FakeKeyboard.Key.ESC);
    assertNull(list.getSelectedValue());
  }

  /**
   * Copy of {@link DummyProject}.
   */
  private static class ProjectStub extends UserDataHolderBase implements Project {
    private static class ProjectStubHolder {
      private static final ProjectStub ourInstance = new ProjectStub();
    }

    @NotNull
    public static Project getInstance() {
      return ProjectStubHolder.ourInstance;
    }

    private ProjectStub() {
    }

    @Override
    public VirtualFile getProjectFile() {
      return null;
    }

    @Override
    @NotNull
    public String getName() {
      return "";
    }

    @Override
    @Nullable
    @NonNls
    public String getPresentableUrl() {
      return null;
    }

    @Override
    @NotNull
    @NonNls
    public String getLocationHash() {
      return "dummy";
    }

    @Override
    @Nullable
    public String getProjectFilePath() {
      return null;
    }

    @Override
    public VirtualFile getWorkspaceFile() {
      return null;
    }

    @Override
    @Nullable
    public VirtualFile getBaseDir() {
      return null;
    }

    @Nullable
    @Override
    public String getBasePath() {
      return null;
    }

    @Override
    public void save() {
    }

    @Override
    public BaseComponent getComponent(@NotNull String name) {
      return null;
    }

    @Nullable
    @Override
    public <T> T getComponent(@NotNull Class<T> interfaceClass) {
      return null;
    }

    @Override
    public boolean hasComponent(@NotNull Class interfaceClass) {
      return false;
    }

    @Override
    @NotNull
    public <T> T[] getComponents(@NotNull Class<T> baseClass) {
      return (T[])ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    @NotNull
    public PicoContainer getPicoContainer() {
      throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
    }

    @Override
    public <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementation) {
      return null;
    }

    @Override
    public boolean isDisposed() {
      return false;
    }

    @Override
    @NotNull
    public Condition getDisposed() {
      return o -> isDisposed();
    }

    @Override
    public boolean isOpen() {
      return false;
    }

    @Override
    public boolean isInitialized() {
      return false;
    }

    @Override
    public boolean isDefault() {
      return false;
    }

    @NotNull
    @Override
    public MessageBus getMessageBus() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dispose() {
    }

    @NotNull
    @Override
    public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
      throw new UnsupportedOperationException("getExtensions()");
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
