// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.property.inspector

import org.jetbrains.android.dom.navigation.NavigationSchema

private val PROPERTY_NAME_UI_NAME_MAP = mapOf(
    NavigationSchema.ATTR_ACTION to "Action",
    NavigationSchema.ATTR_DATA to "Data",
    NavigationSchema.ATTR_DATA_PATTERN to "Pattern")

class NavActivityPropertiesInspectorProvider : NavigationPropertiesInspectorProvider(PROPERTY_NAME_UI_NAME_MAP, null)