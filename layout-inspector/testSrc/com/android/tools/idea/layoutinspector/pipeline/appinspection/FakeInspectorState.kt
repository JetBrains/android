/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.resources.Density
import com.android.tools.idea.layoutinspector.model.FLAG_HAS_MERGED_SEMANTICS
import com.android.tools.idea.layoutinspector.model.FLAG_HAS_UNMERGED_SEMANTICS
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableRoot
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.Element
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ExpandedParameter
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.Parameter
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ParameterGroup
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.Property
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.PropertyGroup
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.Reference
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewResource
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.FakeComposeLayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.FakeViewLayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.sendEvent
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParameterDetailsCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol

// Hand-crafted state loosely based on new basic activity app. Real data would look a lot more scattered.
class FakeInspectorState(
  private val viewInspector: FakeViewLayoutInspector,
  private val composeInspector: FakeComposeLayoutInspector,
) {

  private val viewStrings = listOf(
    // layout strings
    ViewString(1, "androidx.constraintlayout.widget"),
    ViewString(2, "ConstraintLayout"),
    ViewString(3, "androidx.fragment.app"),
    ViewString(4, "FragmentContainerView"),
    ViewString(5, "com.google.android.material.textview"),
    ViewString(6, "MaterialTextView"),
    ViewString(7, "com.google.android.material.button"),
    ViewString(8, "MaterialButton"),
    ViewString(9, "com.google.android.material.floatingactionbutton"),
    ViewString(10, "FloatingActionButton"),
    ViewString(11, "android.view"),
    ViewString(12, "View"),
    ViewString(13, "ComposeView"),

    // property names
    // TODO(b/177231212): Test remaining property types
    ViewString(101, "text"), // STRING
    ViewString(102, "clickable"), // BOOLEAN
    // placeholder for BYTE property
    // placeholder for CHAR property
    // placeholder for DOUBLE property
    ViewString(106, "alpha"), // FLOAT
    // placeholder for INT16 property
    ViewString(108, "minWidth"), // INT32
    // placeholder for INT64 property
    // placeholder for OBJECT property
    ViewString(111, "background"), // COLOR
    ViewString(112, "gravity"), // GRAVITY
    ViewString(113, "orientation"), // INT_ENUM
    ViewString(114, "imeOptions"), // INT_FLAG
    ViewString(115, "id"), // RESOURCE
    ViewString(116, "src"), // DRAWABLE
    // placeholder for ANIM property
    ViewString(118, "stateListAnimator"), // ANIMATOR
    // placeholder for INTERPOLATOR property
    // placeholder for DIMENSION property

    // property values
    ViewString(201, "Next"),
    ViewString(202, "top"),
    ViewString(203, "start"),
    ViewString(204, "normal"),
    ViewString(205, "actionUnspecified"),
    ViewString(206, "id"),
    ViewString(207, "layout"),
    ViewString(208, "style"),
    ViewString(209, "android"),
    ViewString(210, "com.example"),
    ViewString(211, "fab"),
    ViewString(212, "activity_main"),
    ViewString(213, "fragment_first"),
    ViewString(214, "Widget.MaterialComponents.FloatingActionButton"),
    ViewString(215, "Widget.Design.FloatingActionButton"),
    ViewString(216, "Widget.MaterialComponents.Button"),
    ViewString(217, "Widget.AppCompat.Button"),
    ViewString(218, "Base.Widget.AppCompat.Button"),
    ViewString(219, "Widget.Material.Button"),
    ViewString(220, "vertical"),
  )

  private val layoutTrees = listOf(
    ViewNode {
      id = 1
      packageName = 1
      className = 2
      ViewNode {
        id = 2
        packageName = 3
        className = 4
        ViewNode {
          id = 3
          packageName = 5
          className = 6
        }
        ViewNode {
          id = 4
          packageName = 7
          className = 8
        }
        ViewNode {
          id = 5
          packageName = 9
          className = 10
        }
      }
      ViewNode {
        id = 6
        packageName = 11
        className = 13
      }
    },
    ViewNode {
      id = 101
      packageName = 11
      className = 12
    }
  )

  private val propertyGroups = mutableMapOf<Long, List<LayoutInspectorViewProtocol.PropertyGroup>>().apply {
    this[layoutTrees[0].id] = listOf(
      PropertyGroup {
        viewId = 3
        Property {
          name = 101
          type = LayoutInspectorViewProtocol.Property.Type.STRING
          int32Value = 201
        }
        Property {
          name = 102
          type = LayoutInspectorViewProtocol.Property.Type.BOOLEAN
          int32Value = 1
        }
        Property {
          name = 106
          type = LayoutInspectorViewProtocol.Property.Type.FLOAT
          floatValue = 1.0f
        }
      },
      PropertyGroup {
        viewId = 4
        Property {
          name = 108
          type = LayoutInspectorViewProtocol.Property.Type.INT32
          int32Value = 200
        }
        Property {
          name = 111
          type = LayoutInspectorViewProtocol.Property.Type.COLOR
          int32Value = -13172557
        }
        Property {
          name = 112
          type = LayoutInspectorViewProtocol.Property.Type.GRAVITY
          flagValueBuilder.addAllFlag(listOf(202, 203))
        }
        Property {
          name = 113
          type = LayoutInspectorViewProtocol.Property.Type.INT_ENUM
          int32Value = 220
        }
      },
      PropertyGroup {
        viewId = 5
        Property {
          name = 114
          type = LayoutInspectorViewProtocol.Property.Type.INT_FLAG
          flagValueBuilder.addAllFlag(listOf(204, 205))
        }
        Property {
          name = 115
          type = LayoutInspectorViewProtocol.Property.Type.RESOURCE
          source = ViewResource(207, 206, 212)
          addAllResolutionStack(listOf(
            ViewResource(207, 210, 212),
            ViewResource(208, 210, 214),
            ViewResource(208, 210, 215),
          ))
          resourceValue = ViewResource(206, 210, 211)
        }
        Property {
          name = 116
          type = LayoutInspectorViewProtocol.Property.Type.DRAWABLE
          source = ViewResource(208, 210, 216)
          addAllResolutionStack(listOf(
            ViewResource(207, 210, 213),
            ViewResource(208, 210, 216),
            ViewResource(208, 210, 217),
            ViewResource(208, 210, 218),
            ViewResource(208, 209, 219),
          ))
          int32Value = 141
        }
        Property {
          name = 118
          type = LayoutInspectorViewProtocol.Property.Type.ANIMATOR
          source = ViewResource(208, 210, 216)
          addAllResolutionStack(listOf(
            ViewResource(207, 210, 213),
            ViewResource(208, 210, 216),
            ViewResource(208, 210, 217),
            ViewResource(208, 210, 218),
            ViewResource(208, 209, 219),
          ))
          int32Value = 146
        }
      })
    // As tests don't need them, just skip defining properties for anything in the second layout tree
    this[layoutTrees[1].id] = emptyList()
  }

  private val composeStrings = listOf(
    // layout strings
    ComposableString(1, "com.example"),
    ComposableString(2, "File1.kt"),
    ComposableString(3, "File2.kt"),
    ComposableString(4, "Surface"),
    ComposableString(5, "Button"),
    ComposableString(6, "Text"),
    ComposableString(7, "DataObjectComposable"),

    // parameter names
    // TODO(b/177231212): Test remaining parameter types
    ComposableString(101, "text"), // STRING
    ComposableString(102, "clickable"), // BOOLEAN
    // placeholder for DOUBLE parameter
    // placeholder for FLOAT parameter
    ComposableString(105, "maxLines"), // INT32
    // placeholder for INT64 parameter
    ComposableString(107, "color"), // COLOR
    // placeholder for RESOURCE parameter
    ComposableString(109, "elevation"), // DIMENSION_DP
    ComposableString(110, "fontSize"), // DIMENSION_SP
    ComposableString(111, "textSize"), // DIMENSION_EM
    ComposableString(112, "onTextLayout"), // LAMBDA
    // placeholder for FUNCTION_REFERENCE parameter
    ComposableString(114, "dataObject"),
    ComposableString(115, "intProperty"),
    ComposableString(116, "stringProperty"),
    ComposableString(117, "lines"),

    // parameter values
    ComposableString(201, "placeholder"),
    ComposableString(202, "lambda"),
    ComposableString(203, "PojoClass"),
    ComposableString(204, "stringValue"),
    ComposableString(205, "MyLineClass"),
  )

  // Composable tree that lives under ComposeView
  private val composableRoot = ComposableRoot {
    viewId = 6
    ComposableNode {
      id = -2 // -1 reserved by inspectorModel
      packageHash = 1
      filename = 2
      name = 4

      ComposableNode {
        id = -3
        packageHash = 1
        filename = 2
        name = 5

        ComposableNode {
          id = -4
          packageHash = 1
          filename = 2
          name = 6
          flags = FLAG_HAS_MERGED_SEMANTICS or FLAG_HAS_UNMERGED_SEMANTICS
        }
      }

      ComposableNode {
        id = -5
        packageHash = 1
        filename = 3
        name = 7
      }
    }
  }

  // Composable tree that lives under ComposeView
  private val composableRootWithoutSemantics = ComposableRoot {
    viewId = 6
    ComposableNode {
      id = -2 // -1 reserved by inspectorModel
      packageHash = 1
      filename = 2
      name = 4
    }
  }

  private val parameterGroups = listOf(
    ParameterGroup {
      composableId = -2
      Parameter {
        type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
        name = 101
        int32Value = 201
      }
      Parameter {
        type = LayoutInspectorComposeProtocol.Parameter.Type.BOOLEAN
        name = 102
        int32Value = 1
      }
    },
    ParameterGroup {
      composableId = -3
      Parameter {
        type = LayoutInspectorComposeProtocol.Parameter.Type.INT32
        name = 105
        int32Value = 16
      }
      Parameter {
        type = LayoutInspectorComposeProtocol.Parameter.Type.COLOR
        name = 107
        int32Value = -13172557
      }
    },
    ParameterGroup {
      composableId = -4
      Parameter {
        type = LayoutInspectorComposeProtocol.Parameter.Type.DIMENSION_DP
        name = 109
        floatValue = 1f
      }
      Parameter {
        type = LayoutInspectorComposeProtocol.Parameter.Type.DIMENSION_SP
        name = 110
        floatValue = 16f
      }
      Parameter {
        type = LayoutInspectorComposeProtocol.Parameter.Type.DIMENSION_EM
        name = 111
        floatValue = 2f
      }
    },
    ParameterGroup {
      composableId = -5
      Parameter {
        type = LayoutInspectorComposeProtocol.Parameter.Type.LAMBDA
        name = 112
        lambdaValueBuilder.apply {
          packageName = 1
          fileName = 3
          lambdaName = 202
          startLineNumber = 20
          endLineNumber = 21
        }
      }
      Parameter {
        type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
        name = 114
        int32Value = 203
        Element {
          type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
          name = 116
          int32Value = 204
          index = 0
          Reference {
            composableId = -5
            kind = LayoutInspectorComposeProtocol.ParameterReference.Kind.NORMAL
            parameterIndex = 1
          }
        }
        Element {
          type = LayoutInspectorComposeProtocol.Parameter.Type.INT32
          name = 115
          int32Value = 812
          index = 1
        }
        Element {
          type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
          name = 117
          int32Value = 205
          index = 11
          Reference {
            composableId = -5
            kind = LayoutInspectorComposeProtocol.ParameterReference.Kind.NORMAL
            parameterIndex = 1
            addCompositeIndex(11)
          }
        }
      }
    }
  )

  private val expandedStrings = listOf(
    // parameter names
    ComposableString(1, "lines"),
    ComposableString(2, "firstLine"),
    ComposableString(3, "lastLine"),
    ComposableString(4, "list"),
    ComposableString(5, "[0]"),
    ComposableString(6, "[3]"),

    // String values
    ComposableString(21, "MyLineClass"),
    ComposableString(22, "Hello World"),
    ComposableString(23, "End of Text"),
    ComposableString(24, "List[12]"),
    ComposableString(25, "a"),
    ComposableString(26, "b"),
  )

  private val expandedParameter =
    ExpandedParameter {
      type = LayoutInspectorComposeProtocol.Parameter.Type.INT32
      name = 1
      int32Value = 21
      index = 11
      Element {
        type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
        name = 2
        int32Value = 22
        index = 0
      }
      Element {
        type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
        name = 3
        int32Value = 23
        index = 1
      }
      Element {
        type = LayoutInspectorComposeProtocol.Parameter.Type.ITERABLE
        name = 4
        int32Value = 24
        index = 3
        Reference {
          composableId = -5
          kind = LayoutInspectorComposeProtocol.ParameterReference.Kind.NORMAL
          parameterIndex = 1
          addAllCompositeIndex(listOf(11, 3))
        }
        Element {
          type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
          name = 5
          int32Value = 25
          index = 0
        }
        Element {
          type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
          name = 6
          int32Value = 26
          index = 3
        }
      }
    }

  private val firstExpandedListStrings = listOf(
    // parameter names
    ComposableString(1, "list"),
    ComposableString(2, "[4]"),
    ComposableString(3, "[6]"),

    // String values
    ComposableString(21, "List[12]"),
    ComposableString(22, "c"),
    ComposableString(23, "d"),
  )

  private val firstExpandedListParameter =
    ExpandedParameter {
      type = LayoutInspectorComposeProtocol.Parameter.Type.ITERABLE
      name = 1
      int32Value = 21
      index = 3
      Reference {
        composableId = -5
        kind = LayoutInspectorComposeProtocol.ParameterReference.Kind.NORMAL
        parameterIndex = 1
        addAllCompositeIndex(listOf(11, 3))
      }
      Element {
        type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
        name = 2
        int32Value = 22
        index = 4
      }
      Element {
        type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
        name = 3
        int32Value = 23
        index = 6
      }
    }

  private val secondExpandedListStrings = listOf(
    // parameter names
    ComposableString(1, "list"),
    ComposableString(2, "[7]"),
    ComposableString(3, "[10]"),
    ComposableString(4, "[11]"),

    // String values
    ComposableString(21, "List[12]"),
    ComposableString(22, "e"),
    ComposableString(23, "f"),
    ComposableString(24, "g"),
  )

  private val secondExpandedListParameter =
    ExpandedParameter {
      type = LayoutInspectorComposeProtocol.Parameter.Type.ITERABLE
      name = 1
      int32Value = 21
      index = 3
      Element {
        type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
        name = 2
        int32Value = 22
        index = 7
      }
      Element {
        type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
        name = 3
        int32Value = 23
        index = 10
      }
      Element {
        type = LayoutInspectorComposeProtocol.Parameter.Type.STRING
        name = 4
        int32Value = 24
        index = 11
      }
    }

  /**
   * Map of "view ID" to number of times properties were requested for it.
   *
   * This is useful for verifying that data is being cached to avoid subsequent fetches.
   */
  private val getPropertiesRequestCount = mutableMapOf<Long, Int>()

  /**
   * Map of "composable ID" to number of times parameters were requested for it.
   */
  private val getParametersRequestCount = mutableMapOf<Long, Int>()

  /**
   * Map of responses to expected [GetParameterDetailsCommand]s.
   */
  private val parameterDetailsCommands = mutableMapOf<GetParameterDetailsCommand, LayoutInspectorComposeProtocol.GetParameterDetailsResponse>()

  init {
    parameterDetailsCommands[
      GetParameterDetailsCommand.newBuilder().apply {
        rootViewId = 1L
        referenceBuilder.apply {
          generation = 2
          composableId = -5L
          kind = LayoutInspectorComposeProtocol.ParameterReference.Kind.NORMAL
          parameterIndex = 1
          addCompositeIndex(11)
        }
        maxElements = 5
      }.build()
    ] = LayoutInspectorComposeProtocol.GetParameterDetailsResponse.newBuilder().apply {
      rootViewId = 1L
      addAllStrings(expandedStrings)
      parameter = expandedParameter
    }.build()

    parameterDetailsCommands[
      GetParameterDetailsCommand.newBuilder().apply {
        rootViewId = 1L
        referenceBuilder.apply {
          generation = 2
          composableId = -5L
          kind = LayoutInspectorComposeProtocol.ParameterReference.Kind.NORMAL
          parameterIndex = 1
          addAllCompositeIndex(listOf(11, 3))
        }
        startIndex = 4
        maxElements = 2
      }.build()
    ] = LayoutInspectorComposeProtocol.GetParameterDetailsResponse.newBuilder().apply {
      rootViewId = 1L
      addAllStrings(firstExpandedListStrings)
      parameter = firstExpandedListParameter
    }.build()

    parameterDetailsCommands[
      GetParameterDetailsCommand.newBuilder().apply {
        generation = 2
        rootViewId = 1L
        referenceBuilder.apply {
          composableId = -5L
          kind = LayoutInspectorComposeProtocol.ParameterReference.Kind.NORMAL
          parameterIndex = 1
          addAllCompositeIndex(listOf(11, 3))
        }
        startIndex = 7
        maxElements = 4
      }.build()
    ] = LayoutInspectorComposeProtocol.GetParameterDetailsResponse.newBuilder().apply {
      rootViewId = 1L
      addAllStrings(secondExpandedListStrings)
      parameter = secondExpandedListParameter
    }.build()
  }

  fun createAllResponses() {
    createFakeViewTree()
    createFakeViewAttributes()

    createFakeComposeTree()
    createFakeComposeGetParameterResponse()
    createFakeComposeGetAllParameterResponse()
    createFakeComposeGetParameterDetailResponses()
  }

  fun createFakeViewTree() {
    viewInspector.interceptWhen({ it.hasStartFetchCommand() }) { command ->
      // Send all root IDs, which always happens before we send our first layout capture
      viewInspector.connection.sendEvent {
        rootsEventBuilder.apply {
          layoutTrees.forEach { tree -> addIds(tree.id) }
        }
      }

      layoutTrees.forEach { tree -> triggerLayoutCapture(rootId = tree.id, isLastCapture = !command.startFetchCommand.continuous) }

      LayoutInspectorViewProtocol.Response.newBuilder().setStartFetchResponse(LayoutInspectorViewProtocol.StartFetchResponse.getDefaultInstance()).build()
    }
  }

  fun createFakeViewAttributes() {
    viewInspector.interceptWhen({ it.hasGetPropertiesCommand() }) { command ->
      getPropertiesRequestCount.compute(command.getPropertiesCommand.viewId) { _, prev -> (prev ?: 0) + 1 }

      val propertyGroup = propertyGroups[command.getPropertiesCommand.rootViewId]!!
                            .firstOrNull { it.viewId == command.getPropertiesCommand.viewId }
                          // As this test data is hand defined, treat undefined view IDs as views with an empty properties group
                          ?: PropertyGroup {
                            viewId = command.getPropertiesCommand.viewId
                          }
      LayoutInspectorViewProtocol.Response.newBuilder().setGetPropertiesResponse(
        LayoutInspectorViewProtocol.GetPropertiesResponse.newBuilder().apply {
          addAllStrings(viewStrings)
          this.propertyGroup = propertyGroup
        }
      ).build()
    }
  }

  fun createFakeComposeTree(withSemantics: Boolean = true) {
    composeInspector.interceptWhen({ it.hasGetComposablesCommand() }) { command ->
      LayoutInspectorComposeProtocol.Response.newBuilder().apply {
        getComposablesResponseBuilder.apply {
          if (command.getComposablesCommand.rootViewId == layoutTrees[0].id) {
            addAllStrings(composeStrings)
            addRoots(if (withSemantics) composableRoot else composableRootWithoutSemantics)
          }
        }
      }.build()
    }
  }

  fun createFakeComposeGetParameterResponse() {
    composeInspector.interceptWhen({ it.hasGetParametersCommand() }) { command ->
      getParametersRequestCount.compute(command.getParametersCommand.composableId) { _, prev -> (prev ?: 0) + 1 }
      LayoutInspectorComposeProtocol.Response.newBuilder().apply {
        getParametersResponseBuilder.apply {
          parameterGroups.firstOrNull { it.composableId == command.getParametersCommand.composableId }?.let { group ->
            addAllStrings(composeStrings)
            parameterGroup = group
          }
        }
      }.build()
    }
  }

  fun createFakeComposeGetAllParameterResponse() {
    composeInspector.interceptWhen({ it.hasGetAllParametersCommand() }) { command ->
      LayoutInspectorComposeProtocol.Response.newBuilder().apply {
        getAllParametersResponseBuilder.apply {
          rootViewId = command.getAllParametersCommand.rootViewId
          if (command.getAllParametersCommand.rootViewId == layoutTrees[0].id) {
            addAllStrings(composeStrings)
            addAllParameterGroups(parameterGroups)
          }
        }
      }.build()
    }
  }

  fun createFakeComposeGetParameterDetailResponses() {
    composeInspector.interceptWhen({ it.hasGetParameterDetailsCommand() }) { command ->
      LayoutInspectorComposeProtocol.Response.newBuilder().apply {
        getParameterDetailsResponse = parameterDetailsCommands[command.getParameterDetailsCommand] ?: error("Unexpected command")
      }.build()
    }
  }

  fun getPropertiesRequestCountFor(viewId: Long): Int {
    return getPropertiesRequestCount[viewId] ?: 0
  }

  fun getParametersRequestCountFor(composableId: Long): Int {
    return getParametersRequestCount[composableId] ?: 0
  }

  /**
   * The real inspector triggers occasional captures as the UI changes, but for tests, we'll
   * expose this method so it can be triggered manually.
   */
  fun triggerLayoutCapture(rootId: Long, isLastCapture: Boolean = false) {
    val rootView = layoutTrees.first { it.id == rootId }
    viewInspector.connection.sendEvent {
      layoutEventBuilder.apply {
        addAllStrings(viewStrings)
        this.rootView = rootView
        appContextBuilder.apply {
          configurationBuilder.apply {
            density = Density.HIGH.dpiValue
            fontScale = 1.5f
          }
        }
      }
    }
    if (isLastCapture) {
      viewInspector.connection.sendEvent {
        propertiesEventBuilder.apply {
          this.rootId = rootId
          addAllStrings(viewStrings)
          addAllPropertyGroups(propertyGroups[rootId])
        }
      }
    }
  }
}
