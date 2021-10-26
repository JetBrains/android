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
package com.android.tools.idea.common.scene

import kotlinx.coroutines.future.await

/**
 * Suspendable equivalent to [SceneManager.requestRenderAsync].
 */
suspend fun SceneManager.render() { requestRenderAsync().await() }

/**
 * Suspendable equivalent to [SceneManager.requestLayoutAndRenderAsync].
 */
suspend fun SceneManager.layoutAndRender(animate: Boolean) { requestLayoutAndRenderAsync(animate).await() }

/**
 * Suspendable equivalent to [SceneManager.requestLayoutAsync].
 */
suspend fun SceneManager.layout(animate: Boolean) { requestLayoutAsync(animate).await() }