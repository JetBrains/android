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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.StackFrameParser;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.ui.FileColorManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.picocontainer.PicoContainer;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
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

  private RunnableCheck myRunnableCheck;
  private IntelliJStackTraceView myStackView;

  @Before
  public void before() {
    myRunnableCheck = new RunnableCheck();
    myStackView = new IntelliJStackTraceView(myProject, myRunnableCheck, new FileColorManagerMock(),
                                             (project, location) -> new FakeStackNavigation(location));
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
    myStackView.setStackFrames(STACK_STRING);
    List<CodeLocation> viewLocations = myStackView.getCodeLocations();
    assertEquals(CODE_LOCATIONS, viewLocations);

    myStackView.setStackFrames(CODE_LOCATIONS);
    assertEquals(CODE_LOCATIONS, CODE_LOCATIONS);
  }

  @Test
  public void clickComponentTest() throws InvocationTargetException, InterruptedException {
    JList list = myStackView.getListView();
    assertEquals(ListSelectionModel.SINGLE_SELECTION, list.getSelectionModel().getSelectionMode());
    assertEquals(0, myRunnableCheck.getInvocationCount());
    assertEquals(-1, list.getSelectedIndex());

    myStackView.setStackFrames(STACK_STRING);
    assertEquals(CODE_LOCATIONS.size(), myStackView.getCodeLocations().size());

    list.setSelectedIndex(0);
    assertTrue(list.getSelectedValue() instanceof FakeStackNavigation);
    assertEquals(1, ((FakeStackNavigation)list.getSelectedValue()).getNavigatable().getInvocationCount());
  }

  private static class RunnableCheck implements Runnable {
    private int myInvocationCount = 0;

    @Override
    public void run() {
      myInvocationCount++;
    }

    public int getInvocationCount() {
      return myInvocationCount;
    }

    public void clearInvocationCount() {
      myInvocationCount = 0;
    }
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

    @NotNull
    public ComponentConfig[] getComponentConfigurations() {
      return new ComponentConfig[0];
    }

    @Nullable
    public Object getComponent(final ComponentConfig componentConfig) {
      return null;
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

    public ComponentConfig getConfig(Class componentImplementation) {
      throw new UnsupportedOperationException("Method getConfig not implemented in " + getClass());
    }
  }

  private static class FakeNavigatable implements Navigatable {
    @NotNull private final CodeLocation myCodeLocation;

    private int myInvocationCount = 0;

    public FakeNavigatable(@NotNull CodeLocation codeLocation) {
      myCodeLocation = codeLocation;
    }

    @Override
    public void navigate(boolean requestFocus) {
      myInvocationCount++;
    }

    @Override
    public boolean canNavigate() {
      return true;
    }

    @Override
    public boolean canNavigateToSource() {
      return true;
    }

    @NotNull
    public CodeLocation getCodeLocation() {
      return myCodeLocation;
    }

    public int getInvocationCount() {
      return myInvocationCount;
    }
  }

  private static class FakeStackNavigation implements StackNavigation {
    @NotNull private final CodeLocation myCodeLocation;

    @NotNull private final FakeNavigatable myNavigatable;

    public FakeStackNavigation(@NotNull CodeLocation codeLocation) {
      myCodeLocation = codeLocation;
      myNavigatable = new FakeNavigatable(myCodeLocation);
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

    @Nullable
    @Override
    public Navigatable[] getNavigatable(@Nullable Runnable preNavigate) {
      return new Navigatable[]{myNavigatable};
    }

    @Nullable
    @Override
    public VirtualFile findClassFile() {
      return null;
    }

    @NotNull
    public FakeNavigatable getNavigatable() {
      return myNavigatable;
    }
  }

  private static class FileColorManagerMock extends FileColorManager {
    @Override
    public boolean isEnabled() {
      return false;
    }

    @Override
    public void setEnabled(boolean enabled) {

    }

    @Override
    public boolean isEnabledForTabs() {
      return false;
    }

    @Override
    public boolean isEnabledForProjectView() {
      return false;
    }

    @Override
    public Project getProject() {
      return null;
    }

    @Nullable
    @Override
    public Color getColor(@NotNull String name) {
      return null;
    }

    @Override
    public Collection<String> getColorNames() {
      return null;
    }

    @Nullable
    @Override
    public Color getFileColor(@NotNull PsiFile file) {
      return null;
    }

    @Nullable
    @Override
    public Color getFileColor(@NotNull VirtualFile file) {
      return null;
    }

    @Nullable
    @Override
    public Color getScopeColor(@NotNull String scopeName) {
      return null;
    }

    @Override
    public boolean isShared(@NotNull String scopeName) {
      return false;
    }

    @Override
    public boolean isColored(@NotNull String scopeName, boolean shared) {
      return false;
    }

    @Nullable
    @Override
    public Color getRendererBackground(VirtualFile file) {
      return null;
    }

    @Nullable
    @Override
    public Color getRendererBackground(PsiFile file) {
      return null;
    }

    @Override
    public void addScopeColor(@NotNull String scopeName, @NotNull String colorName, boolean isProjectLevel) {

    }
  }
}
