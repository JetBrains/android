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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.util.messages.Topic
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.idea.util.toKaModulesForModificationEvents

internal fun <T : Any> Module.fireEvent(topic: Topic<T>, callEventHandler: T.(KtModule) -> Unit) =
  runWriteAction {
    val publisher = project.analysisMessageBus.syncPublisher(topic)
    this@fireEvent.toKaModulesForModificationEvents().forEach { publisher.callEventHandler(it) }
  }
