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

package com.android.tools.res.apk

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType

/** [ResourceItem] of the [ApkResourceRepository]. */
internal class ApkResourceItem(
  private val resRef: ResourceReference,
  private val folderConfig: FolderConfiguration,
  private val apkResValue: ResourceValue
) : ResourceItem {
  private val fileBased: Boolean = apkResValue.value?.let { isResourceFileReference(it, resRef.resourceType) } ?: false
  override fun getConfiguration(): FolderConfiguration = folderConfig

  override fun getName(): String = resRef.name

  override fun getType(): ResourceType = resRef.resourceType

  override fun getNamespace(): ResourceNamespace = resRef.namespace

  override fun getLibraryName(): String? = null

  override fun getResourceValue(): ResourceValue? = apkResValue

  override fun getSource(): PathString? = null

  override fun isFileBased(): Boolean  = fileBased
}

private const val RES_FOLDER_SLASH = "${SdkConstants.RES_FOLDER}/"
/** Heuristic to determine whether the resource value is resource file reference. */
internal fun isResourceFileReference(resValue: String, resType: ResourceType): Boolean =
  resType != ResourceType.STRING && resValue.startsWith(RES_FOLDER_SLASH)