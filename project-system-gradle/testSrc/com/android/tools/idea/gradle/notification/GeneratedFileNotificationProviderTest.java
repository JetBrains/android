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
package com.android.tools.idea.gradle.notification;

import static com.android.tools.idea.FileEditorUtil.DISABLE_GENERATED_FILE_NOTIFICATION_KEY;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.createAndroidProjectBuilderForDefaultTestProjectStructure;
import static com.android.tools.idea.testing.ProjectFiles.createFile;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.ui.EditorNotificationPanel;
import java.io.IOException;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link GeneratedFileNotificationProvider}.
 */
public class GeneratedFileNotificationProviderTest {

  @Rule
  public AndroidProjectRule androidProjectRule = AndroidProjectRule.withAndroidModel(
    createAndroidProjectBuilderForDefaultTestProjectStructure()
  );

  private FileEditor fileEditor;

  private final GeneratedFileNotificationProvider myNotificationProvider = new GeneratedFileNotificationProvider();
  private VirtualFile file;

  @Before
  public void before() throws IOException {
    EdtTestUtil.runInEdtAndWait(() -> {;
      VirtualFile buildFolder = createFolderInProjectRoot(androidProjectRule.getProject(), "build");
      file = createFile(buildFolder, "test.txt");
      fileEditor = FileEditorManager.getInstance(androidProjectRule.getProject()).openFile(file).get(0);
    });
  }

  @Nullable
  private Function<FileEditor, EditorNotificationPanel>  collectNotificationData() {
    return ApplicationManager.getApplication().runReadAction((Computable<Function<FileEditor, EditorNotificationPanel>>)() ->
      myNotificationProvider.collectNotificationData(androidProjectRule.getProject(), file));
  }

  @Test
  public void testCreateNotificationPanelWithFileInBuildFolder() {
    Function<FileEditor, EditorNotificationPanel> function = requireNonNull(collectNotificationData());
    EditorNotificationPanel panel = function.apply(fileEditor);
    assertEquals("Files under the \"build\" folder are generated and should not be edited.", panel.getText());
  }

  @Test
  public void testNotificationCanBeDisabledWithKey() throws Exception {
    fileEditor.putUserData(DISABLE_GENERATED_FILE_NOTIFICATION_KEY, Boolean.TRUE);
    assertThat(requireNonNull(collectNotificationData()).apply(fileEditor)).named("collect notification data").isNull();
  }
}

