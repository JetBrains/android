/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.resourceManagers;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.testing.TestProjectPaths.UIBUILDER_PROPERTY;
import static com.google.common.truth.Truth.assertThat;

public class LocalResourceManagerTest extends AndroidGradleTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadProject(UIBUILDER_PROPERTY);
    generateSources();
  }

  public void testGetAttributeDefinitionsWithLibraryNames() {}
  public void ignore_testGetAttributeDefinitionsWithLibraryNames() {
    VfsUtil.markDirtyAndRefresh(false, true, false, getProject().getBaseDir());
    LocalResourceManager manager = ModuleResourceManagers.getInstance(myAndroidFacet).getLocalResourceManager();
    AttributeDefinitions defs = manager.getAttributeDefinitions();
    assertAttribute(defs, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, CONSTRAINT_LAYOUT_LIB_ARTIFACT);
    assertAttribute(defs, ATTR_LAYOUT_RIGHT_CREATOR, CONSTRAINT_LAYOUT_LIB_ARTIFACT);
    assertAttribute(defs, ATTR_LAYOUT_TOP_CREATOR, CONSTRAINT_LAYOUT_LIB_ARTIFACT);
    assertAttribute(defs, ATTR_COLLAPSE_PARALLAX_MULTIPLIER, DESIGN_LIB_ARTIFACT);
    assertAttribute(defs, ATTR_RIPPLE_COLOR, DESIGN_LIB_ARTIFACT);
    assertAttribute(defs, ATTR_COLOR, APPCOMPAT_LIB_ARTIFACT);
  }

  private static void assertAttribute(@NotNull AttributeDefinitions defs, @NotNull String attributeName, @NotNull String libraryArtifact) {
    AttributeDefinition definition = defs.getAttrDefByName(attributeName);
    assertThat(definition).isNotNull();
    assertThat(definition.getLibraryName()).startsWith(libraryArtifact);
  }
}
