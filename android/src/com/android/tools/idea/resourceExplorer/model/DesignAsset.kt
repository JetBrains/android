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
package com.android.tools.idea.resourceExplorer.model

import com.android.ide.common.resources.configuration.ResourceQualifier
import com.intellij.openapi.vfs.VirtualFile

/**
 * A Design asset on disk.
 *
 * This class helps to interface a project's resource with a external file
 */
data class DesignAsset(
    val file: VirtualFile,
    val qualifiers: List<ResourceQualifier>
)

/**
 * Represents a set of design assets on disk grouped by base name.
 *
 * For example, fr/icon@2x.png, fr/icon.jpg  and en/icon.png will be
 * gatherd in the same DesignAssetSet under the name "icon"
 */
data class DesignAssetSet(
    val name: String,
    var designAssets: List<DesignAsset>
)