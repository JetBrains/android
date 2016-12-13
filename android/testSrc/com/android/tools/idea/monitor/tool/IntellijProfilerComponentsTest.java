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
package com.android.tools.idea.monitor.tool;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IntellijProfilerComponentsTest {

  @Mock private VirtualFile myVirtualFile;
  @Mock private FileEditorProviderManager myFileEditorProviderManager;
  @Mock private Project myProject;
  @Mock private JComponent myComponent;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void editorIsNullIfVirtualFileIsNull() {
    assertNull(IntellijProfilerComponents.getFileViewer(null, myFileEditorProviderManager, myProject));
  }

  @Test
  public void editorIsNullIfProjectIsNull() {
    assertNull(IntellijProfilerComponents.getFileViewer(myVirtualFile, myFileEditorProviderManager, null));
  }

  @Test
  public void editorIsProperValueFromInput() {
    FileEditorProvider provider = mock(FileEditorProvider.class);
    setupEditorProvider(provider);
    when(myFileEditorProviderManager.getProviders(eq(myProject), eq(myVirtualFile)))
      .thenReturn(new FileEditorProvider[]{provider});

    JComponent actualEditor = IntellijProfilerComponents.getFileViewer(myVirtualFile, myFileEditorProviderManager, myProject);
    assertEquals(myComponent, actualEditor);
  }

  @Test
  public void editorIsNullIfProvidersNotFound() {
    when(myFileEditorProviderManager.getProviders(eq(myProject), eq(myVirtualFile))).thenReturn(new FileEditorProvider[]{});
    assertNull(IntellijProfilerComponents.getFileViewer(myVirtualFile, myFileEditorProviderManager, myProject));
  }

  private void setupEditorProvider(FileEditorProvider provider) {
    FileEditor fileEditor = mock(FileEditor.class);
    when(provider.createEditor(eq(myProject), eq(myVirtualFile))).thenReturn(fileEditor);
    when(fileEditor.getComponent()).thenReturn(myComponent);
  }
}
