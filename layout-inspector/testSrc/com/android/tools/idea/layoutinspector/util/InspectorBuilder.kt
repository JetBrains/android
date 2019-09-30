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
package com.android.tools.idea.layoutinspector.util

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import org.mockito.Mockito

object InspectorBuilder {

  fun setUpDemo(projectRule: AndroidProjectRule) {
    InspectorClient.clientFactory = { Mockito.mock(InspectorClient::class.java) }
    projectRule.fixture.testDataPath = TestUtils.getWorkspaceFile("tools/adt/idea/layout-inspector/testData/resource").path
    projectRule.fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML)
    projectRule.fixture.copyFileToProject("res/color/app_text_color.xml")
    projectRule.fixture.copyFileToProject("res/drawable/background_choice.xml")
    projectRule.fixture.copyFileToProject("res/drawable/battery.xml")
    projectRule.fixture.copyFileToProject("res/drawable/dsl1.xml")
    projectRule.fixture.copyFileToProject("res/drawable/dsl2.xml")
    projectRule.fixture.copyFileToProject("res/drawable/dsl3.xml")
    projectRule.fixture.copyFileToProject("res/drawable/vd.xml")
    projectRule.fixture.copyFileToProject("res/layout/demo.xml")
    projectRule.fixture.copyFileToProject("res/layout-w800dp/demo.xml")
    projectRule.fixture.copyFileToProject("res/values/colors.xml")
    projectRule.fixture.copyFileToProject("res/values/drawables.xml")
    projectRule.fixture.copyFileToProject("res/values-land/colors.xml")
    projectRule.fixture.copyFileToProject("res/values/strings.xml")
    projectRule.fixture.copyFileToProject("res/values/styles.xml")
    projectRule.fixture.copyFileToProject("res/values-land/styles.xml")
  }

  fun tearDownDemo() {
    InspectorClient.clientFactory = { DefaultInspectorClient(it) }
  }

  fun createLayoutInspectorForDemo(projectRule: AndroidProjectRule): LayoutInspector {
    val root = createDemoViewNodes()
    val inspectorModel = InspectorModel(projectRule.project, root)
    val configBuilder = ConfigurationBuilder(projectRule.module.androidFacet!!)
    val (config, stringTable) = configBuilder.makeConfiguration()
    inspectorModel.resourceLookup.updateConfiguration(config, stringTable)
    return LayoutInspector(inspectorModel)
  }

  fun findViewNode(inspector: LayoutInspector, id: String): ViewNode? {
    return findViewNode(inspector.layoutInspectorModel.root, id)
  }

  private fun findViewNode(node: ViewNode, id: String): ViewNode? {
    if (node.viewId?.name == id) {
      return node
    }
    return node.children.mapNotNull { findViewNode(it, id) }.firstOrNull()
  }

  private fun createDemoViewNodes(): ViewNode {
    val layout = ResourceReference(ResourceNamespace.TODO(), ResourceType.LAYOUT, "demo")
    val relativeLayoutId = ResourceReference(ResourceNamespace.TODO(), ResourceType.ID, "relativeLayout")
    val textViewId = ResourceReference(ResourceNamespace.TODO(), ResourceType.ID, "title")
    val relativeLayout = ViewNode(1, "RelativeLayout", layout, 0, 0, 1200, 1600, relativeLayoutId, "")
    val textView = ViewNode(1, "TextView", layout, 200, 400, 400, 100, textViewId, "@drawable/battery")
    relativeLayout.children.add(textView)
    textView.parent = relativeLayout
    return relativeLayout
  }
}
