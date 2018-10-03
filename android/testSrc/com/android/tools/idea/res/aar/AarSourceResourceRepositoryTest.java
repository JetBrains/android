/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res.aar;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.testFramework.UsefulTestCase.assertSameElements;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.ResourcesTestsUtil;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests for {@link AarSourceResourceRepository}.
 */
public class AarSourceResourceRepositoryTest extends TestCase {

  public void testGetAllDeclaredIds_hasRDotTxt() {
    // R.txt contains these 3 ids which are actually not defined anywhere else. The layout file contains "id_from_layout" but it should not
    // be parsed if R.txt is present.
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository();
    assertThat(repository.getIdsFromRTxt()).containsExactly("id1", "id2", "id3");
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID)).isEmpty();
  }

  public void testGetAllDeclaredIds_noRDotTxt() {
    // There's no R.txt, so the layout file should be parsed and the two ids found.
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository("my_aar_lib_noRDotTxt");
    assertThat(repository.getIdsFromRTxt()).isNull();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("id_from_layout");
  }

  public void testGetAllDeclaredIds_wrongRDotTxt() {
    // IDs should come from R.txt, not parsing the layout.
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository("my_aar_lib_wrongRDotTxt");
    assertThat(repository.getIdsFromRTxt()).containsExactly("id1", "id2", "id3");
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID)).isEmpty();
  }

  public void testGetAllDeclaredIds_brokenRDotTxt() {
    // We can't parse R.txt, so we fall back to parsing layouts.
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository("my_aar_lib_brokenRDotTxt");
    assertThat(repository.getIdsFromRTxt()).isNull();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("id_from_layout");
  }

  public void testMultipleValues() {
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository();
    List<ResourceItem> items = repository.getResources(RES_AUTO, ResourceType.STRING, "hello");
    assertNotNull(items);
    List<String> helloVariants = ContainerUtil.map(
      items,
      resourceItem -> {
        ResourceValue value = resourceItem.getResourceValue();
        assertNotNull(value);
        return value.getValue();
      });
    assertSameElements(helloVariants, "bonjour", "hello", "hola");
  }

  public void testLibraryNameIsMaintained() {
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository();
    assertThat(repository.getLibraryName()).isEqualTo(ResourcesTestsUtil.AAR_LIBRARY_NAME);
    for (ResourceItem item : repository.getAllResources()) {
      assertThat(item.getLibraryName()).isEqualTo(ResourcesTestsUtil.AAR_LIBRARY_NAME);
    }
  }

  public void testPackageName() {
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository();
    assertThat(repository.getPackageName()).isEqualTo(ResourcesTestsUtil.AAR_PACKAGE_NAME);
  }
}
