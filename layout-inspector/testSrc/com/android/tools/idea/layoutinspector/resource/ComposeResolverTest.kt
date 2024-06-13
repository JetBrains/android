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
package com.android.tools.idea.layoutinspector.resource

import com.android.test.testutils.TestUtils
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class ComposeResolverTest {

  private val projectRule = AndroidProjectRule.withSdk()
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fileOpenCaptureRule).around(EdtRule())!!

  @Before
  fun setup() {
    loadComposeFiles()
  }

  @RunsInEdt
  @Test
  fun testColumnNode() {
    val model = createModel()
    val composable = model[-2] as ComposeViewNode
    val resolver = ComposeResolver(projectRule.project)
    val navigatable = resolver.findComposableNavigatable(composable)
    navigatable!!.navigate(true)
    fileOpenCaptureRule.checkEditor(
      "MyCompose.kt",
      17,
      "modifier = Modifier.padding(20.dp).clickable(onClick = { selectColumn() }),",
    )
  }

  @RunsInEdt
  @Test
  fun testComposeViewNodeInOtherFileWithSameName() {
    val model = createModel()
    val composable = model[-5] as ComposeViewNode
    val resolver = ComposeResolver(projectRule.project)
    val navigatable = resolver.findComposableNavigatable(composable)
    navigatable!!.navigate(true)
    fileOpenCaptureRule.checkEditor("MyCompose.kt", 8, "Text(text = \"Hello \$name!\")")
  }

  private fun loadComposeFiles() {
    val fixture = projectRule.fixture
    fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/compose").toString()
    fixture.copyFileToProject("java/com/example/MyCompose.kt")
    fixture.copyFileToProject("java/com/example/composable/MyCompose.kt")
  }

  private fun createModel(): InspectorModel =
    model(
      projectRule.testRootDisposable,
      projectRule.project,
      FakeTreeSettings(),
      body =
        DemoExample.setUpDemo(projectRule.fixture) {
          view(0, qualifiedName = "androidx.ui.core.AndroidComposeView") {
            compose(-2, "Column", "MyCompose.kt", 49835523, 540, 17) {
              compose(-3, "Text", "MyCompose.kt", 49835523, 593, 18)
              compose(-4, "Greeting", "MyCompose.kt", 49835523, 622, 19) {
                compose(-5, "Text", "MyCompose.kt", 1216697758, 164, 3)
              }
            }
          }
        },
    )
}
