/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.actions;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.databinding.TestDataPaths;
import com.android.tools.idea.testing.AndroidDomRule;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public final class ConvertLayoutToDataBindingActionTest {
  private final AndroidProjectRule myProjectRule = AndroidProjectRule.withSdk().initAndroid(true);
  private final AndroidDomRule myDomRule = new AndroidDomRule("res/layout", () -> myProjectRule.fixture);

  @Rule
  public final TestRule myRuleChain = RuleChain.outerRule(myProjectRule).around(myDomRule);

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  @Before
  public void setUp() {
    myProjectRule.fixture.setTestDataPath(TestDataPaths.TEST_DATA_ROOT + "/actions");
  }

  @Test
  @RunsInEdt
  public void classicLayoutCanBeConvertedToDataBindingLayout() {
    final ConvertLayoutToDataBindingAction action = new ConvertLayoutToDataBindingAction() {
      @Override
      protected boolean isUsingDataBinding(@NotNull Project project) {
        return true;
      }
    };
    myDomRule.testWriteAction("classic_layout.xml", "classic_layout_after.xml", () -> {
      Project project = myProjectRule.getProject();
      Editor editor = myProjectRule.fixture.getEditor();
      PsiFile file = myProjectRule.fixture.getFile();

      assertThat(action.isAvailable(project, editor, file)).isTrue();
      action.invoke(project, editor, file);
    });
  }

}
