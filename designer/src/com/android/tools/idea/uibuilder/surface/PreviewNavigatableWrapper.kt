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
package com.android.tools.idea.uibuilder.surface

import com.intellij.pom.Navigatable

/**
 * The name property is used whenever we want to display a navigatable to a user.
 *
 * We will need to display the name when a user wants to click a background element and thereby
 * presses shift + click to open a popup to choose between background elements.
 */
data class PreviewNavigatableWrapper(val name: String, val navigatable: Navigatable)
