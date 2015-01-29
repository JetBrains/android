/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import junit.framework.TestCase;

public class LocaleTest extends TestCase {
  public void test1() {
    Locale locale = Locale.create("en-rUS");
    assertEquals("en", locale.qualifier.getLanguage());
    assertEquals("US", locale.qualifier.getRegion());
    assertTrue(locale.hasLanguage());
    assertTrue(locale.hasRegion());
  }

  public void test2() {
    Locale locale = Locale.create("zh");
    assertEquals("zh", locale.qualifier.getLanguage());
    assertNull(locale.qualifier.getRegion());
    assertTrue(locale.hasLanguage());
    assertFalse(locale.hasRegion());
  }

  public void testEquals() {
    Locale locale = Locale.create("zh");
    assertEquals("zh", locale.qualifier.getLanguage());
    assertNull(locale.qualifier.getRegion());
    assertTrue(locale.hasLanguage());
    assertFalse(locale.hasRegion());
  }

  public void test() {
    LocaleQualifier qualifier1 = LocaleQualifier.getQualifier("nb");
    LocaleQualifier qualifier2 = LocaleQualifier.getQualifier("no");
    LocaleQualifier qualifier3 = LocaleQualifier.getQualifier("nb-rNO");
    LocaleQualifier qualifier4 = LocaleQualifier.getQualifier("nb-rSE");
    LocaleQualifier qualifier5 = LocaleQualifier.getQualifier("no-rSE");
    assertNotNull(qualifier1);
    assertNotNull(qualifier2);
    assertNotNull(qualifier3);
    assertNotNull(qualifier4);
    assertNotNull(qualifier5);

    assertEquals(Locale.ANY, Locale.ANY);
    assertFalse(Locale.ANY.hasLanguage());
    assertFalse(Locale.ANY.hasRegion());
    //noinspection ConstantConditions
    assertFalse(Locale.create(new LocaleQualifier(LocaleQualifier.FAKE_VALUE)).hasLanguage());
    //noinspection ConstantConditions
    assertFalse(Locale.create(new LocaleQualifier(LocaleQualifier.FAKE_VALUE)).hasRegion());

    assertEquals(Locale.create(qualifier1), Locale.create(qualifier1));
    assertTrue(Locale.create(qualifier1).hasLanguage());
    assertFalse(Locale.create(qualifier1).hasRegion());
    assertTrue(Locale.create(qualifier3).hasLanguage());
    assertTrue(Locale.create(qualifier3).hasRegion());

    assertEquals(Locale.create(qualifier3), Locale.create(qualifier3));
    assertEquals(Locale.create(qualifier1), Locale.create(qualifier1));
    assertTrue(Locale.create(qualifier1).equals(Locale.create(qualifier1)));
    assertTrue(Locale.create(qualifier3).equals(Locale.create(qualifier3)));
    assertFalse(Locale.create(qualifier3).equals(Locale.create(qualifier4)));
    assertFalse(Locale.create(qualifier1).equals(Locale.create(qualifier3)));
    assertFalse(Locale.create(qualifier1).equals(Locale.create(qualifier2)));
    assertFalse(Locale.create(qualifier3).equals(Locale.create(qualifier5)));
    assertEquals("nb", Locale.create(qualifier1).toString());
    assertEquals("nb-NO", Locale.create(qualifier3).toString());

    assertEquals(Locale.create(qualifier1), Locale.create("b+nb"));
    assertEquals(Locale.create(qualifier3), Locale.create("b+nb+NO"));
  }

  public void testFolderConfig() {
    FolderConfiguration config = new FolderConfiguration();
    assertEquals(Locale.ANY, Locale.create(config));
    config.setLocaleQualifier(LocaleQualifier.getQualifier("en"));
    assertEquals(Locale.create("en"), Locale.create(config));
    config.setLocaleQualifier(LocaleQualifier.getQualifier("en-rUS"));
    assertEquals(Locale.create("en-rUS"), Locale.create(config));
  }
}
