/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.sync.change;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class DefaultProjectStructureEntityDescriptionBuilder {

  /**
   * Tries to build human-readable description for the given project structure entity node assuming that it's one
   * of the {@link ProjectKeys standard types}.
   *
   * @param dataNode  project structure entity node to build description for
   * @return          human-readable description of the given project structure entity node
   * @throws IllegalArgumentException   if it's not possible to build a human-readable description for the given node
   */
  @NotNull
  public static String build(@NotNull DataNode<?> dataNode) throws IllegalArgumentException {
    Key<?> key = dataNode.getKey();
    if (ProjectKeys.PROJECT.equals(key)) {
      return AndroidBundle.message("android.gradle.project.entity.project", ((ProjectData)dataNode.getData()).getInternalName());
    }
    else if (ProjectKeys.MODULE.equals(key)) {
      return AndroidBundle.message("android.gradle.project.entity.module", ((ModuleData)dataNode.getData()).getInternalName());
    }
    else if (ProjectKeys.MODULE_DEPENDENCY.equals(key)) {
      ModuleDependencyData dependency = (ModuleDependencyData)dataNode.getData();
      return AndroidBundle.message("android.gradle.project.entity.dependency.module",
                                   dependency.getOwnerModule().getInternalName(), dependency.getTarget().getInternalName());
    }
    else if (ProjectKeys.LIBRARY_DEPENDENCY.equals(key)) {
      LibraryDependencyData dependency = (LibraryDependencyData)dataNode.getData();
      return AndroidBundle.message("android.gradle.project.entity.dependency.library",
                                   dependency.getOwnerModule().getInternalName(), dependency.getTarget().getInternalName());
    }
    else if (ProjectKeys.CONTENT_ROOT.equals(key)) {
      String rootPath = ((ContentRootData)dataNode.getData()).getRootPath();
      if ((rootPath.endsWith("/") || rootPath.endsWith("\"))")) && rootPath.length() > 0) {
        rootPath = rootPath.substring(0, rootPath.length() - 1);
      }
      int i = Math.max(rootPath.lastIndexOf('/'), rootPath.lastIndexOf('\\'));
      if (i > 0) {
        rootPath = rootPath.substring(i + 1);
      }
      DataNode<ModuleData> moduleNode = dataNode.getDataNode(ProjectKeys.MODULE);
      if (moduleNode == null) {
        throw new IllegalArgumentException(String.format("Can't build a description for content root '%s'. Reason: target node is unknown",
                                                         rootPath));
      }
      return AndroidBundle.message("android.gradle.project.entity.content.root", rootPath, moduleNode.getData().getInternalName());
    }
    else {
      throw new IllegalArgumentException(String.format("Can't build a description for project structure entity of type %s (%s)",
                                         dataNode.getKey(), dataNode.getData()));
    }
  }
}
