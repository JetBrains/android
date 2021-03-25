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

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.Density
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeParametersCache
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterGroupItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ShowMoreElementsItem
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
import com.android.tools.idea.layoutinspector.properties.DimensionUnits
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.NAMESPACE_INTERNAL
import com.android.tools.idea.layoutinspector.properties.PropertiesSettings
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableGroupItem
import com.android.tools.property.ptable2.PTableGroupModification
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParameterDetailsCommand
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetParameterDetailsResponse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol as ComposeProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol

/** Timeout used in this test. While debugging, you may want extend the timeout */
private const val TIMEOUT = 1L
private val TIMEOUT_UNIT = TimeUnit.SECONDS
private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class AppInspectionPropertiesProviderTest {
  // Hand-crafted state loosely based on new basic activity app. Real data would look a lot more scattered.
  class FakeInspectorState(
    private val viewInspector: FakeViewLayoutInspector,
    composeInspector: FakeComposeLayoutInspector) {

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

    private val propertyGroups = mutableMapOf<Long, List<ViewProtocol.PropertyGroup>>().apply {
      this[layoutTrees[0].id] = listOf(
        PropertyGroup {
          viewId = 3
          Property {
            name = 101
            type = ViewProtocol.Property.Type.STRING
            int32Value = 201
          }
          Property {
            name = 102
            type = ViewProtocol.Property.Type.BOOLEAN
            int32Value = 1
          }
          Property {
            name = 106
            type = ViewProtocol.Property.Type.FLOAT
            floatValue = 1.0f
          }
        },
        PropertyGroup {
          viewId = 4
          Property {
            name = 108
            type = ViewProtocol.Property.Type.INT32
            int32Value = 200
          }
          Property {
            name = 111
            type = ViewProtocol.Property.Type.COLOR
            int32Value = -13172557
          }
          Property {
            name = 112
            type = ViewProtocol.Property.Type.GRAVITY
            flagValueBuilder.addAllFlag(listOf(202, 203))
          }
          Property {
            name = 113
            type = ViewProtocol.Property.Type.INT_ENUM
            int32Value = 220
          }
        },
        PropertyGroup {
          viewId = 5
          Property {
            name = 114
            type = ViewProtocol.Property.Type.INT_FLAG
            flagValueBuilder.addAllFlag(listOf(204, 205))
          }
          Property {
            name = 115
            type = ViewProtocol.Property.Type.RESOURCE
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
            type = ViewProtocol.Property.Type.DRAWABLE
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
            type = ViewProtocol.Property.Type.ANIMATOR
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

    private val parameterGroups = listOf(
      ParameterGroup {
        composableId = -2
        Parameter {
          type = ComposeProtocol.Parameter.Type.STRING
          name = 101
          int32Value = 201
        }
        Parameter {
          type = ComposeProtocol.Parameter.Type.BOOLEAN
          name = 102
          int32Value = 1
        }
      },
      ParameterGroup {
        composableId = -3
        Parameter {
          type = ComposeProtocol.Parameter.Type.INT32
          name = 105
          int32Value = 16
        }
        Parameter {
          type = ComposeProtocol.Parameter.Type.COLOR
          name = 107
          int32Value = -13172557
        }
      },
      ParameterGroup {
        composableId = -4
        Parameter {
          type = ComposeProtocol.Parameter.Type.DIMENSION_DP
          name = 109
          floatValue = 1f
        }
        Parameter {
          type = ComposeProtocol.Parameter.Type.DIMENSION_SP
          name = 110
          floatValue = 16f
        }
        Parameter {
          type = ComposeProtocol.Parameter.Type.DIMENSION_EM
          name = 111
          floatValue = 2f
        }
      },
      ParameterGroup {
        composableId = -5
        Parameter {
          type = ComposeProtocol.Parameter.Type.LAMBDA
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
          type = ComposeProtocol.Parameter.Type.STRING
          name = 114
          int32Value = 203
          Element {
            type = ComposeProtocol.Parameter.Type.STRING
            name = 116
            int32Value = 204
            index = 0
            Reference {
              composableId = -5
              parameterIndex = 1
            }
          }
          Element {
            type = ComposeProtocol.Parameter.Type.INT32
            name = 115
            int32Value = 812
            index = 1
          }
          Element {
            type = ComposeProtocol.Parameter.Type.STRING
            name = 117
            int32Value = 205
            index = 11
            Reference {
              composableId = -5
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
        type = ComposeProtocol.Parameter.Type.INT32
        name = 1
        int32Value = 21
        index = 11
        Element {
          type = ComposeProtocol.Parameter.Type.STRING
          name = 2
          int32Value = 22
          index = 0
        }
        Element {
          type = ComposeProtocol.Parameter.Type.STRING
          name = 3
          int32Value = 23
          index = 1
        }
        Element {
          type = ComposeProtocol.Parameter.Type.ITERABLE
          name = 4
          int32Value = 24
          index = 3
          Reference {
            composableId = -5
            parameterIndex = 1
            addAllCompositeIndex(listOf(11, 3))
          }
          Element {
            type = ComposeProtocol.Parameter.Type.STRING
            name = 5
            int32Value = 25
            index = 0
          }
          Element {
            type = ComposeProtocol.Parameter.Type.STRING
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
        type = ComposeProtocol.Parameter.Type.ITERABLE
        name = 1
        int32Value = 21
        index = 3
        Reference {
          composableId = -5
          parameterIndex = 1
          addAllCompositeIndex(listOf(11, 3))
        }
        Element {
          type = ComposeProtocol.Parameter.Type.STRING
          name = 2
          int32Value = 22
          index = 4
        }
        Element {
          type = ComposeProtocol.Parameter.Type.STRING
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
        type = ComposeProtocol.Parameter.Type.ITERABLE
        name = 1
        int32Value = 21
        index = 3
        Element {
          type = ComposeProtocol.Parameter.Type.STRING
          name = 2
          int32Value = 22
          index = 7
        }
        Element {
          type = ComposeProtocol.Parameter.Type.STRING
          name = 3
          int32Value = 23
          index = 10
        }
        Element {
          type = ComposeProtocol.Parameter.Type.STRING
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
    private val parameterDetailsCommands = mutableMapOf<GetParameterDetailsCommand, GetParameterDetailsResponse>()

    init {
      viewInspector.interceptWhen({ it.hasStartFetchCommand() }) { command ->
        // Send all root IDs, which always happens before we send our first layout capture
        viewInspector.connection.sendEvent {
          rootsEventBuilder.apply {
            layoutTrees.forEach { tree -> addIds(tree.id) }
          }
        }

        layoutTrees.forEach { tree -> triggerLayoutCapture(rootId = tree.id, isLastCapture = !command.startFetchCommand.continuous) }

        ViewProtocol.Response.newBuilder().setStartFetchResponse(ViewProtocol.StartFetchResponse.getDefaultInstance()).build()
      }

      viewInspector.interceptWhen({ it.hasGetPropertiesCommand() }) { command ->
        getPropertiesRequestCount.compute(command.getPropertiesCommand.viewId) { _, prev -> (prev ?: 0) + 1 }

        val propertyGroup = propertyGroups[command.getPropertiesCommand.rootViewId]!!
                              .firstOrNull { it.viewId == command.getPropertiesCommand.viewId }
                            // As this test data is hand defined, treat undefined view IDs as views with an empty properties group
                            ?: PropertyGroup {
                              viewId = command.getPropertiesCommand.viewId
                            }
        ViewProtocol.Response.newBuilder().setGetPropertiesResponse(
          ViewProtocol.GetPropertiesResponse.newBuilder().apply {
            addAllStrings(viewStrings)
            this.propertyGroup = propertyGroup
          }
        ).build()
      }

      composeInspector.interceptWhen({ it.hasGetComposablesCommand() }) { command ->
        ComposeProtocol.Response.newBuilder().apply {
          getComposablesResponseBuilder.apply {
            if (command.getComposablesCommand.rootViewId == layoutTrees[0].id) {
              addAllStrings(composeStrings)
              addRoots(composableRoot)
            }
          }
        }.build()
      }

      composeInspector.interceptWhen({ it.hasGetParametersCommand() }) { command ->
        getParametersRequestCount.compute(command.getParametersCommand.composableId) { _, prev -> (prev ?: 0) + 1 }
        ComposeProtocol.Response.newBuilder().apply {
          getParametersResponseBuilder.apply {
            parameterGroups.firstOrNull { it.composableId == command.getParametersCommand.composableId }?.let { group ->
              addAllStrings(composeStrings)
              parameterGroup = group
            }
          }
        }.build()
      }

      composeInspector.interceptWhen({ it.hasGetAllParametersCommand() }) { command ->
        ComposeProtocol.Response.newBuilder().apply {
          getAllParametersResponseBuilder.apply {
            rootViewId = command.getAllParametersCommand.rootViewId
            if (command.getAllParametersCommand.rootViewId == layoutTrees[0].id) {
              addAllStrings(composeStrings)
              addAllParameterGroups(parameterGroups)
            }
          }
        }.build()
      }

      composeInspector.interceptWhen({ it.hasGetParameterDetailsCommand() }) { command ->
        ComposeProtocol.Response.newBuilder().apply {
          getParameterDetailsResponse = parameterDetailsCommands[command.getParameterDetailsCommand] ?: error("Unexpected command")
        }.build()
      }

      parameterDetailsCommands[
        GetParameterDetailsCommand.newBuilder().apply {
          rootViewId = 1L
          referenceBuilder.apply {
            composableId = -5L
            parameterIndex = 1
            addCompositeIndex(11)
          }
          maxElements = 5
          skipSystemComposables = true
        }.build()
      ] = GetParameterDetailsResponse.newBuilder().apply {
        rootViewId = 1L
        addAllStrings(expandedStrings)
        parameter = expandedParameter
      }.build()

      parameterDetailsCommands[
        GetParameterDetailsCommand.newBuilder().apply {
          rootViewId = 1L
          referenceBuilder.apply {
            composableId = -5L
            parameterIndex = 1
            addAllCompositeIndex(listOf(11, 3))
          }
          startIndex = 4
          maxElements = 2
          skipSystemComposables = true
        }.build()
      ] = GetParameterDetailsResponse.newBuilder().apply {
        rootViewId = 1L
        addAllStrings(firstExpandedListStrings)
        parameter = firstExpandedListParameter
      }.build()

      parameterDetailsCommands[
        GetParameterDetailsCommand.newBuilder().apply {
          rootViewId = 1L
          referenceBuilder.apply {
            composableId = -5L
            parameterIndex = 1
            addAllCompositeIndex(listOf(11, 3))
          }
          startIndex = 7
          maxElements = 4
          skipSystemComposables = true
        }.build()
      ] = GetParameterDetailsResponse.newBuilder().apply {
        rootViewId = 1L
        addAllStrings(secondExpandedListStrings)
        parameter = secondExpandedListParameter
      }.build()
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

  private val projectRule = AndroidProjectRule.withSdk()
  private val inspectionRule = AppInspectionInspectorRule()
  private val inspectorRule = LayoutInspectorRule(inspectionRule.createInspectorClientProvider(), projectRule) {
    listOf(MODERN_PROCESS.name)
  }

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectionRule).around(inspectorRule)!!

  private lateinit var inspectorState: FakeInspectorState

  @Before
  fun setUp() {
    val propertiesComponent = PropertiesComponentMock()
    projectRule.replaceService(PropertiesComponent::class.java, propertiesComponent)
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS

    inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
  }

  @Test
  fun canQueryPropertiesForViews() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    inspectorRule.inspectorModel[3]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("text", PropertyType.STRING, "Next")
        assertProperty("clickable", PropertyType.BOOLEAN, "true")
        assertProperty("alpha", PropertyType.FLOAT, "1.0")
      }
    }

    inspectorRule.inspectorModel[4]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("minWidth", PropertyType.INT32, "200")
        assertProperty("background", PropertyType.COLOR, "#3700B3")
        assertProperty("gravity", PropertyType.GRAVITY, "top|start")
        assertProperty("orientation", PropertyType.INT_ENUM, "vertical")
      }
    }

    inspectorRule.inspectorModel[5]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("imeOptions", PropertyType.INT_FLAG, "normal|actionUnspecified")
        assertProperty("id", PropertyType.RESOURCE, "@com.example:id/fab")
        assertProperty("src", PropertyType.DRAWABLE, "@drawable/?")
        assertProperty("stateListAnimator", PropertyType.ANIMATOR, "@animator/?")
      }
    }
  }

  @Test
  fun syntheticPropertiesAlwaysAdded() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    inspectorRule.inspectorModel[1]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!

      // Technically the view with ID #1 has no properties, but synthetic properties are always added
      result.table.run {
        assertProperty("name", PropertyType.STRING, "androidx.constraintlayout.widget.ConstraintLayout",
                       PropertySection.VIEW, NAMESPACE_INTERNAL)
        assertProperty("x", PropertyType.DIMENSION, "0px", PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
        assertProperty("y", PropertyType.DIMENSION, "0px", PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
        assertProperty("width", PropertyType.DIMENSION, "0px", PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
        assertProperty("height", PropertyType.DIMENSION, "0px", PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
      }
    }
  }

  @Test
  fun propertiesAreCachedUntilNextLayoutEvent() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedSignal = ArrayBlockingQueue<Unit>(2) // We should get no more than two updates before continuing
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedSignal.offer(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!! // Event triggered by tree #1
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!! // Event triggered by tree #2

    val provider = inspectorRule.inspectorClient.provider

    // Get properties for views from the two different layout trees so we can verify that the cache of each
    // layout tree is maintained separately.
    val nodeInTree1 = inspectorRule.inspectorModel[3]!!
    val nodeInTree2 = inspectorRule.inspectorModel[101]!!

    // Tree #1
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(0)

    provider.requestProperties(nodeInTree1).get() // First fetch, not cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(1)

    provider.requestProperties(nodeInTree1).get() // Should be cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(1)

    provider.requestProperties(nodeInTree1).get() // Still cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(1)

    // Tree #2
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree2.drawId)).isEqualTo(0)

    provider.requestProperties(nodeInTree2).get() // Not cached yet
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree2.drawId)).isEqualTo(1)

    provider.requestProperties(nodeInTree2).get() // Cached now
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree2.drawId)).isEqualTo(1)

    // Trigger a fake layout update in *just* the first tree, which should reset just its cache and
    // not that for the second tree
    inspectorState.triggerLayoutCapture(rootId = 1)
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!!

    provider.requestProperties(nodeInTree1).get() // First fetch after layout event, not cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(2)

    provider.requestProperties(nodeInTree1).get() // Tree #1 node should be cached again
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(2)

    provider.requestProperties(nodeInTree2).get() // Tree #2 node should have remained cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree2.drawId)).isEqualTo(1)
  }

  @Test
  fun snapshotModeSendsAllPropertiesAtOnce() {
    InspectorClientSettings.isCapturingModeOn = false // i.e. snapshot mode

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // Calling "get properties" at this point should work without talking to the device because everything should
    // be cached now.

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    for (id in listOf(3L, 4L, 5L)) {
      assertThat(inspectorState.getPropertiesRequestCountFor(id)).isEqualTo(0)
      inspectorRule.inspectorModel[id]!!.let { targetNode ->
        provider.requestProperties(targetNode).get()
        val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
        assertThat(result.view).isSameAs(targetNode)
        assertThat(inspectorState.getPropertiesRequestCountFor(id)).isEqualTo(0)
      }
    }
  }

  @Test
  fun canQueryParametersForComposables() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    inspectorRule.inspectorModel[-2]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("text", PropertyType.STRING, "placeholder")
        assertProperty("clickable", PropertyType.BOOLEAN, "true")
      }
    }

    inspectorRule.inspectorModel[-3]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("maxLines", PropertyType.INT32, "16")
        assertProperty("color", PropertyType.COLOR, "#3700B3")
      }
    }

    inspectorRule.inspectorModel[-4]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
        assertProperty("elevation", PropertyType.DIMENSION_DP, "1.5px")
        assertProperty("fontSize", PropertyType.DIMENSION_SP, "36.0px")
        assertProperty("textSize", PropertyType.DIMENSION_EM, "2.0em")
      }
      result.table.run {
        PropertiesSettings.dimensionUnits = DimensionUnits.DP
        assertProperty("elevation", PropertyType.DIMENSION_DP, "1.0dp")
        assertProperty("fontSize", PropertyType.DIMENSION_SP, "16.0sp")
        assertProperty("textSize", PropertyType.DIMENSION_EM, "2.0em")
      }
    }

    val parameter = inspectorRule.inspectorModel[-5]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)

      result.table.run {
        assertProperty("onTextLayout", PropertyType.LAMBDA, "λ")
        assertProperty("dataObject", PropertyType.STRING, "PojoClass")
        val groupItem = this[ANDROID_URI, "dataObject"] as ParameterGroupItem
        assertProperty(groupItem.children[0], "stringProperty", PropertyType.STRING, "stringValue")
        assertProperty(groupItem.children[1], "intProperty", PropertyType.INT32, "812")
        assertProperty(groupItem.children[2], "lines", PropertyType.STRING, "MyLineClass")
        assertThat(groupItem.children.size).isEqualTo(3)

        groupItem
      }
    }

    // The 1st element of parameter is a reference to parameter itself.
    // When the 1st sub element is expanded in the UI the child elements should be copied from parameter.
    val propertyExpandedLatch = ReportingCountDownLatch(1)
    propertyExpandedLatch.runInEdt {
      val first = parameter.children.first() as ParameterGroupItem
      assertThat(first.children).isEmpty()
      first.expandWhenPossible { restructured ->
        assertThat(restructured).isTrue()
        assertProperty(first.children[0], "stringProperty", PropertyType.STRING, "stringValue")
        assertProperty(first.children[1], "intProperty", PropertyType.INT32, "812")
        assertProperty(first.children[2], "lines", PropertyType.STRING, "MyLineClass")
        assertThat(first.children.size).isEqualTo(3)
        propertyExpandedLatch.countDown()
      }
    }
    propertyExpandedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // The last element of parameter is a reference to a value that has not been loaded from the agent yet.
    // When the last element is expanded in the UI the child elements will be loaded from the agent.
    val propertyDownloadedLatch = ReportingCountDownLatch(1)
    val last = parameter.children.last() as ParameterGroupItem
    propertyDownloadedLatch.runInEdt {
      assertThat(last.children).isEmpty()
      last.expandWhenPossible { restructured ->
        assertThat(restructured).isTrue()
        assertProperty(last.children[0], "firstLine", PropertyType.STRING, "Hello World")
        assertProperty(last.children[1], "lastLine", PropertyType.STRING, "End of Text")
        assertProperty(last.children[2], "list", PropertyType.ITERABLE, "List[12]")
        assertThat(last.children.size).isEqualTo(3)
        val list = last.children.last() as ParameterGroupItem
        assertProperty(list.children[0], "[0]", PropertyType.STRING, "a")
        assertProperty(list.children[1], "[3]", PropertyType.STRING, "b")
        assertThat(list.children[2]).isInstanceOf(ShowMoreElementsItem::class.java)
        assertThat(list.children[2].index).isEqualTo(4)
        assertThat(list.children.size).isEqualTo(3)
        propertyDownloadedLatch.countDown()
      }
    }
    propertyDownloadedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // The list parameter from the expanded parameter is a List of String where only a part of the elements
    // have been downloaded. Download some more elements (first time).
    val moreListElements1 = ReportingCountDownLatch(1)
    val table1 = spy(PTable.create(mock()))
    val event1: AnActionEvent = mock()
    doAnswer {
      val modification = it.getArgument<PTableGroupModification>(1)
      assertThat(modification.added).hasSize(2)
      assertThat(modification.removed).isEmpty()
      moreListElements1.countDown()
    }.`when`(table1).updateGroupItems(any(PTableGroupItem::class.java), any(PTableGroupModification::class.java))
    doAnswer {
      table1.component
    }.`when`(event1).getData(Mockito.eq(PlatformDataKeys.CONTEXT_COMPONENT))
    val list = last.children.last() as ParameterGroupItem
    moreListElements1.runInEdt {
      val showMoreItem = list.children[2] as ShowMoreElementsItem
      // Click the "Show more" link:
      showMoreItem.link.actionPerformed(event1)
    }
    moreListElements1.await(TIMEOUT, TIMEOUT_UNIT)
    assertProperty(list, "list", PropertyType.ITERABLE, "List[12]")
    assertThat(list.reference).isNotNull()
    assertProperty(list.children[0], "[0]", PropertyType.STRING, "a")
    assertProperty(list.children[1], "[3]", PropertyType.STRING, "b")
    assertProperty(list.children[2], "[4]", PropertyType.STRING, "c")
    assertProperty(list.children[3], "[6]", PropertyType.STRING, "d")
    assertThat(list.children[4]).isInstanceOf(ShowMoreElementsItem::class.java)
    assertThat(list.children[4].index).isEqualTo(7)
    assertThat(list.children.size).isEqualTo(5)

    // Expand the list a second time:
    val moreListElements2 = ReportingCountDownLatch(1)
    val table2 = spy(PTable.create(mock()))
    val event2: AnActionEvent = mock()
    doAnswer {
      val modification = it.getArgument<PTableGroupModification>(1)
      assertThat(modification.added).hasSize(3)
      assertThat(modification.removed).hasSize(1)
      moreListElements2.countDown()
    }.`when`(table2).updateGroupItems(any(PTableGroupItem::class.java), any(PTableGroupModification::class.java))
    doAnswer {
      table2.component
    }.`when`(event2).getData(Mockito.eq(PlatformDataKeys.CONTEXT_COMPONENT))
    moreListElements2.runInEdt {
      val showMoreItem = list.children[4] as ShowMoreElementsItem
      // Click the "Show more" link:
      showMoreItem.link.actionPerformed(event2)
    }
    moreListElements2.await(TIMEOUT, TIMEOUT_UNIT)
    assertProperty(list, "list", PropertyType.ITERABLE, "List[12]")
    assertThat(list.reference).isNull()
    assertProperty(list.children[0], "[0]", PropertyType.STRING, "a")
    assertProperty(list.children[1], "[3]", PropertyType.STRING, "b")
    assertProperty(list.children[2], "[4]", PropertyType.STRING, "c")
    assertProperty(list.children[3], "[6]", PropertyType.STRING, "d")
    assertProperty(list.children[4], "[7]", PropertyType.STRING, "e")
    assertProperty(list.children[5], "[10]", PropertyType.STRING, "f")
    assertProperty(list.children[6], "[11]", PropertyType.STRING, "g")
    assertThat(list.children.size).isEqualTo(7)
  }

  @Test
  fun parametersAreCachedUntilNextLayoutEvent() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedSignal = ArrayBlockingQueue<Unit>(2) // We should get no more than two updates before continuing
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedSignal.offer(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!! // Event triggered by tree #1
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!! // Event triggered by tree #2

    val provider = inspectorRule.inspectorClient.provider

    val composableNode = inspectorRule.inspectorModel[-2]!!
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(0)

    provider.requestProperties(composableNode).get() // First fetch, not cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(1)

    provider.requestProperties(composableNode).get()  // Should be cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(1)

    provider.requestProperties(composableNode).get()  // Still cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(1)

    // Trigger a fake layout update in *just* the first tree, which should reset just its cache and
    // not that for the second tree
    inspectorState.triggerLayoutCapture(rootId = 1)
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!!

    provider.requestProperties(composableNode).get() // First fetch after layout event, not cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(2)

    provider.requestProperties(composableNode).get()  // Should be cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(2)

    // Trigger a fake layout update in *just* the second tree, which should not affect the cache of the
    // first
    inspectorState.triggerLayoutCapture(rootId = 101)
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!!

    provider.requestProperties(composableNode).get()  // Should still be cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(2)
  }

  @Test
  fun snapshotModeSendsAllParametersAtOnce() {
    InspectorClientSettings.isCapturingModeOn = false // i.e. snapshot mode

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // Calling "get properties" at this point should work without talking to the device because everything should
    // be cached now.

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    for (id in listOf(-2L, -3L, -4L)) {
      assertThat(inspectorState.getParametersRequestCountFor(id)).isEqualTo(0)
      inspectorRule.inspectorModel[id]!!.let { targetNode ->
        provider.requestProperties(targetNode).get()
        val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
        assertThat(result.view).isSameAs(targetNode)
        assertThat(inspectorState.getPropertiesRequestCountFor(id)).isEqualTo(0)
      }
    }
  }

  private fun PropertiesTable<InspectorPropertyItem>.assertProperty(
    name: String,
    type: PropertyType,
    value: String,
    group: PropertySection = PropertySection.DEFAULT,
    namespace: String = ANDROID_URI,
  ) = assertProperty(this[namespace, name], name, type, value, group, namespace)


  private fun assertProperty(
    property: InspectorPropertyItem, name: String,
    type: PropertyType,
    value: String,
    group: PropertySection = PropertySection.DEFAULT,
    namespace: String = ANDROID_URI,
  ) {
    assertThat(property.name).isEqualTo(name)
    assertThat(property.attrName).isEqualTo(name)
    assertThat(property.namespace).isEqualTo(namespace)
    assertThat(property.type).isEqualTo(type)
    assertThat(property.value).isEqualTo(value)
    assertThat(property.group).isEqualTo(group)
  }

  /**
   * Helper class to receive a properties provider result.
   */
  private class ProviderResult(
    val view: ViewNode,
    val table: PropertiesTable<InspectorPropertyItem>,
    val model: InspectorModel,
    val cache: ComposeParametersCache?
  ) {

    init {
      table.values.forEach { property ->
        assertThat(property.viewId).isEqualTo(view.drawId)
        if (property is ParameterItem) {
          assertThat(property.lookup).isSameAs(cache!!)
        } else {
          assertThat(property.lookup).isSameAs(model)
        }
      }
    }
  }
}
