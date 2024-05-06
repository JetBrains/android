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
package com.android.tools.idea.mlkit.importmodel;

import static com.android.tools.idea.mlkit.importmodel.ImportMlModelAction.MIN_AGP_VERSION;
import static com.android.tools.idea.mlkit.importmodel.ImportMlModelAction.MIN_SDK_VERSION;
import static com.android.tools.idea.mlkit.importmodel.ImportMlModelAction.TITLE;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidProjectRuleKt.onEdt;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.mlkit.MlProjectTestUtil;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.TestActionEvent;
import org.jetbrains.android.util.AndroidBundle;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link ImportMlModelAction}.
 */
@RunsInEdt
public class ImportMlModelActionTest {

  private AnActionEvent myEvent;
  private ImportMlModelAction myAction;

  @Rule
  public EdtAndroidProjectRule projectRule = onEdt(AndroidProjectRule.withAndroidModels());

  @Before
  public void setUp() {
    myAction = new ImportMlModelAction();
  }

  private void setupProject(String version, int version2) {
    MlProjectTestUtil.setupTestMlProject(projectRule.getProject(), version, version2);
    myEvent = TestActionEvent.createTestEvent(new MapDataContext(
      ImmutableMap.of(
        CommonDataKeys.PROJECT, projectRule.getProject(),
        PlatformCoreDataKeys.MODULE, gradleModule(projectRule.getProject(), ":")
      )
    ));
  }

  @Test
  public void allConditionsMet_shouldEnabledPresentation() {
    setupProject(MIN_AGP_VERSION, MIN_SDK_VERSION);
    myAction.update(myEvent);
    assertThat(myEvent.getPresentation().isEnabled()).isTrue();
  }

  @Test
  public void lowAgpVersion_shouldDisablePresentation() {
    setupProject("3.6.0", MIN_SDK_VERSION);

    myAction.update(myEvent);

    assertThat(myEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myEvent.getPresentation().getText()).isEqualTo(
      AndroidBundle.message("android.wizard.action.requires.new.agp", TITLE, MIN_AGP_VERSION));
  }

  @Test
  public void lowMinSdkApi_shouldDisablePresentation() {
    setupProject(MIN_AGP_VERSION, MIN_SDK_VERSION - 2);

    myAction.update(myEvent);

    assertThat(myEvent.getPresentation().isEnabled()).isFalse();
    assertThat(myEvent.getPresentation().getText()).isEqualTo(
      AndroidBundle.message("android.wizard.action.requires.minsdk", TITLE, MIN_SDK_VERSION));
  }
}
