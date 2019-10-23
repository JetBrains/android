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
package com.android.tools.idea.resources.aar;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.testFramework.UsefulTestCase.assertSameElements;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.ResourcesTestsUtil;
import com.intellij.util.containers.ContainerUtil;
import java.util.Collection;
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
    assertThat(repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ID)).isEmpty();
  }

  public void testGetAllDeclaredIds_noRDotTxt() {
    // There's no R.txt, so the layout file should be parsed and the two ids found.
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository("my_aar_lib_noRDotTxt");
    assertThat(repository.getIdsFromRTxt()).isNull();
    assertThat(repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ID).keySet())
      .containsExactly(
          "btn_title_refresh",
          "bug123032845",
          "header",
          "image",
          "imageButton",
          "imageView",
          "imageView2",
          "nonExistent",
          "noteArea",
          "styledView",
          "text2",
          "title_refresh_progress");
  }

  public void testGetAllDeclaredIds_wrongRDotTxt() {
    // IDs should come from R.txt, not parsing the layout.
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository("my_aar_lib_wrongRDotTxt");
    assertThat(repository.getIdsFromRTxt()).containsExactly("id1", "id2", "id3");
    assertThat(repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ID)).isEmpty();
  }

  public void testGetAllDeclaredIds_brokenRDotTxt() {
    // We can't parse R.txt, so we fall back to parsing layouts.
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository("my_aar_lib_brokenRDotTxt");
    assertThat(repository.getIdsFromRTxt()).isNull();
    assertThat(repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ID).keySet()).containsExactly("id_from_layout");
  }

  public void testMultipleValues_wholeResourceDirectory() {
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository();
    List<ResourceItem> items = repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.STRING, "hello");
    assertNotNull(items);
    List<String> helloVariants = ContainerUtil.map(
      items,
      resourceItem -> {
        ResourceValue value = resourceItem.getResourceValue();
        assertNotNull(value);
        return value.getValue();
      });
    assertSameElements(helloVariants, "bonjour", "hello", "hola");

    items = repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "MyTheme.Dark");
    assertThat(items.size()).isEqualTo(1);
    StyleResourceValue styleValue = (StyleResourceValue)items.get(0).getResourceValue();
    assertThat(styleValue.getParentStyleName()).isEqualTo("android:Theme.Light");
    Collection<StyleItemResourceValue> styleItems = styleValue.getDefinedItems();
    assertThat(styleItems.size()).isEqualTo(2);
    StyleItemResourceValue textColor = styleValue.getItem(ResourceNamespace.ANDROID, "textColor");
    assertThat(textColor.getAttrName()).isEqualTo("android:textColor");
    assertThat(textColor.getValue()).isEqualTo("#999999");
    StyleItemResourceValue foo = styleValue.getItem(ResourceNamespace.RES_AUTO, "foo");
    assertThat(foo.getAttrName()).isEqualTo("foo");
    assertThat(foo.getValue()).isEqualTo("?android:colorForeground");

    items = repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.STYLEABLE, "Styleable1");
    assertThat(items.size()).isEqualTo(1);
    StyleableResourceValue styleableValue = (StyleableResourceValue)items.get(0).getResourceValue();
    List<AttrResourceValue> attributes = styleableValue.getAllAttributes();
    assertThat(attributes.size()).isEqualTo(1);
    AttrResourceValue attr = attributes.get(0);
    assertThat(attr.getName()).isEqualTo("some_attr");
    assertThat(attr.getFormats()).containsExactly(AttributeFormat.COLOR);

    items = repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.STYLEABLE, "Styleable.with.dots");
    assertThat(items.size()).isEqualTo(1);
    styleableValue = (StyleableResourceValue)items.get(0).getResourceValue();
    attributes = styleableValue.getAllAttributes();
    assertThat(attributes.size()).isEqualTo(1);
    attr = attributes.get(0);
    assertThat(attr.getName()).isEqualTo("some_attr");
    assertThat(attr.getFormats()).containsExactly(AttributeFormat.COLOR);

    items = repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "some_attr");
    assertThat(items.size()).isEqualTo(1);
    attr = (AttrResourceValue)items.get(0).getResourceValue();
    assertThat(attr.getName()).isEqualTo("some_attr");
    assertThat(attr.getFormats()).containsExactly(AttributeFormat.COLOR);

    items = repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "app_attr1");
    assertThat(items).isEmpty();

    items = repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "app_attr2");
    assertThat(items.size()).isEqualTo(1);
    attr = (AttrResourceValue)items.get(0).getResourceValue();
    assertThat(attr.getName()).isEqualTo("app_attr2");
    assertThat(attr.getFormats()).containsExactly(AttributeFormat.BOOLEAN,
                                                        AttributeFormat.COLOR,
                                                        AttributeFormat.DIMENSION,
                                                        AttributeFormat.FLOAT,
                                                        AttributeFormat.FRACTION,
                                                        AttributeFormat.INTEGER,
                                                        AttributeFormat.REFERENCE,
                                                        AttributeFormat.STRING);
  }

  public void testMultipleValues_partOfResourceDirectories() {
    AarSourceResourceRepository repository =
        ResourcesTestsUtil.getTestAarRepositoryWithResourceFolders("my_aar_lib", "values/strings.xml", "values-fr/strings.xml");
    List<ResourceItem> items = repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.STRING, "hello");
    assertNotNull(items);
    List<String> helloVariants = ContainerUtil.map(
      items,
      resourceItem -> {
        ResourceValue value = resourceItem.getResourceValue();
        assertNotNull(value);
        return value.getValue();
      });
    assertSameElements(helloVariants, "bonjour", "hello");
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
