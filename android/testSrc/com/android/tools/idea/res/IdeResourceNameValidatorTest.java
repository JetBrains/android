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
package com.android.tools.idea.res;

import com.android.SdkConstants;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.junit.Test;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

public class IdeResourceNameValidatorTest {
  @Test
  public void testValidator() throws Exception {
    // Valid
    IdeResourceNameValidator validator = IdeResourceNameValidator.forResourceName(ResourceType.COLOR);
    assertTrue(validator.getErrorText("foo") == null);
    assertTrue(validator.checkInput("foo"));
    assertTrue(validator.canClose("foo"));
    assertTrue(validator.getErrorText("foo.xml") == null);
    assertTrue(validator.getErrorText("Foo123_$") == null);
    assertTrue(validator.getErrorText("foo.xm") == null); // For resources defined in xml, . => _

    // Invalid
    assertEquals("Enter a new name", validator.getErrorText(""));
    assertFalse(validator.checkInput(""));
    assertFalse(validator.canClose(""));
    assertEquals("Enter a new name", validator.getErrorText(" "));
    assertEquals("' ' is not a valid resource name character", validator.getErrorText("foo bar"));
    assertEquals("The resource name must start with a letter", validator.getErrorText("1foo"));
    assertEquals("'%' is not a valid resource name character", validator.getErrorText("foo%bar"));

    assertThat(IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE).getErrorText("foo.xm"))
      .startsWith("'.' is not a valid file-based resource name character");

    // Only lowercase chars allowed in file-based resource names.
    assertThat(IdeResourceNameValidator.forFilename(ResourceFolderType.LAYOUT).getErrorText("Foo123_$"))
      .startsWith("'F' is not a valid file-based resource name character");
    assertEquals(null, IdeResourceNameValidator.forFilename(ResourceFolderType.LAYOUT).getErrorText("foo123_"));

    assertThat(IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE, SdkConstants.DOT_XML).getErrorText("foo.xml"))
      .isNull();
    assertThat(IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE).getErrorText("foo.xml"))
      .startsWith("'.' is not a valid file-based resource name character");
    assertThat(IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE).getErrorText("foo.1.2"))
      .startsWith("'.' is not a valid file-based resource name character");

    // Build system does NOT allow, so we must also not allow this.
    assertEquals("'-' is not a valid resource name character",
                 IdeResourceNameValidator.forResourceName(ResourceType.STYLE).getErrorText("style-name"));
    assertEquals("':' is not a valid resource name character",
                 IdeResourceNameValidator.forResourceName(ResourceType.STYLE).getErrorText("style:name"));
  }

  @Test
  public void testIds() throws Exception {
    IdeResourceNameValidator validator = IdeResourceNameValidator.forResourceName(ResourceType.ID);
    assertEquals(null, validator.getErrorText("foo"));
    assertEquals("The resource name must start with a letter", validator.getErrorText(" foo"));
    assertEquals("' ' is not a valid resource name character", validator.getErrorText("foo "));
    assertEquals("'@' is not a valid resource name character", validator.getErrorText("foo@"));
  }

  @Test
  public void testIds2() throws Exception {
    ListMultimap<String, ResourceItem> multimap = ArrayListMultimap.create();
    multimap.put("foo1", new ResourceItem("foo1", null, ResourceType.ID, null, null));
    multimap.put("foo3", new ResourceItem("foo3", null, ResourceType.ID, null, null));
    multimap.put("foo.4", new ResourceItem("foo.4", null, ResourceType.ID, null, null));
    multimap.put("foo_5", new ResourceItem("foo_5", null, ResourceType.ID, null, null));

    LocalResourceRepository resources = new TestLocalResourceRepository();
    resources.getItems().put(RES_AUTO, ResourceType.ID, multimap);

    IdeResourceNameValidator validator = IdeResourceNameValidator.forResourceName(ResourceType.ID, resources);
    assertEquals("foo1 already exists", validator.getErrorText("foo1"));
    assertEquals(null, validator.getErrorText("foo2"));
    assertEquals("foo3 already exists", validator.getErrorText("foo3"));

    assertEquals("foo_4 already exists", validator.getErrorText("foo.4"));
    assertEquals("foo_4 already exists", validator.getErrorText("foo_4"));
    assertEquals("foo_5 already exists", validator.getErrorText("foo.5"));
    assertEquals("foo_5 already exists", validator.getErrorText("foo_5"));

    assertTrue(validator.doesResourceExist("foo1"));
    assertTrue(validator.doesResourceExist("foo.4"));
    assertTrue(validator.doesResourceExist("foo_4"));
    assertTrue(validator.doesResourceExist("foo.5"));
    assertTrue(validator.doesResourceExist("foo_5"));
    assertFalse(validator.doesResourceExist("foo_no"));
  }

  @Test
  public void publicXml() throws Exception {
    assertThat(IdeResourceNameValidator.forFilename(ResourceFolderType.VALUES).checkInput("public")).isTrue();
    assertThat(IdeResourceNameValidator.forFilename(ResourceFolderType.COLOR).checkInput("public")).isFalse();
    assertThat(IdeResourceNameValidator.forFilename(ResourceFolderType.VALUES, SdkConstants.DOT_XML).checkInput("public.xml"))
      .isTrue();
    assertThat(IdeResourceNameValidator.forFilename(ResourceFolderType.COLOR, SdkConstants.DOT_XML).checkInput("public.xml"))
      .isFalse();
  }

  @Test
  public void extensions() throws Exception {
    IdeResourceNameValidator validator = IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE, SdkConstants.DOT_XML);
    assertThat(validator.checkInput("foo.xml")).isTrue();
    assertThat(validator.checkInput("foo.xm")).isFalse();
    assertThat(validator.checkInput("foo.png")).isFalse();
    assertThat(validator.checkInput("foo.png.xml")).isFalse();
    assertThat(validator.checkInput("foo.9.xml")).isFalse();
  }
}
