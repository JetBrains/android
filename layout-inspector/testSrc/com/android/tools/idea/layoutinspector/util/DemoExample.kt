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

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FQCN_RELATIVE_LAYOUT
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.layoutinspector.InspectorModelDescriptor
import com.android.tools.idea.layoutinspector.InspectorViewDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

const val DECOR_VIEW = "com.android.internal.policy.DecorView"

object DemoExample {

  fun setUpDemo(fixture: CodeInsightTestFixture, body: InspectorViewDescriptor.() -> Unit = {}): InspectorModelDescriptor.() -> Unit {
    fixture.testDataPath = resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/resource").toString()
    fixture.copyFileToProject(FN_ANDROID_MANIFEST_XML)
    fixture.copyFileToProject("res/color/app_text_color.xml")
    fixture.copyFileToProject("res/drawable/background_choice.xml")
    fixture.copyFileToProject("res/drawable/battery.xml")
    fixture.copyFileToProject("res/drawable/dsl1.xml")
    fixture.copyFileToProject("res/drawable/dsl2.xml")
    fixture.copyFileToProject("res/drawable/dsl3.xml")
    fixture.copyFileToProject("res/drawable/vd.xml")
    fixture.copyFileToProject("res/layout/demo.xml")
    fixture.copyFileToProject("res/layout/design_tab_text.xml")
    fixture.copyFileToProject("res/layout-w800dp/demo.xml")
    fixture.copyFileToProject("res/values/colors.xml")
    fixture.copyFileToProject("res/values/drawables.xml")
    fixture.copyFileToProject("res/values-land/colors.xml")
    fixture.copyFileToProject("res/values/strings.xml")
    fixture.copyFileToProject("res/values/styles.xml")
    fixture.copyFileToProject("res/values-land/styles.xml")

    return createDemoViewNodes(body)
  }

  private fun createDemoViewNodes(body: InspectorViewDescriptor.() -> Unit): InspectorModelDescriptor.() -> Unit = {
    val namespace = ResourceNamespace.fromPackageName("com.example")
    val layout = ResourceReference(namespace, ResourceType.LAYOUT, "demo")
    val relativeLayoutId = ResourceReference(namespace, ResourceType.ID, "relativeLayout")
    val textViewId = ResourceReference(namespace, ResourceType.ID, "title")
    this.also {
      view(1, 0, 0, 1200, 1600, qualifiedName = DECOR_VIEW) {
        view(2, 0, 0, 1200, 1600, qualifiedName = FQCN_RELATIVE_LAYOUT, viewId = relativeLayoutId, layout = layout) {
          view(3, 200, 400, 400, 100, qualifiedName = FQCN_TEXT_VIEW, viewId = textViewId, textValue = "@drawable/battery", layout = layout,
               body = body)
        }
      }
    }
  }
}
