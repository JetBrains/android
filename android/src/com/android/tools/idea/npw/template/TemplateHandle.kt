/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.templates.TemplateMetadata
import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import java.io.File

/**
 * A handle to various relevant information related to a target template.xml file.
 */
class TemplateHandle(val rootPath: File) {
  val template: Template = Template.createFromPath(rootPath)
  val metadata: TemplateMetadata = TemplateManager.getInstance().getTemplateMetadata(rootPath)!!

  override fun hashCode() = Objects.hashCode(rootPath, metadata.title)

  override fun equals(other: Any?) = when {
      other == null || other::class != this::class -> false
      other === this -> true
      else -> {
        val another = other as TemplateHandle
        Objects.equal(rootPath, another.rootPath) && Objects.equal(metadata.title, another.metadata.title)
      }
    }

  override fun toString() =
    MoreObjects.toStringHelper(this).add("title", metadata.title).add("path", rootPath.absolutePath).toString()
}