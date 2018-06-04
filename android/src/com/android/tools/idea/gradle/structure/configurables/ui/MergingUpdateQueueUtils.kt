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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.intellij.openapi.Disposable
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import javax.swing.JComponent

/**
 * Creates a pre-configured [MergingUpdateQueue] for [parent] with the given [name] and the [activationComponent].
 */
fun createMergingUpdateQueue(name: String, parent: Disposable, activationComponent: JComponent? = null): MergingUpdateQueue =
  MergingUpdateQueue(name, 300, true, MergingUpdateQueue.ANY_COMPONENT, parent, activationComponent).apply {
    setRestartTimerOnAdd(true)
  }

/**
 * Enqueues an update which executes the [body] when run and has the identity of [tag].
 */
fun MergingUpdateQueue.enqueueTagged(tag: Any, body: () -> Unit) {
  queue(object : Update(tag, false) {
    override fun run() = body()
  })
}