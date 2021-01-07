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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.flags.junit.SetFlagRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.TestAppInspectorCommandHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.VIEW_LAYOUT_INSPECTOR_ID
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import kotlinx.coroutines.CoroutineScope
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol

/**
 * An [InspectorClientProvider] for creating an app inspection-based client.
 *
 * Note that some parameters are provided lazily to allow rules to initialize them first.
 */
class AppInspectionClientProvider(private val getApiServices: () -> AppInspectionApiServices,
                                  private val getScope: () -> CoroutineScope,
                                  private val inspectorId: String)
  : InspectorClientProvider {
  override fun create(params: InspectorClientLauncher.Params, model: InspectorModel): InspectorClient {
    val apiServices = getApiServices()
    val scope = getScope()

    return AppInspectionInspectorClient(params.process, model, apiServices, scope)
  }
}

/**
 * App inspection-pipeline specific setup and teardown for tests.
 */
class AppInspectionInspectorRule : TestRule {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  private val flagRule = SetFlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_USE_INSPECTION, true)
  private val grpcServer = FakeGrpcServer.createFakeGrpcServer("AppInspectionInspectorRuleServer", transportService)
  private val inspectionService = AppInspectionServiceRule(timer, transportService, grpcServer)

  /**
   * A fake handler which pretends to be acting like an inspector running on device normally would.
   *
   * Use this to intercept commands normally sent to the view inspector.
   */
  val viewInspectorHandler = FakeViewLayoutInspector()

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestAppInspectorCommandHandler(
      timer,
      rawInspectorResponse = { rawCommand ->
        val viewCommand = ViewProtocol.Command.parseFrom(rawCommand.content)
        val viewResponse = viewInspectorHandler.handleCommand(viewCommand)
        val rawResponse = AppInspection.RawResponse.newBuilder().setContent(viewResponse.toByteString())
        AppInspection.AppInspectionResponse.newBuilder().setRawResponse(rawResponse)
      })
    )
  }

  /**
   * Convenience method so users don't have to manually create an [AppInspectionClientProvider].
   */
  fun createInspectorClientProvider(inspectorId: String = VIEW_LAYOUT_INSPECTOR_ID): AppInspectionClientProvider {
    return AppInspectionClientProvider({ inspectionService.apiServices }, { inspectionService.scope }, inspectorId)
  }

  override fun apply(base: Statement, description: Description): Statement {
    // Rules will be applied in reverse order. This class will evaluate last.
    val innerRules = listOf(inspectionService, grpcServer, flagRule)
    return innerRules.fold(base) { stmt: Statement, rule: TestRule -> rule.apply(stmt, description) }
  }
}
