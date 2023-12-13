/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.notifications;

import static com.android.tools.idea.testing.AndroidProjectRuleKt.onEdt;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.android.tools.idea.mlkit.MlProjectTestUtil;
import com.android.tools.idea.mlkit.viewer.TfliteModelFileEditor;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.ui.EditorNotificationPanel;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link LowAgpVersionNotificationProvider}.
 */
@RunsInEdt
public class LowAgpVersionNotificationProviderTest {

  @Rule
  public EdtAndroidProjectRule projectRule = onEdt(AndroidProjectRule.withAndroidModels());

  @Mock private VirtualFile myMockVirtualFile;
  @Mock private TfliteModelFileEditor myMockEditor;
  private Project myProject;
  private LowAgpVersionNotificationProvider myNotificationProvider;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(myMockVirtualFile.getExtension()).thenReturn("tflite");

    myNotificationProvider = new LowAgpVersionNotificationProvider();
  }

  private void setupProject(String version) {
    MlProjectTestUtil.setupTestMlProject(projectRule.getProject(), version, 28, ImmutableList.of());
    myProject = projectRule.getProject();
  }

  @Test
  public void lowAgpVersion_hasNotification() {
    setupProject("4.0.0");
    var paneProvider = myNotificationProvider.collectNotificationData(myProject, myMockVirtualFile);
    assertThat(paneProvider).isNotNull();
    assertThat(paneProvider.apply(myMockEditor)).isNotNull();
  }

  @Test
  public void qualifiedAgpVersion_noNotification() {
    setupProject("4.2.0");
    assertThat(myNotificationProvider.collectNotificationData(myProject, myMockVirtualFile)).isNull();
  }
}
