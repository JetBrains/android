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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.Pannable
import com.intellij.openapi.actionSystem.DataProvider

/**
 * This defines an interface of the [LayoutlibInteractionHandler] downstream client.
 *
 * The client is supposed to work with panning interaction, basic mouse and keyboard events and
 * basic zooming. It is also supposed to be a [ScenesOwner] so that interactions can be passed
 * directly to the [Scene]s. [DataProvider] is only required to support [Pannable] delegating.
 *
 * TODO(b/228294269): Consider expanding this to generic [InteractionHandler] use. This interface
 *   should not extend [DataProvider]. This is only done so because of [Pannable] can be obtained
 *   with [PANNABLE_KEY].
 */
interface InteractableScenesSurface :
  Pannable, DataProvider, ScenesOwner, HoverableSurface, ZoomControllableSurface
