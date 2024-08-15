/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Collection;

/** Augments the java importer */
public interface BlazeJavaSyncAugmenter {
  ExtensionPointName<BlazeJavaSyncAugmenter> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.JavaSyncAugmenter");

  /**
   * Adds extra libraries for this source rule.
   *
   * @param jars The output jars for the rule. Subject to jdeps optimization.
   * @param genJars Generated jars from this rule. Added unconditionally.
   */
  void addJarsForSourceTarget(
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ProjectViewSet projectViewSet,
      TargetIdeInfo target,
      Collection<BlazeJarLibrary> jars,
      Collection<BlazeJarLibrary> genJars);

  /**
   * Return true when generated jar should be attached with workspace. This function provide ability
   * to not attach generated jar for specific target.
   *
   * <p>TODO(b/157683101): remove once https://youtrack.jetbrains.com/issue/KT-24309 is fixed.
   */
  default boolean shouldAttachGenJar(TargetIdeInfo target) {
    return true;
  }
}
