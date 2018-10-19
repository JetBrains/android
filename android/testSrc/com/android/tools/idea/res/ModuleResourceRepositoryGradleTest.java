/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.testFramework.VfsTestUtil.createFile;

import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import java.util.List;

/** Tests for {@link ModuleResourceRepository} based on {@link AndroidGradleTestCase}. */
public class ModuleResourceRepositoryGradleTest extends AndroidGradleTestCase {
  // This test provides additional coverage relative to ModuleResourceRepositoryTest.testOverlays by exercising
  // the ModuleResourceRepository.forMainResources method that may affect resource overlay order. See http://b/117805863.
  public void testOverlayOrder() throws Exception {
    loadProject(PROJECT_WITH_APPAND_LIB);
    createFile(
        getProject().getBaseDir(),
        "app/src/debug/res/values/strings.xml",
        "" +
        "<resources>\n" +
        "  <string name=\"app_name\">This app_name definition should win</string>\n" +
        "</resources>");
    Module appModule = myModules.getAppModule();
    LocalResourceRepository repository = ResourceRepositoryManager.getModuleResources(appModule);
    List<ResourceItem> resources = repository.getResources(RES_AUTO, ResourceType.STRING, "app_name");
    assertThat(resources).hasSize(1);
    // Check that the debug version of app_name takes precedence over the default on.
    assertThat(resources.get(0).getResourceValue().getValue()).isEqualTo("This app_name definition should win");
  }
}
