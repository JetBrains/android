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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorPropertiesModel
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.layoutinspector.view
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import org.mockito.Mockito

object InspectorBuilder {

  fun setUpDemo(projectRule: AndroidProjectRule) {
    InspectorClient.clientFactory = { Mockito.mock(InspectorClient::class.java) }
    projectRule.fixture.testDataPath = TestUtils.getWorkspaceFile("tools/adt/idea/layout-inspector/testData/resource").path
    projectRule.fixture.copyFileToProject(FN_ANDROID_MANIFEST_XML)
    projectRule.fixture.copyFileToProject("res/color/app_text_color.xml")
    projectRule.fixture.copyFileToProject("res/drawable/background_choice.xml")
    projectRule.fixture.copyFileToProject("res/drawable/battery.xml")
    projectRule.fixture.copyFileToProject("res/drawable/dsl1.xml")
    projectRule.fixture.copyFileToProject("res/drawable/dsl2.xml")
    projectRule.fixture.copyFileToProject("res/drawable/dsl3.xml")
    projectRule.fixture.copyFileToProject("res/drawable/vd.xml")
    projectRule.fixture.copyFileToProject("res/layout/demo.xml")
    projectRule.fixture.copyFileToProject("res/layout/design_tab_text.xml")
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
    val inspectorModel = InspectorModel(projectRule.project)
    inspectorModel.update(root, root.drawId, listOf(root.drawId))
    val configBuilder = ConfigurationBuilder(projectRule.module.androidFacet!!)
    val (config, stringTable) = configBuilder.makeConfiguration()
    inspectorModel.resourceLookup.updateConfiguration(config, stringTable)
    return LayoutInspector(inspectorModel)
  }

  fun createModel(projectRule: AndroidProjectRule): InspectorPropertiesModel {
    val model = InspectorPropertiesModel()
    model.layoutInspector = createLayoutInspectorForDemo(projectRule)
    return model
  }

  fun createProperty(viewId: String,
                     attrName: String,
                     type: LayoutInspectorProto.Property.Type,
                     source: ResourceReference?,
                     model: InspectorPropertiesModel): InspectorPropertyItem {
    val inspectorModel = model.layoutInspector?.layoutInspectorModel!!
    val node = inspectorModel[viewId]!!
    return InspectorPropertyItem(
      ANDROID_URI, attrName, attrName, type, null, PropertySection.DECLARED, source ?: node.layout, node, inspectorModel.resourceLookup)
  }

  private fun createDemoViewNodes(): ViewNode {
    val layout = ResourceReference(ResourceNamespace.TODO(), ResourceType.LAYOUT, "demo")
    val relativeLayoutId = ResourceReference(ResourceNamespace.TODO(), ResourceType.ID, "relativeLayout")
    val textViewId = ResourceReference(ResourceNamespace.TODO(), ResourceType.ID, "title")
    return view(1, 0, 0, 1200, 1600, "RelativeLayout", relativeLayoutId, layout = layout) {
      view(1, 200, 400, 400, 100, "TextView", textViewId, "@drawable/battery", layout = layout)
    }
  }
}
