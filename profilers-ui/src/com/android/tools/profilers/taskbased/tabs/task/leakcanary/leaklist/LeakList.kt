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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leaklist

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.android.tools.profilers.leakcanary.LeakCanaryModel

@Composable
fun LeakListView(leakCanaryModel: LeakCanaryModel, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    val leaks by leakCanaryModel.leaks.collectAsState()
    val isRecording by leakCanaryModel.isRecording.collectAsState()
    val selectedLeak by leakCanaryModel.selectedLeak.collectAsState()
    LeakListContent(leaks, selectedLeak, isRecording, leakCanaryModel::onLeakSelection)
  }
}