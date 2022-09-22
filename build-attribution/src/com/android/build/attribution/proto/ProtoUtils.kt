/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution.proto

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.data.PluginData
import com.android.utils.HelpfulEnumConverter
import java.lang.Exception

fun transformPluginData(pluginData: PluginData): BuildAnalysisResultsMessage.PluginData =
  BuildAnalysisResultsMessage.PluginData.newBuilder()
    .setPluginType(transformPluginType(pluginData.pluginType))
    .setIdName(pluginData.idName)
    .build()

fun transformPluginType(pluginType: PluginData.PluginType) =
  when (pluginType) {
    PluginData.PluginType.UNKNOWN -> BuildAnalysisResultsMessage.PluginData.PluginType.UNKNOWN
    PluginData.PluginType.SCRIPT -> BuildAnalysisResultsMessage.PluginData.PluginType.SCRIPT
    PluginData.PluginType.BUILDSRC_PLUGIN -> BuildAnalysisResultsMessage.PluginData.PluginType.BUILDSRC_PLUGIN
    PluginData.PluginType.BINARY_PLUGIN -> BuildAnalysisResultsMessage.PluginData.PluginType.BINARY_PLUGIN
  }

fun constructPluginType(type: BuildAnalysisResultsMessage.PluginData.PluginType) =
  when (type) {
    BuildAnalysisResultsMessage.PluginData.PluginType.BINARY_PLUGIN -> PluginData.PluginType.BINARY_PLUGIN
    BuildAnalysisResultsMessage.PluginData.PluginType.BUILDSRC_PLUGIN -> PluginData.PluginType.BUILDSRC_PLUGIN
    BuildAnalysisResultsMessage.PluginData.PluginType.SCRIPT -> PluginData.PluginType.SCRIPT
    BuildAnalysisResultsMessage.PluginData.PluginType.UNKNOWN -> PluginData.PluginType.UNKNOWN
    BuildAnalysisResultsMessage.PluginData.PluginType.UNRECOGNIZED -> throw IllegalStateException("Unrecognized plugin type")
  }