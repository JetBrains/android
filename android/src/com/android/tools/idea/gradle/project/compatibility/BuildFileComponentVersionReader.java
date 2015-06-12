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
package com.android.tools.idea.gradle.project.compatibility;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.google.common.base.Splitter;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Obtains the version for a component from a build.gradle file, given the component name (e.g. "buildToolsVersion".)
 */
class BuildFileComponentVersionReader implements ComponentVersionReader {
  @NotNull private final String myComponentName;

  @Nullable private final BuildFileKey myKey;

  BuildFileComponentVersionReader(@NotNull String keyPath) {
    List<String> segments = Splitter.on('/').splitToList(keyPath);
    myComponentName = segments.get(segments.size() - 1);
    myKey = BuildFileKey.findByPath(keyPath);
  }

  @Override
  public boolean appliesTo(@NotNull Module module) {
    return AndroidGradleFacet.getInstance(module) != null && GradleBuildFile.get(module) != null;
  }

  @Override
  @Nullable
  public String getComponentVersion(@NotNull Module module) {
    GradleBuildFile buildFile = GradleBuildFile.get(module);
    if (buildFile != null && myKey != null) {
      Object value = buildFile.getValue(myKey);
      if (value != null) {
        return value.toString();
      }
    }
    return null;
  }

  @Override
  @Nullable
  public FileLocation getVersionSource(@NotNull Module module) {
    GradleBuildFile buildFile = GradleBuildFile.get(module);
    if (buildFile != null) {
      return new FileLocation(buildFile.getFile());
    }
    return null;
  }

  @Override
  @NotNull
  public List<NotificationHyperlink> getQuickFixes(@NotNull Module module,
                                                   @Nullable VersionRange expectedVersion,
                                                   @Nullable FileLocation location) {
    FileLocation source = location;
    if (source == null) {
      source = getVersionSource(module);
    }
    if (source != null) {
      NotificationHyperlink quickFix = new OpenBuildFileHyperlink(module, source);
      return singletonList(quickFix);
    }
    return emptyList();
  }

  @Override
  public boolean isProjectLevel() {
    return false;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "'" + myComponentName + "'";
  }

  private static class OpenBuildFileHyperlink extends NotificationHyperlink {
    @NotNull private final FileLocation myFileLocation;

    OpenBuildFileHyperlink(@NotNull Module module, @NotNull FileLocation fileLocation) {
      super("openFile", String.format("Open build.gradle file in module '%1$s'", module.getName()));
      myFileLocation = fileLocation;
    }

    @Override
    protected void execute(@NotNull Project project) {
      Navigatable openFile = new OpenFileDescriptor(project, myFileLocation.file, myFileLocation.lineNumber, myFileLocation.column, false);
      if (openFile.canNavigate()) {
        openFile.navigate(true);
      }
    }
  }
}
