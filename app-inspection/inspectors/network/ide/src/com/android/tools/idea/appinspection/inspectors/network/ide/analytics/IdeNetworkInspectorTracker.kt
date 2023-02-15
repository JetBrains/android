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
package com.android.tools.idea.appinspection.inspectors.network.ide.analytics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.NetworkInspectorEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.NetworkInspectorEvent.RuleUpdatedEvent
import com.intellij.openapi.project.Project

class IdeNetworkInspectorTracker(private val project: Project) : NetworkInspectorTracker {
  override fun trackMigrationDialogSelected() {
    track(
      NetworkInspectorEvent.newBuilder().apply {
        type = NetworkInspectorEvent.Type.MIGRATION_LINK_SELECTED
      }
    )
  }

  override fun trackConnectionDetailsSelected() {
    track(
      NetworkInspectorEvent.newBuilder().apply {
        type = NetworkInspectorEvent.Type.CONNECTION_DETAIL_SELECTED
      }
    )
  }

  override fun trackRequestTabSelected() {
    track(
      NetworkInspectorEvent.newBuilder().apply {
        type = NetworkInspectorEvent.Type.REQUEST_TAB_SELECTED
      }
    )
  }

  override fun trackResponseTabSelected() {
    track(
      NetworkInspectorEvent.newBuilder().apply {
        type = NetworkInspectorEvent.Type.RESPONSE_TAB_SELECTED
      }
    )
  }

  override fun trackCallstackTabSelected() {
    track(
      NetworkInspectorEvent.newBuilder().apply {
        type = NetworkInspectorEvent.Type.CALLSTACK_TAB_SELECTED
      }
    )
  }

  override fun trackRuleCreated() {
    track(
      NetworkInspectorEvent.newBuilder().apply { type = NetworkInspectorEvent.Type.RULE_CREATED }
    )
  }

  override fun trackRuleUpdated(component: NetworkInspectorTracker.InterceptionCriteria) {
    track(
      NetworkInspectorEvent.newBuilder().apply {
        type = NetworkInspectorEvent.Type.RULE_UPDATED
        ruleDetailUpdatedBuilder.apply {
          this.component =
            RuleUpdatedEvent.Component.values().firstOrNull { it.name == component.name }
        }
      }
    )
  }

  override fun trackResponseIntercepted(
    statusCode: Boolean,
    headerAdded: Boolean,
    headerReplaced: Boolean,
    bodyReplaced: Boolean,
    bodyModified: Boolean
  ) {
    track(
      NetworkInspectorEvent.newBuilder().apply {
        type = NetworkInspectorEvent.Type.RESPONSE_INTERCEPTED
        responseInterceptedBuilder.apply {
          this.statusCode = statusCode
          this.headerAdded = headerAdded
          this.bodyReplaced = bodyReplaced
          this.bodyModified = bodyModified
        }
      }
    )
  }

  private fun track(networkEvent: NetworkInspectorEvent.Builder) {
    val inspectionEvent =
      AppInspectionEvent.newBuilder()
        .setType(AppInspectionEvent.Type.INSPECTOR_EVENT)
        .setNetworkInspectorEvent(networkEvent)

    val studioEvent =
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.APP_INSPECTION)
        .setAppInspectionEvent(inspectionEvent)

    // TODO(b/153270761): Use studioEvent.withProjectId instead, after code is moved out of
    //  monolithic core module
    studioEvent.projectId = AnonymizerUtil.anonymizeUtf8(project.basePath!!)
    UsageTracker.log(studioEvent)
  }
}
