/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.draw.DrawCommandBase
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.scene.ConnectionDirection
import com.android.tools.idea.naveditor.scene.NavSceneManager
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

abstract class DrawActionBase : DrawCommandBase() {
}
