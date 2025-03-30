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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.google.common.base.Function;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Looks up locations of the Gradle subprojects that are in the same parent project.
 */
public class GradleSiblingLookup implements Function<String, VirtualFile> {
  @NotNull private final VirtualFile myImportSource;
  @NotNull private final Project myDestination;
  private Map<String, VirtualFile> mySiblingsMap;

  public GradleSiblingLookup(@NotNull VirtualFile importSource, @NotNull Project destination) {
    myImportSource = importSource;
    myDestination = destination;
  }

  /**
   * Recursively go up the file system tree to find parent project with settings.gradle and then obtain collection of siblings from
   * that file.
   */
  private static Map<String, VirtualFile> findSiblings(@Nullable VirtualFile directory, Project project, Set<VirtualFile> seen) {
    if (directory == null) {
      return Collections.emptyMap();
    }
    else {
      if (seen.contains(directory)) {
        return findSiblings(null, project, seen);
      }
      seen.add(directory);
      VirtualFile settings = directory.findChild(SdkConstants.FN_SETTINGS_GRADLE);
      if (settings == null) {
        return findSiblings(directory.getParent(), project, seen);
      }
      else {
        return GradleModuleImporter.getSubProjects(settings, project);
      }
    }
  }

  public String getPrimaryProjectName() {
    String name = SdkConstants.GRADLE_PATH_SEPARATOR + myImportSource.getName();
    if (mySiblingsMap != null) { // We only do the scan if needed
      for (Map.Entry<String, VirtualFile> entry : mySiblingsMap.entrySet()) {
        if (myImportSource.equals(entry.getValue())) {
          name = entry.getKey();
          break;
        }
      }
    }
    return name;
  }

  @Override
  public VirtualFile apply(String input) {
    if (mySiblingsMap == null) {
      mySiblingsMap = findSiblings(myImportSource, myDestination, Sets.<VirtualFile>newHashSet());
    }
    return mySiblingsMap.get(input);
  }
}
