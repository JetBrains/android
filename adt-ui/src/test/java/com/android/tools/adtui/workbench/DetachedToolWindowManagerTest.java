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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import javax.swing.*;
import java.awt.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class DetachedToolWindowManagerTest {
  // Hack to avoid: "java.lang.Error: Cannot load com.apple.laf.AquaLookAndFeel"
  @SuppressWarnings("unused")
  private static volatile boolean DARK = UIUtil.isUnderDarcula();

  @Rule
  public FrameworkRule myFrameworkRule = new FrameworkRule();
  @Mock
  private FileEditor myFileEditor1;
  @Mock
  private FileEditor myFileEditor2;
  @Mock
  private WorkBench myWorkBench1;
  @Mock
  private WorkBench myWorkBench2;
  @Mock
  private AttachedToolWindow myAttachedToolWindow1;
  @Mock
  private AttachedToolWindow myAttachedToolWindow2;
  @Mock
  private MessageBus myMessageBus;
  @Mock
  private MessageBusConnection myConnection;
  @Mock
  private VirtualFile myVirtualFile;
  @Mock
  private DetachedToolWindow myDetachedToolWindow1;
  @Mock
  private DetachedToolWindow myDetachedToolWindow2;
  @Mock
  private DetachedToolWindowFactory myDetachedToolWindowFactory;
  @Mock
  private KeyboardFocusManager myKeyboardFocusManager;

  private FileEditorManager myEditorManager;
  private FileEditorManagerListener myListener;
  private DetachedToolWindowManager myManager;

  @Before
  public void before() {
    initMocks(this);
    KeyboardFocusManager.setCurrentKeyboardFocusManager(myKeyboardFocusManager);
    Project project = ProjectManager.getInstance().getDefaultProject();
    myEditorManager = FileEditorManager.getInstance(project);
    when(myWorkBench1.getDetachedToolWindows()).thenReturn(ImmutableList.of(myAttachedToolWindow1));
    when(myWorkBench2.getDetachedToolWindows()).thenReturn(ImmutableList.of(myAttachedToolWindow2));
    when(myAttachedToolWindow1.getDefinition()).thenReturn(PalettePanelToolContent.getDefinition());
    when(myAttachedToolWindow2.getDefinition()).thenReturn(PalettePanelToolContent.getOtherDefinition());
    when(myDetachedToolWindowFactory.create(any(Project.class), any(ToolWindowDefinition.class)))
      .thenReturn(myDetachedToolWindow1, myDetachedToolWindow2, null);

    myManager = new DetachedToolWindowManager(
      ApplicationManager.getApplication(),
      project,
      FileEditorManager.getInstance(project));
    myManager.initComponent();
    myManager.setDetachedToolWindowFactory(myDetachedToolWindowFactory);
    assert myManager.getComponentName().equals("DetachedToolWindowManager");

    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[0]);
    when(project.getMessageBus()).thenReturn(myMessageBus);
    when(myMessageBus.connect(eq(project))).thenReturn(myConnection).thenReturn(null);
    myManager.projectOpened();
    ArgumentCaptor<FileEditorManagerListener> captor = ArgumentCaptor.forClass(FileEditorManagerListener.class);
    //noinspection unchecked
    verify(myConnection).subscribe(any(Topic.class), captor.capture());
    when(myFileEditor1.getComponent()).thenReturn(new JPanel());
    when(myFileEditor2.getComponent()).thenReturn(new JPanel());
    myListener = captor.getValue();
    myManager.register(myFileEditor1, myWorkBench1);
    myManager.register(myFileEditor2, myWorkBench2);
  }

  @After
  public void after() {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null);
    myManager.projectClosed();
    myManager.disposeComponent();
  }

  @Test
  public void testProjectClosed() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1);
    myManager.restoreDefaultLayout();
    myManager.projectClosed();
    verify(myConnection).disconnect();
    verify(myDetachedToolWindow1).updateSettingsInAttachedToolWindow();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRestoreDefaultLayout() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1);

    myManager.restoreDefaultLayout();
    verify(myDetachedToolWindow1).show(eq(myAttachedToolWindow1));
  }

  @Test
  public void testFileOpened() {
    myListener.fileOpened(myEditorManager, myVirtualFile);
  }

  @Test
  public void testFileOpenedCausingFloatingToolWindowToDisplay() {
    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor1});
    myListener.fileOpened(myEditorManager, myVirtualFile);
    //noinspection unchecked
    verify(myDetachedToolWindow1).show(eq(myAttachedToolWindow1));
  }

  @Test
  public void testFileOpenedCausingFloatingToolWindowToDisplay2() {
    when(myEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor1, myFileEditor2});
    myListener.fileOpened(myEditorManager, myVirtualFile);
    //noinspection unchecked
    verify(myDetachedToolWindow1).show(eq(myAttachedToolWindow1));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSwitchingBetweenTwoEditorsWithDifferentFloatingToolWindows() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1, myWorkBench2);
    myListener.fileOpened(myEditorManager, myVirtualFile);
    verify(myDetachedToolWindow1).show(eq(myAttachedToolWindow1));
    myListener.fileOpened(myEditorManager, myVirtualFile);
    verify(myDetachedToolWindow1).hide();
    verify(myDetachedToolWindow2).show(eq(myAttachedToolWindow2));

    FileEditorManagerEvent event1 = new FileEditorManagerEvent(myEditorManager, null, null, null, myFileEditor1);
    FileEditorManagerEvent event2 = new FileEditorManagerEvent(myEditorManager, null, null, null, myFileEditor2);

    myListener.selectionChanged(event1);
    verify(myDetachedToolWindow2).hide();
    verify(myDetachedToolWindow1, times(2)).show(eq(myAttachedToolWindow1));
    myListener.selectionChanged(event2);
    verify(myDetachedToolWindow1, times(2)).hide();
    verify(myDetachedToolWindow2, times(2)).show(eq(myAttachedToolWindow2));

    // Now unregister one of them:
    myManager.unregister(myFileEditor1);
    myListener.selectionChanged(event1);
    verify(myDetachedToolWindow1, times(3)).hide();
    verify(myDetachedToolWindow2, times(2)).hide();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testFileCloseCausingFloatingToolWindowToHide() {
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(myWorkBench1, new JLabel());
    myListener.fileOpened(myEditorManager, myVirtualFile);
    verify(myDetachedToolWindow1).show(eq(myAttachedToolWindow1));

    myListener.fileClosed(myEditorManager, myVirtualFile);
    verify(myDetachedToolWindow1).hide();
  }
}
