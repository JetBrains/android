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

import static com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow;
import static com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.MovedOrResized;
import static com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.SetToolWindowType;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.android.tools.adtui.workbench.DetachedToolWindowManager.DetachedToolWindowFactory;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import java.awt.KeyboardFocusManager;
import java.util.Collections;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

public class DetachedToolWindowManagerTest extends WorkBenchTestCase {
  // Hack to avoid: "java.lang.Error: Cannot load com.apple.laf.AquaLookAndFeel"
  @SuppressWarnings("unused")
  private static volatile boolean DARK = !JBColor.isBright();
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
  private WorkBench<String> myWorkBench1;
  @Mock
  private WorkBench<String> myWorkBench2;
  @Mock
  private AttachedToolWindow<String> myAttachedToolWindow1a;
  @Mock
  private AttachedToolWindow<String> myAttachedToolWindow1b;
  @Mock
  private AttachedToolWindow<String> myAttachedToolWindow2a;
  @Mock
  private AttachedToolWindow<String> myAttachedToolWindow2b;
  @Mock
  private VirtualFile myVirtualFile;
  @Mock
  private DetachedToolWindow<String> myDetachedToolWindow1a;
  @Mock
  private DetachedToolWindow<String> myDetachedToolWindow1b;
  @Mock
  private DetachedToolWindow<String> myDetachedToolWindow2a;
  @Mock
  private DetachedToolWindow<String> myDetachedToolWindow2b;
  @Mock
  private DetachedToolWindowFactory myDetachedToolWindowFactory;
  @Mock
  private KeyboardFocusManager myKeyboardFocusManager;

  private FileEditorManagerListener myListener;
  private DetachedToolWindowManager myManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    openMocks(this).close();
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
      .thenReturn(myDetachedToolWindow1a, (DetachedToolWindow<String>) null);
    when(myDetachedToolWindowFactory.create(any(Project.class), eq(WORKBENCH_TITLE1), eq(PalettePanelToolContent.getOtherDefinition())))
      .thenReturn(myDetachedToolWindow1b, (DetachedToolWindow<String>) null);
    when(myDetachedToolWindowFactory.create(any(Project.class), eq(WORKBENCH_TITLE2), eq(PalettePanelToolContent.getDefinition())))
      .thenReturn(myDetachedToolWindow2a, (DetachedToolWindow<String>) null);
    when(myDetachedToolWindowFactory.create(any(Project.class), eq(WORKBENCH_TITLE2), eq(PalettePanelToolContent.getOtherDefinition())))
      .thenReturn(myDetachedToolWindow2b, (DetachedToolWindow<String>) null);

    myManager = new DetachedToolWindowManager(getProject());
    myManager.setDetachedToolWindowFactory(myDetachedToolWindowFactory);
    myListener = myManager.getFileEditorManagerListener();

    when(myEditorManager.getSelectedEditors()).thenReturn(FileEditor.EMPTY_ARRAY);
    when(myEditorManager.getOpenFiles()).thenReturn(VirtualFile.EMPTY_ARRAY);
    //noinspection UnstableApiUsage
    when(myEditorManager.getOpenFilesWithRemotes()).thenReturn(Collections.emptyList());
    when(myEditorManager.getAllEditors()).thenReturn(FileEditor.EMPTY_ARRAY);
    registerProjectService(FileEditorManager.class, myEditorManager);
    when(myFileEditor1.getComponent()).thenReturn(new JPanel());
    when(myFileEditor2.getComponent()).thenReturn(new JPanel());
    myManager.register(myFileEditor1, myWorkBench1);
    myManager.register(myFileEditor2, myWorkBench2);
    Disposer.register(getTestRootDisposable(), myManager);
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

    verify(myDetachedToolWindow1a).show(eq(myAttachedToolWindow1a));
  }

  public void testFileOpenedCausingFloatingToolWindowToDisplay2() {
    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor1, myFileEditor2});
    myListener.fileOpened(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();

    verify(myDetachedToolWindow1a).show(eq(myAttachedToolWindow1a));
  }

  public void testSwitchingBetweenTwoEditorsWithDifferentFloatingToolWindows() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1, myWorkBench2);
    myListener.fileOpened(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow1a).show(eq(myAttachedToolWindow1a));
    myListener.fileOpened(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();
    verify(myDetachedToolWindow1a).hide();
    verify(myDetachedToolWindow2a).show(eq(myAttachedToolWindow2a));

    FileEditorManagerEvent event1 = createEvent(myFileEditor1);
    FileEditorManagerEvent event2 = createEvent(myFileEditor2);

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

  private @NotNull FileEditorManagerEvent createEvent(FileEditor fileEditor1) {
    return new FileEditorManagerEvent(
      myEditorManager,
      null,
      null,
      null,
      null,
      fileEditor1,
      null);
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

  public void testMinimizeRestoreAndDockFloatingToolWindow() {
    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor1});
    myListener.fileOpened(myEditorManager, myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();

    ToolWindowManager tm = ToolWindowManager.getInstance(getProject());
    ToolWindow tw1 = mock(ToolWindow.class);
    String id1a = DetachedToolWindow.idOf(WORKBENCH_TITLE1, PalettePanelToolContent.getDefinition());
    when(tw1.getId()).thenReturn(id1a);
    getProject().getMessageBus().syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(tm, tw1, HideToolWindow);
    verify(myDetachedToolWindow1a).setMinimized(eq(true));

    getProject().getMessageBus().syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(tm, tw1, MovedOrResized);
    verify(myDetachedToolWindow1a).setMinimized(eq(false));

    getProject().getMessageBus().syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(tm, tw1, SetToolWindowType);
    verify(myDetachedToolWindow1a).updateSettingsInAttachedToolWindow();
  }
}
