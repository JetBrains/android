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
package com.android.tools.adtui.workbench;

import com.android.tools.adtui.workbench.DetachedToolWindowManager.DetachedToolWindowFactory;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.mockito.Mock;

import javax.swing.*;
import java.awt.*;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class DetachedToolWindowManagerTest extends WorkBenchTestCase {
  // Hack to avoid: "java.lang.Error: Cannot load com.apple.laf.AquaLookAndFeel"
  @SuppressWarnings("unused")
  private static volatile boolean DARK = StartupUiUtil.isUnderDarcula();
  private static final String WORKBENCH_NAME1 = "NELE_EDITOR";
  private static final String WORKBENCH_TITLE1 = "Designer";
  private static final String WORKBENCH_NAME2 = "Layout Inspector";
  private static final String WORKBENCH_TITLE2 = WORKBENCH_NAME2;

  @Mock
  private FileEditorManager myEditorManager;
  @Mock
  private FileEditor myFileEditor1;
  @Mock
  private FileEditor myFileEditor2;
  @Mock
  private WorkBench myWorkBench1;
  @Mock
  private WorkBench myWorkBench2;
  @Mock
  private AttachedToolWindow myAttachedToolWindow1a;
  @Mock
  private AttachedToolWindow myAttachedToolWindow1b;
  @Mock
  private AttachedToolWindow myAttachedToolWindow2a;
  @Mock
  private AttachedToolWindow myAttachedToolWindow2b;
  @Mock
  private VirtualFile myVirtualFile;
  @Mock
  private DetachedToolWindow myDetachedToolWindow1a;
  @Mock
  private DetachedToolWindow myDetachedToolWindow1b;
  @Mock
  private DetachedToolWindow myDetachedToolWindow2a;
  @Mock
  private DetachedToolWindow myDetachedToolWindow2b;
  @Mock
  private DetachedToolWindowFactory myDetachedToolWindowFactory;
  @Mock
  private KeyboardFocusManager myKeyboardFocusManager;

  private FileEditorManagerListener myListener;
  private DetachedToolWindowManager myManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    KeyboardFocusManager.setCurrentKeyboardFocusManager(myKeyboardFocusManager);
    when(myWorkBench1.getName()).thenReturn(WORKBENCH_NAME1);
    when(myWorkBench2.getName()).thenReturn(WORKBENCH_NAME2);
    when(myWorkBench1.getDetachedToolWindows()).thenReturn(ImmutableList.of(myAttachedToolWindow1a, myAttachedToolWindow1b));
    when(myWorkBench2.getDetachedToolWindows()).thenReturn(ImmutableList.of(myAttachedToolWindow2a, myAttachedToolWindow2b));
    when(myAttachedToolWindow1a.getDefinition()).thenReturn(PalettePanelToolContent.getDefinition());
    when(myAttachedToolWindow1b.getDefinition()).thenReturn(PalettePanelToolContent.getOtherDefinition());
    when(myAttachedToolWindow2a.getDefinition()).thenReturn(PalettePanelToolContent.getDefinition());
    when(myAttachedToolWindow2b.getDefinition()).thenReturn(PalettePanelToolContent.getOtherDefinition());
    when(myDetachedToolWindowFactory.create(any(Project.class), eq(WORKBENCH_TITLE1), eq(PalettePanelToolContent.getDefinition())))
      .thenReturn(myDetachedToolWindow1a, null);
    when(myDetachedToolWindowFactory.create(any(Project.class), eq(WORKBENCH_TITLE1), eq(PalettePanelToolContent.getOtherDefinition())))
      .thenReturn(myDetachedToolWindow1b, null);
    when(myDetachedToolWindowFactory.create(any(Project.class), eq(WORKBENCH_TITLE2), eq(PalettePanelToolContent.getDefinition())))
      .thenReturn(myDetachedToolWindow2a, null);
    when(myDetachedToolWindowFactory.create(any(Project.class), eq(WORKBENCH_TITLE2), eq(PalettePanelToolContent.getOtherDefinition())))
      .thenReturn(myDetachedToolWindow2b, null);

    myManager = new DetachedToolWindowManager(getProject());
    myManager.setDetachedToolWindowFactory(myDetachedToolWindowFactory);
    myListener = myManager.getFileEditorManagerListener();

    when(myEditorManager.getSelectedEditors()).thenReturn(FileEditor.EMPTY_ARRAY);
    when(myEditorManager.getOpenFiles()).thenReturn(VirtualFile.EMPTY_ARRAY);
    //noinspection UnstableApiUsage
    when(myEditorManager.getOpenFilesWithRemotes()).thenReturn(VirtualFile.EMPTY_ARRAY);
    when(myEditorManager.getAllEditors()).thenReturn(FileEditor.EMPTY_ARRAY);
    ServiceContainerUtil.replaceService(getProject(), FileEditorManager.class, myEditorManager, getTestRootDisposable());
    when(myFileEditor1.getComponent()).thenReturn(new JPanel());
    when(myFileEditor2.getComponent()).thenReturn(new JPanel());
    myManager.register(myFileEditor1, myWorkBench1);
    myManager.register(myFileEditor2, myWorkBench2);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      KeyboardFocusManager.setCurrentKeyboardFocusManager(null);
      //myManager.projectClosed();
      //myManager.disposeComponent();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testProjectClosed() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1);
    myManager.restoreDefaultLayout();
    UIUtil.dispatchAllInvocationEvents();
    Disposer.dispose(myManager);
    verify(myDetachedToolWindow1a).updateSettingsInAttachedToolWindow();
  }

  @SuppressWarnings("unchecked")
  public void testRestoreDefaultLayout() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1);
    myManager.restoreDefaultLayout();
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow1a).show(eq(myAttachedToolWindow1a));
  }

  public void testFileOpened() {
    myListener.fileOpened(myEditorManager, myVirtualFile);
  }

  public void testFileOpenedCausingFloatingToolWindowToDisplay() {
    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor1});
    myListener.fileOpened(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();

    //noinspection unchecked
    verify(myDetachedToolWindow1a).show(eq(myAttachedToolWindow1a));
  }

  public void testFileOpenedCausingFloatingToolWindowToDisplay2() {
    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor1, myFileEditor2});
    myListener.fileOpened(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();

    //noinspection unchecked
    verify(myDetachedToolWindow1a).show(eq(myAttachedToolWindow1a));
  }

  @SuppressWarnings("unchecked")
  public void testSwitchingBetweenTwoEditorsWithDifferentFloatingToolWindows() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1, myWorkBench2);
    myListener.fileOpened(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow1a).show(eq(myAttachedToolWindow1a));
    myListener.fileOpened(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow1a).hide();
    verify(myDetachedToolWindow2a).show(eq(myAttachedToolWindow2a));

    FileEditorManagerEvent event1 = new FileEditorManagerEvent(myEditorManager, null, null, null, myFileEditor1);
    FileEditorManagerEvent event2 = new FileEditorManagerEvent(myEditorManager, null, null, null, myFileEditor2);

    myListener.selectionChanged(event1);
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow2a).hide();
    verify(myDetachedToolWindow1a, times(2)).show(eq(myAttachedToolWindow1a));
    myListener.selectionChanged(event2);
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow1a, times(2)).hide();
    verify(myDetachedToolWindow2a, times(2)).show(eq(myAttachedToolWindow2a));

    // Now unregister one of them:
    myManager.unregister(myFileEditor1);
    myListener.selectionChanged(event1);
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow1a, times(3)).hide();
    verify(myDetachedToolWindow2a, times(2)).hide();
  }

  @SuppressWarnings("unchecked")
  public void testFileCloseCausingFloatingToolWindowToHide() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1, new JLabel());
    myListener.fileOpened(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow1a).show(eq(myAttachedToolWindow1a));

    myListener.fileClosed(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow1a).hide();
  }
}
