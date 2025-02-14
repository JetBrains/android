/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene.decorator

import com.android.AndroidXConstants
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutDecorator
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory.Provider
import java.lang.reflect.Constructor

/**
 * [Provider] implementation for `MotionLayout` related tags.
 */
class NlSceneDecoratorProviderImpl : Provider {
  override fun provide(): Map<String, Constructor<out SceneDecorator>> = mapOf(
    AndroidXConstants.CLASS_MOTION_LAYOUT.oldName() to MotionLayoutDecorator::class.java.getConstructor(),
    AndroidXConstants.CLASS_MOTION_LAYOUT.newName() to MotionLayoutDecorator::class.java.getConstructor()
  )
}