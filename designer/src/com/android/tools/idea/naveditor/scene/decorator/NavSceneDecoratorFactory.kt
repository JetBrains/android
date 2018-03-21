/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.lang.reflect.Constructor
import java.util.*

/**
 * Creates [SceneComponent]s from [NlComponent]s for the navigation editor.
 */
private val ourConstructorMap = HashMap<String, Constructor<out SceneDecorator>>()

class NavSceneDecoratorFactory(schema: NavigationSchema) : SceneDecoratorFactory() {

  init {
    for ((key, value) in schema.tagTypeMap) {
      val decoratorClass: Class<out SceneDecorator> = when (value) {
        NavigationSchema.DestinationType.NAVIGATION -> NavigationDecorator::class.java
        NavigationSchema.DestinationType.ACTIVITY -> ActivityDecorator::class.java
        NavigationSchema.DestinationType.FRAGMENT -> FragmentDecorator::class.java
        else -> throw IllegalStateException()
      }

      ourConstructorMap.put(key, decoratorClass.getConstructor())
      ourConstructorMap.put(NavigationSchema.TAG_ACTION, ActionDecorator::class.java.getConstructor())
    }
  }

  override fun get(component: NlComponent): SceneDecorator {
    return get(component.tagName).orElse(SceneDecoratorFactory.BASIC_DECORATOR)
  }

  override fun getConstructorMap(): Map<String, Constructor<out SceneDecorator>> {
    return ourConstructorMap
  }
}