/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.insights.ai

import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.IssueId
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import org.jetbrains.annotations.TestOnly

data class GeminiCrashInsightRequest(
  val connection: Connection,
  val issueId: IssueId,
  val variantId: String?,
  val deviceName: String,
  val apiLevel: String,
  val event: Event,
)

interface AiInsightContributor {
  suspend fun fetchInsight(request: GeminiCrashInsightRequest, project: Project, codeContextResolver: CodeContextResolver): AiInsight

  fun canContribute(): Boolean

  companion object {
    val EP_NAME = ExtensionPointName<AiInsightContributor>("com.android.tools.idea.insights.ai.aiInsightContributor")

    fun getFirstAvailableContributor() = EP_NAME.extensions.firstOrNull { it.canContribute() }
  }
}

// Stub AI insight contributor to be used for E2E testing.
@TestOnly
class StubAiInsightContributor : AiInsightContributor {
  override fun canContribute() = java.lang.Boolean.getBoolean("appinsights.generate.fake.insight")

  override suspend fun fetchInsight(
    request: GeminiCrashInsightRequest,
    project: Project,
    codeContextResolver: CodeContextResolver,
  ): AiInsight {
    delay(2000)
    return AiInsight(rawInsight = "Fake insight for testing purposes.", event = request.event, insightSource = InsightSource.STUDIO_BOT)
  }
}

const val FILE_PHRASE = "The fix should likely be in "

fun createGeminiInsightRequest(connection: Connection, issueId: IssueId, variantId: String?, event: Event) =
  GeminiCrashInsightRequest(
    connection = connection,
    issueId = issueId,
    variantId = variantId,
    deviceName = event.eventData.device.let { "${it.manufacturer} ${it.model}" },
    apiLevel = event.eventData.operatingSystemInfo.displayVersion,
    event = event,
  )
