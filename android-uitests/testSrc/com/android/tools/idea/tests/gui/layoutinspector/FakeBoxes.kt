/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.layoutinspector

import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.Property
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.PropertyGroup
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewAppContext
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewBounds
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewFlagValue
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewRect
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewResource
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewScreenshot
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.FakeViewLayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.sendEvent
import com.android.tools.idea.protobuf.ByteString
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Configuration
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Property.Type
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchResponse
import java.awt.Color

class FakeBoxes(private val viewInspector: FakeViewLayoutInspector) {

  init {
    viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      sendRootIdEvent()
      sendSingleBoxTreeEvent()
      Response.newBuilder().setStartFetchResponse(StartFetchResponse.getDefaultInstance()).build()
    }
    viewInspector.interceptWhen({ it.hasGetPropertiesCommand() }) {
      val properties = when (it.getPropertiesCommand.viewId) {
        1L -> linearLayoutProperties
        2L -> frameLayoutProperties
        3L -> button3Properties
        4L -> button4Properties
        else -> PropertyGroup {}
      }

      Response.newBuilder().apply {
        getPropertiesResponse = GetPropertiesResponse.newBuilder().apply {
          addAllStrings(strings)
          propertyGroup = properties
        }.build()
      }.build()
    }
  }

  private fun sendRootIdEvent() {
    viewInspector.connection.sendEvent {
      rootsEventBuilder.apply {
        addIds(singleBox.id)
      }
    }
  }

  private fun sendSingleBoxTreeEvent() {
    viewInspector.connection.sendEvent {
      layoutEvent = LayoutEvent.newBuilder().apply {
        addAllStrings(strings)
        rootView = singleBox
        screenshot = ViewScreenshot {
          type = Screenshot.Type.SKP
          bytes = ByteString.copyFrom(generateSingleBoxSkiaImage())
        }
        appContext = applicationContext
        configuration = appConfiguration
      }.build()
    }
  }

  fun sendMultipleBoxesTreeEvent() {
    viewInspector.connection.sendEvent {
      layoutEvent = LayoutEvent.newBuilder().apply {
        addAllStrings(strings)
        rootView = multipleBoxes
        screenshot = ViewScreenshot {
          type = Screenshot.Type.SKP
          bytes = ByteString.copyFrom(generateMultipleBoxesSkiaImage())
        }
        appContext = applicationContext
        configuration = appConfiguration
      }.build()
    }
  }

  private external fun generateSingleBoxSkiaImage(): ByteArray

  private external fun generateMultipleBoxesSkiaImage(): ByteArray

  private val strings = listOf(
    ViewString(1, "com.android.tools.tests.layout"),
    ViewString(2, "inspection"),
    ViewString(3, "id"),
    ViewString(4, "android"),
    ViewString(5, "linear1"),
    ViewString(6, "android.widget"),
    ViewString(7, "LinearLayout"),
    ViewString(8, "AppTheme"),
    ViewString(9, "style"),
    ViewString(10, "layout"),
    ViewString(11, "frame2"),
    ViewString(12, "FrameLayout"),
    ViewString(13, "button3"),
    ViewString(14, "button4"),
    ViewString(15, "androidx.appcompat.widget"),
    ViewString(16, "AppCompatButton"),
    ViewString(17, "background"),
    ViewString(18, "orientation"),
    ViewString(19, "vertical"),
    ViewString(20, "layout_width"),
    ViewString(21, "layout_height"),
    ViewString(22, "match_parent"),
    ViewString(23, "500"),
    ViewString(24, "1000"),
    ViewString(25, "backgroundTint"),
    ViewString(26, "ButtonStyle"),
    ViewString(27, "clickable"),
    ViewString(28, "gravity"),
    ViewString(29, "center"),
    ViewString(30, "Widget.Material.Button"),
    ViewString(31, "clip_horizontal"),
    ViewString(32, "clip_vertical"),
    ViewString(33, "fill"),
  )

  private val applicationContext =
    ViewAppContext(ViewResource(9, 1, 8)) // "@style/AppTheme"

  private val appConfiguration =
    Configuration.newBuilder().apply {
      fontScale = 1.0f
    }.build()

  private val layoutFile =
    ViewResource(10, 1, 2) // "@layout/inspection"

  private val buttonStyleReference =
    ViewResource(9,1, 26) // "@style/ButtonStyle

  private val buttonMaterialReference =
    ViewResource(9, 1, 30) // "@style/Widget.Material.Button

  private val singleBox =
    ViewNode {
      id = 1
      resource = ViewResource(3, 1, 5) // "@id/linear1"
      packageName = 6 // "android.widget"
      className = 7 // "LinearLayout"
      bounds = ViewBounds(ViewRect(1000, 2000))
      layoutResource = layoutFile
    }

  private val multipleBoxes =
    ViewNode {
      id = 1
      resource = ViewResource(3, 1, 5) // "@id/linear1"
      packageName = 6 // "android.widget"
      className = 7 // "LinearLayout"
      bounds = ViewBounds(ViewRect(1000, 2000))
      layoutResource = layoutFile

      ViewNode {
        id = 2
        resource = ViewResource(3, 1, 11) // "@id/frame2"
        packageName = 6 // "android.widget"
        className = 12 // "FrameLayout"
        bounds = ViewBounds(ViewRect(100, 100, 500, 1000))
        layoutResource = layoutFile

        ViewNode {
          id = 3
          resource = ViewResource(3, 1, 13) // "@id/button3"
          packageName = 15 // "androidx.appcompat.widget"
          className = 16 // "AppCompatButton"
          bounds = ViewBounds(ViewRect(200, 200, 200, 500))
          layoutResource = layoutFile
        }
      }
      ViewNode {
        id = 4
        resource = ViewResource(3, 1, 14) // "@id/button4"
        packageName = 15 // "androidx.appcompat.widget"
        className = 16 // "AppCompatButton"
        bounds = ViewBounds(ViewRect(300, 1200, 400, 500))
        layoutResource = layoutFile
      }
    }

  private val linearLayoutProperties =
    PropertyGroup {
      viewId = 1 // linearLayout id
      layout = layoutFile
      Property {
        name = 3 // "id"
        namespace = 4 // "android"
        type = Type.RESOURCE
        resourceValue = ViewResource(3, 1, 5)
      }
      Property {
        name = 17 // "background"
        namespace = 4 // "android"
        type = Type.COLOR
        int32Value = Color.YELLOW.rgb
        source = layoutFile
        addResolutionStack(layoutFile)
      }
      Property {
        name = 18 // "orientation"
        namespace = 4 // "android"
        type = Type.INT_ENUM
        int32Value = 19 // "vertical"
        source = layoutFile
      }
      Property {
        name = 20 // "layout_width"
        namespace = 4 // "android"
        type = Type.INT_ENUM
        int32Value = 22 // "match_parent"
        source = layoutFile
        isLayout = true
      }
      Property {
        name = 21 // "layout_height"
        namespace = 4 // "android"
        type = Type.INT_ENUM
        int32Value = 22 // "match_parent"
        source = layoutFile
        isLayout = true
      }
      Property {
        name = 23 // "elevation"
        namespace = 4 // "android"
        type = Type.FLOAT
        floatValue = 0.0f
      }
    }

  private val frameLayoutProperties =
    PropertyGroup {
      viewId = 2 // frameLayout id
      layout = layoutFile
      Property {
        name = 3 // "id"
        namespace = 4 // "android"
        type = Type.RESOURCE
        resourceValue = ViewResource(3, 1, 11) // "@id/frame2
      }
      Property {
        name = 17 // "background"
        namespace = 4 // "android"
        type = Type.COLOR
        int32Value = Color.BLUE.rgb
        source = layoutFile
        addResolutionStack(layoutFile)
      }
      Property {
        name = 20 // "layout_width"
        namespace = 4 // "android"
        type = Type.INT_ENUM
        int32Value = 23 // "500"
        source = layoutFile
        isLayout = true
      }
      Property {
        name = 21 // "layout_height"
        namespace = 4 // "android"
        type = Type.INT_ENUM
        int32Value = 24 // "1000"
        source = layoutFile
        isLayout = true
      }
    }

  private val button3Properties =
    PropertyGroup {
      viewId = 3 // button3 id
      layout = layoutFile
      Property {
        name = 3 // "id"
        namespace = 4 // "android"
        type = Type.RESOURCE
        resourceValue = ViewResource(3, 1, 13) // "@id/button3"
      }
      Property {
        name = 25 // "backgroundTint"
        namespace = 4 // "android"
        type = Type.COLOR
        int32Value = Color.BLACK.rgb
        source = layoutFile
        addResolutionStack(layoutFile)
        addResolutionStack(buttonStyleReference)
      }
      Property {
        name = 20 // "layout_width"
        namespace = 4 // "android"
        type = Type.INT32
        int32Value = 200
        source = layoutFile
        isLayout = true
      }
      Property {
        name = 21 // "layout_height"
        namespace = 4 // "android"
        type = Type.INT32
        int32Value = 500
        source = layoutFile
        isLayout = true
      }
    }

  private val button4Properties =
    PropertyGroup {
      viewId = 4 // button4 id
      layout = layoutFile
      Property {
        name = 3 // "id"
        namespace = 4 // "android"
        type = Type.RESOURCE
        resourceValue = ViewResource(3, 1, 14) // "@id/button4"
      }
      Property {
        name = 25 // "backgroundTint"
        namespace = 4 // "android"
        type = Type.COLOR
        int32Value = Color.RED.rgb
        source = layoutFile
        addResolutionStack(layoutFile)
        addResolutionStack(buttonStyleReference)
      }
      Property {
        name = 27 // "clickable"
        namespace = 4 // "android"
        type = Type.BOOLEAN
        int32Value = 1
      }
      Property {
        name = 23 // "elevation"
        namespace = 4 // "android"
        type = Type.FLOAT
        floatValue = 5.5f
      }
      Property {
        name = 28 // "gravity"
        namespace = 4 // "android"
        type = Type.GRAVITY
        flagValue = ViewFlagValue(29) // "center"
        source = buttonMaterialReference
        addResolutionStack(buttonMaterialReference)
      }
      Property {
        name = 20 // "layout_width"
        namespace = 4 // "android"
        type = Type.INT32
        int32Value = 400
        source = layoutFile
        isLayout = true
      }
      Property {
        name = 21 // "layout_height"
        namespace = 4 // "android"
        type = Type.INT32
        int32Value = 500
        source = layoutFile
        isLayout = true
      }
      Property {
        name = 30 // "layout_gravity
        namespace = 4 // "android"
        type = Type.GRAVITY
        flagValue = ViewFlagValue(31, 32, 33) // "clip_horizontal", "clip_vertical", "fill"
      }
    }
}
