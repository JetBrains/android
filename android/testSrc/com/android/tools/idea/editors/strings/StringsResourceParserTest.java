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
package com.android.tools.idea.editors.strings;

import com.android.ide.common.res2.ResourceItem;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.ModuleResourceRepository;
import com.google.common.base.Function;
import com.google.common.collect.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class StringsResourceParserTest extends AndroidTestCase {
  public void testParser() {
    VirtualFile res = myFixture.copyDirectoryToProject("stringsEditor/res", "res");
    ModuleResourceRepository repository = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res));
    StringResourceData data = StringResourceParser.parse(repository);

    Set<String> locales = Sets.newHashSet(Iterables.transform(data.getLocales(), new Function<Locale, String>() {
      @Override
      public String apply(Locale input) {
        return input.toLocaleId();
      }
    }));
    assertSameElements(locales, ImmutableSet.of("en-GB", "en-IN", "fr", "hi"));

    Map<String, ResourceItem> defaultValues = data.getDefaultValues();
    assertEquals(4, defaultValues.size());
    assertContainsElements(defaultValues.keySet(), ImmutableSet.of("key1", "key2", "key3", "key5"));

    List<String> untranslatableKeys = data.getUntranslatableKeys();
    assertSameElements(untranslatableKeys, Lists.newArrayList("key5"));

    Table<String, Locale, ResourceItem> translations = data.getTranslations();
    assertNull(translations.get("key1", Locale.create("hi")));
    assertEquals("Key 2 hi", StringResourceData.resourceToString(translations.get("key2", Locale.create("hi"))));
  }
}
