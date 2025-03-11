/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

/** The class that used to serialize workspace model cache. Keep it to avoid exception in logs. */
@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadata() {
    var typeMetadata: StorageTypeMetadata
    typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.google.idea.blaze.base.qsync.entity.BazelEntitySource",
                                                     properties = listOf(
                                                       OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                           name = "virtualFileUrl",
                                                                           valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                             isNullable = true,
                                                                             typeMetadata = FinalClassMetadata.KnownClass(
                                                                               fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                           withDefault = false)),
                                                     supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))
    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = -203650444)
    addMetadataHash(typeFqn = "com.google.idea.blaze.base.qsync.entity.BazelEntitySource", metadataHash = 550709046)
  }
}
