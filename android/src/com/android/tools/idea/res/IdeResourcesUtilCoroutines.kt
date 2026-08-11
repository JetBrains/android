/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.intellij.ide.actions.CreateElementActionBase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.android.dom.resources.ResourceElement
import org.jetbrains.android.dom.resources.Resources
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidUtils

private val LOG = Logger.getInstance("com.android.tools.idea.res.IdeResourcesUtilCoroutines")

/**
 * Suspending version of [createValueResource] that creates a new resource value.
 *
 * It creates missing files under a write action on the EDT, performs slow PSI and DOM element loading inside a background [readAction], and
 * applies PSI modifications under a [WriteCommandAction] on the EDT.
 */
suspend fun createValueResourceAsync(
  project: Project,
  resDir: VirtualFile,
  resourceName: String,
  resourceType: ResourceType,
  fileName: String,
  dirNames: List<String>,
  value: String,
): Boolean {
  return createValueResourceAsync(project, resDir, resourceName, value, resourceType, fileName, dirNames) { element ->
    if (value.isNotEmpty()) {
      val s = if (resourceType == ResourceType.STRING) normalizeXmlResourceValue(value) else value
      element.stringValue = s
    } else if (resourceType == ResourceType.STYLEABLE || resourceType == ResourceType.STYLE) {
      element.stringValue = "value"
      element.xmlTag?.value?.text = ""
    }
    true
  }
}

/** Suspending version of [createValueResource] with a custom [afterAddedProcessor] lambda. */
suspend fun createValueResourceAsync(
  project: Project,
  resDir: VirtualFile,
  resourceName: String,
  resourceValue: String?,
  resourceType: ResourceType,
  fileName: String,
  dirNames: List<String>,
  afterAddedProcessor: (ResourceElement) -> Boolean,
): Boolean {
  if (dirNames.isEmpty()) {
    return false
  }

  return try {
    // 1. Create missing resource files under WriteAction on EDT
    val resFiles =
      withContext(Dispatchers.EDT) {
        runWriteAction { dirNames.map { dirName -> findOrCreateResourceFile(project, resDir, fileName, dirName) } }
      }
    if (resFiles.any { it == null }) {
      return false
    }

    val nonNullResFiles = resFiles.filterNotNull().toTypedArray()
    val writable = withContext(Dispatchers.EDT) { ReadonlyStatusHandler.ensureFilesWritable(project, *nonNullResFiles) }
    if (!writable) {
      return false
    }

    // 2. Load DOM elements under a background ReadAction
    val resourcesElements = readAction { nonNullResFiles.map { file -> AndroidUtils.loadDomElement(project, file, Resources::class.java) } }

    if (resourcesElements.any { it == null }) {
      AndroidUtils.reportError(project, AndroidBundle.message("not.resource.file.error", fileName))
      return false
    }

    // 3. Apply PSI / XML modifications on EDT via writeCommandAction
    withContext(Dispatchers.EDT) {
      WriteCommandAction.writeCommandAction(project).withName("Add Resource").run<RuntimeException> {
        for (resources in resourcesElements) {
          if (resources != null) {
            if (resourceType == ResourceType.ATTR) {
              resources.addAttr().name.setValue(ResourceReference.attr(ResourceNamespace.TODO(), resourceName))
            } else {
              val element = addValueResource(resourceType, resources, resourceValue)
              element.name.value = resourceName
              afterAddedProcessor(element)
            }
          }
        }
      }
    }
    true
  } catch (e: Exception) {
    val message = CreateElementActionBase.filterMessage(e.message)
    if (message.isNullOrEmpty()) {
      LOG.error(e)
    } else {
      LOG.info(e)
      AndroidUtils.reportError(project, message)
    }
    false
  }
}
