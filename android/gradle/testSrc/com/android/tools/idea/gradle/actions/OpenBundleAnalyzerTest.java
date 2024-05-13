/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.testing.IdeComponents;
import com.intellij.notification.Notification;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;

import java.awt.Component;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link GoToBundleLocationTask}.
 */
public class OpenBundleAnalyzerTest extends HeavyPlatformTestCase {
  private File myTmpDir;
  private File myBundle;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // In order not to violate the assertion in TestEditorManagerImpl.java
    // that return of getDocument is not null, .aab extension is omitted here.
    myBundle = createTempFile("myFooAppBundle", "foo..foo..foo..");
    myTmpDir = myBundle.getParentFile();
  }

  public void testOpenAnalyzerOpenDir() {
    VirtualFile target = findFileByIoFile(myBundle, true);
    IdeComponents ideComponents = new IdeComponents(getProject());
    ideComponents.replaceApplicationService(FileChooserFactory.class, new FileChooserFactoryImpl() {
      @NotNull
      @Override
      public FileChooserDialog createFileChooser(@NotNull FileChooserDescriptor descriptor,
                                                 @Nullable Project project,
                                                 @Nullable Component parent) {
        FileChooserDialog dialog = mock(FileChooserDialog.class);
        when(dialog.choose(eq(myProject), any())).thenReturn(new VirtualFile[]{target});
        return dialog;
      }
    });

    Map<String, File> bundlePathsMap = new HashMap<>();
    bundlePathsMap.put("fooApp", myTmpDir);

    GoToBundleLocationTask.OpenFolderNotificationListener listener =
      new GoToBundleLocationTask.OpenFolderNotificationListener(myProject, bundlePathsMap);
    HyperlinkEvent e = new HyperlinkEvent(new Object(), HyperlinkEvent.EventType.ACTIVATED, null, "analyze:fooApp");
    listener.hyperlinkActivated(mock(Notification.class), e);
    assertTrue(FileEditorManager.getInstance(myProject).isFileOpen(target));
  }

  public void testOpenAnalyzerOpenFile() {
    VirtualFile target = findFileByIoFile(myBundle, true);
    Map<String, File> bundlePathsMap = new HashMap<>();
    bundlePathsMap.put("fooApp", myBundle);

    GoToBundleLocationTask.OpenFolderNotificationListener listener =
      new GoToBundleLocationTask.OpenFolderNotificationListener(myProject, bundlePathsMap);
    HyperlinkEvent e = new HyperlinkEvent(new Object(), HyperlinkEvent.EventType.ACTIVATED, null, "analyze:fooApp");
    listener.hyperlinkActivated(mock(Notification.class), e);
    assertTrue(FileEditorManager.getInstance(myProject).isFileOpen(target));
  }
}