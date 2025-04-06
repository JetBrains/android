/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.tree.GotoDeclarationAction
import kotlinx.coroutines.CoroutineScope

/** Navigate the editor to the selected node in the view model, issued from the renderer */
fun navigateToSelectedViewFromRendererDoubleClick(
  scope: CoroutineScope,
  inspectorModel: InspectorModel,
  client: InspectorClient,
  notificationModel: NotificationModel,
) {
  GotoDeclarationAction.navigateToSelectedView(scope, inspectorModel, client, notificationModel)
  client.stats.gotoSourceFromRenderDoubleClick()
}
