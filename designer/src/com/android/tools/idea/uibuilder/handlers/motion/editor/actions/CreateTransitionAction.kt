/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.actions

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyAttribute
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyCycle
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyPosition
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyTimeCycle
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyTrigger
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor

/**
 * Create Transition action.
 */
class CreateTransitionAction(private val motionEditor: MotionEditor) : OpenPopUpAction("Create KeyFrames", MEIcons.CREATE_KEYFRAME) {

  private val panels = listOf(CreateKeyPosition(), CreateKeyAttribute(), CreateKeyTrigger(), CreateKeyCycle(), CreateKeyTimeCycle())

  override val actions = panels.map { panel -> PanelAction(panel, motionEditor) }

}