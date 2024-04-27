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
package com.android.tools.idea.databinding

import com.android.tools.idea.databinding.util.BrUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class BrUtilTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val edtRule = EdtRule()

  // Project rule initialization must NOT happen on the EDT thread
  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(edtRule)

  // Legal cast because project rule is initialized with onDisk
  private val fixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT + "/databinding"
  }

  @Test
  @RunsInEdt
  fun collectIds() {
    fixture.copyFileToProject("src/p1/p2/ModelWithIds.java")
    val modelClass = fixture.findClass("p1.p2.ModelWithIds")

    val methodsAndFields = mutableListOf<PsiModifierListOwner>()
    methodsAndFields.addAll(modelClass.fields)
    methodsAndFields.addAll(modelClass.methods)

    assertThat(BrUtil.collectIds(methodsAndFields))
      .containsExactly("length", "size", "sum", "value", "count", "text", "enabled")
  }
}
