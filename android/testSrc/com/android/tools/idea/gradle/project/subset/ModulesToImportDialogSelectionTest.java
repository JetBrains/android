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
package com.android.tools.idea.gradle.project.subset;

import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link com.android.tools.idea.gradle.project.subset.ModulesToImportDialog.Selection}
 */
public class ModulesToImportDialogSelectionTest extends TestCase {
  public void testSaveAndLoad() throws Exception {
    File file = FileUtil.createTempFile("selection", ".xml", true);

    List<DataNode<ModuleData>> modules = Lists.newArrayList();
    modules.add(createModule("project"));
    modules.add(createModule("app"));

    ModulesToImportDialog.Selection.save(modules, file);
    List<String> moduleNames = ModulesToImportDialog.Selection.load(file);
    assertThat(moduleNames).containsOnly("project", "app");
  }

  @NotNull
  private static DataNode<ModuleData> createModule(@NotNull String name) {
    String path = "~/project/" + name;
    ModuleData data = new ModuleData(name, GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), name, path, path);
    return new DataNode<>(MODULE, data, null);
  }
}