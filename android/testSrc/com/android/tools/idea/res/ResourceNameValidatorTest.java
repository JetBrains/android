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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("javadoc")
public class ResourceNameValidatorTest extends AndroidTestCase {
  public void testValidator() throws Exception {
    // Valid
    ResourceNameValidator validator = ResourceNameValidator.create(true, ResourceFolderType.VALUES);
    assertTrue(validator.getErrorText("foo") == null);
    assertTrue(validator.checkInput("foo"));
    assertTrue(validator.canClose("foo"));
    assertTrue(validator.getErrorText("foo.xml") == null);
    assertTrue(validator.getErrorText("Foo123_$") == null);
    assertTrue(validator.getErrorText("foo.xm") == null); // For non-file types, . => _

    // Invalid
    assertEquals("Enter a new name", validator.getErrorText(""));
    assertFalse(validator.checkInput(""));
    assertFalse(validator.canClose(""));
    assertEquals("Enter a new name", validator.getErrorText(" "));
    assertEquals("' ' is not a valid resource name character", validator.getErrorText("foo bar"));
    assertEquals("The resource name must begin with a character", validator.getErrorText("1foo"));
    assertEquals("'%' is not a valid resource name character", validator.getErrorText("foo%bar"));
    assertEquals("foo already exists",
                 ResourceNameValidator.create(true, Collections.singleton("foo"),ResourceType.STRING).getErrorText("foo"));
    assertEquals("The filename must end with .xml",
                 ResourceNameValidator.create(true, ResourceFolderType.LAYOUT).getErrorText("foo.xm"));
    assertEquals("The filename must end with .xml or .png",
                 ResourceNameValidator.create(true, ResourceFolderType.DRAWABLE).getErrorText("foo.xm"));
    assertEquals("'.' is not a valid resource name character",
                 ResourceNameValidator.create(false, ResourceFolderType.DRAWABLE).getErrorText("foo.xm"));

    // Only lowercase chars allowed in file-based resource names
    assertEquals("File-based resource names must start with a lowercase letter.",
                 ResourceNameValidator.create(true, ResourceFolderType.LAYOUT).getErrorText("Foo123_$"));
    assertEquals(null, ResourceNameValidator.create(true, ResourceFolderType.LAYOUT).getErrorText("foo123_"));

    // Can't start with _ in file-based resource names, is okay for value based resources
    assertEquals(null, ResourceNameValidator.create(true, ResourceFolderType.VALUES).getErrorText("_foo"));
    assertEquals("File-based resource names must start with a lowercase letter.",
                 ResourceNameValidator.create(true, ResourceFolderType.LAYOUT).getErrorText("_foo"));
    assertEquals("File-based resource names must start with a lowercase letter.",
                 ResourceNameValidator.create(true, ResourceFolderType.DRAWABLE).getErrorText("_foo"));

    assertEquals(null, ResourceNameValidator.create(true, ResourceFolderType.DRAWABLE).getErrorText("foo.xml"));
    assertEquals("'.' is not a valid resource name character",
                 ResourceNameValidator.create(false, ResourceFolderType.DRAWABLE).getErrorText("foo.xml"));
    assertEquals("'.' is not a valid resource name character",
                 ResourceNameValidator.create(false, ResourceFolderType.DRAWABLE).getErrorText("foo.1.2"));
  }

  public void testIds() throws Exception {
    ResourceNameValidator validator = ResourceNameValidator.create(false, (LocalResourceRepository)null, ResourceType.ID);
    assertEquals(null, validator.getErrorText("foo"));
    assertEquals("The resource name must begin with a character", validator.getErrorText(" foo"));
    assertEquals("' ' is not a valid resource name character", validator.getErrorText("foo "));
    assertEquals("'@' is not a valid resource name character", validator.getErrorText("foo@"));
  }

  public void testIds2() throws Exception {
    final Map<ResourceType, ListMultimap<String, ResourceItem>> map = Maps.newHashMap();
    ListMultimap<String, ResourceItem> multimap = ArrayListMultimap.create();
    map.put(ResourceType.ID, multimap);
    multimap.put("foo1", new ResourceItem("foo1", ResourceType.ID, null));
    multimap.put("foo3", new ResourceItem("foo3", ResourceType.ID, null));
    multimap.put("foo.4", new ResourceItem("foo.4", ResourceType.ID, null));
    multimap.put("foo_5", new ResourceItem("foo_5", ResourceType.ID, null));
    multimap.put("foo-6", new ResourceItem("foo-6", ResourceType.ID, null));
    multimap.put("foo:7", new ResourceItem("foo:7", ResourceType.ID, null));
    LocalResourceRepository resources = new LocalResourceRepository("unit test") {
      @NonNull
      @Override
      protected Map<ResourceType, ListMultimap<String, ResourceItem>> getMap() {
        return map;
      }

      @Nullable
      @Override
      protected ListMultimap<String, ResourceItem> getMap(ResourceType type, boolean create) {
        return map.get(type);
      }

      @NotNull
      @Override
      protected Set<VirtualFile> computeResourceDirs() {
        return ImmutableSet.of();
      }
    };
    ResourceNameValidator validator = ResourceNameValidator.create(false, resources, ResourceType.ID);
    assertEquals("foo1 already exists", validator.getErrorText("foo1"));
    assertEquals(null, validator.getErrorText("foo2"));
    assertEquals("foo3 already exists", validator.getErrorText("foo3"));

    assertEquals("foo_4 already exists", validator.getErrorText("foo.4"));
    assertEquals("foo_4 already exists", validator.getErrorText("foo:4"));
    assertEquals("foo_4 already exists", validator.getErrorText("foo-4"));
    assertEquals("foo_4 already exists", validator.getErrorText("foo_4"));

    assertEquals("foo_5 already exists", validator.getErrorText("foo.5"));
    assertEquals("foo_5 already exists", validator.getErrorText("foo:5"));
    assertEquals("foo_5 already exists", validator.getErrorText("foo-5"));
    assertEquals("foo_5 already exists", validator.getErrorText("foo_5"));

    assertEquals("foo_6 already exists", validator.getErrorText("foo.6"));
    assertEquals("foo_6 already exists", validator.getErrorText("foo:6"));
    assertEquals("foo_6 already exists", validator.getErrorText("foo-6"));
    assertEquals("foo_6 already exists", validator.getErrorText("foo_6"));

    assertEquals("foo_7 already exists", validator.getErrorText("foo.7"));
    assertEquals("foo_7 already exists", validator.getErrorText("foo:7"));
    assertEquals("foo_7 already exists", validator.getErrorText("foo-7"));
    assertEquals("foo_7 already exists", validator.getErrorText("foo_7"));
  }

  public void testUniqueOrExists() throws Exception {
    Set<String> existing = new HashSet<>();
    existing.add("foo1");
    existing.add("foo2");
    existing.add("foo3");
    existing.add("foo_4");

    ResourceNameValidator validator = ResourceNameValidator.create(true, existing, ResourceType.ID);
    validator.unique();

    assertNull(validator.getErrorText("foo")); // null: ok (no error message)
    assertNull(validator.getErrorText("foo4"));
    assertNotNull(validator.getErrorText("foo1"));
    assertNotNull(validator.getErrorText("foo2"));
    assertNotNull(validator.getErrorText("foo3"));
    assertNotNull(validator.getErrorText("foo_4"));

    validator.exist();
    assertNotNull(validator.getErrorText("foo"));
    assertNotNull(validator.getErrorText("foo4"));
    assertNull(validator.getErrorText("foo1"));
    assertNull(validator.getErrorText("foo2"));
    assertNull(validator.getErrorText("foo3"));
    assertNull(validator.getErrorText("foo_4"));
    assertNull(validator.getErrorText("foo.4"));
    assertNull(validator.getErrorText("foo:4"));
    assertNull(validator.getErrorText("foo-4"));
  }
}