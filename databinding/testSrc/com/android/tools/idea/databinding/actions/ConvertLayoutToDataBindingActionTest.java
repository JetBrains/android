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
package com.android.tools.idea.databinding.actions;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache;
import com.android.tools.idea.databinding.TestDataPaths;
import com.android.tools.idea.testing.AndroidDomRule;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class ConvertLayoutToDataBindingActionTest {
  private final AndroidProjectRule myProjectRule = AndroidProjectRule.withSdk().initAndroid(true);
  private final AndroidDomRule myDomRule = new AndroidDomRule("res/layout", myProjectRule::getFixture);

  @Rule
  public final TestRule myRuleChain = RuleChain.outerRule(myProjectRule).around(myDomRule);

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  @NotNull
  private final DataBindingMode myDataBindingMode;

  @Parameterized.Parameters(name = "{0}")
  public static List<DataBindingMode> getModes() {
    return Lists.newArrayList(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX);
  }

  public ConvertLayoutToDataBindingActionTest(@NotNull DataBindingMode mode) {
    myDataBindingMode = mode;
  }

  @Before
  public void setUp() {
    myProjectRule.getFixture().setTestDataPath(TestDataPaths.TEST_DATA_ROOT + "/actions");

    AndroidFacet androidFacet = FacetManager.getInstance(myProjectRule.getModule()).getFacetByType(AndroidFacet.ID);
    LayoutBindingModuleCache.getInstance(androidFacet).setDataBindingMode(myDataBindingMode);
  }

  @Test
  @RunsInEdt
  public void classicLayoutCanBeConvertedToDataBindingLayout() {
    final ConvertLayoutToDataBindingAction action = new ConvertLayoutToDataBindingAction();
    myDomRule.testWriteAction("classic_layout.xml", "classic_layout_after.xml", () -> {
      Project project = myProjectRule.getProject();
      Editor editor = myProjectRule.getFixture().getEditor();
      PsiFile file = myProjectRule.getFixture().getFile();

      assertThat(action.isAvailable(project, editor, file)).isTrue();
      action.invoke(project, editor, file);
    });
  }

}
