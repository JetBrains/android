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
package com.android.tools.idea.insights.ui.vcs

import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.Connection
import com.intellij.openapi.util.Key

/** Key of the app VCS info of the currently selected crash event from the "issues" view. */
val VCS_INFO_OF_SELECTED_CRASH = Key.create<AppVcsInfo>("vcsInfoOfSelectedCrash")

/** Key of the associated connection of the currently selected issue. */
val CONNECTION_OF_SELECTED_CRASH = Key.create<Connection>("connectionOfSelectedCrash")
