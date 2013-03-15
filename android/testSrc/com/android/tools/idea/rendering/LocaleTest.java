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

import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import junit.framework.TestCase;

public class LocaleTest extends TestCase {
  public void test1() {
    Locale locale = Locale.create("en-rUS");
    assertEquals("en", locale.language.getValue());
    assertEquals("US", locale.region.getValue());
    assertTrue(locale.hasLanguage());
    assertTrue(locale.hasRegion());
  }

  public void test2() {
    Locale locale = Locale.create("zh");
    assertEquals("zh", locale.language.getValue());
    assertEquals(RegionQualifier.FAKE_REGION_VALUE, locale.region.getValue());
    assertTrue(locale.hasLanguage());
    assertFalse(locale.hasRegion());
  }

  public void testEquals() {
    Locale locale = Locale.create("zh");
    assertEquals("zh", locale.language.getValue());
    assertEquals(RegionQualifier.FAKE_REGION_VALUE, locale.region.getValue());
    assertTrue(locale.hasLanguage());
    assertFalse(locale.hasRegion());
  }

  public void test() {
    LanguageQualifier language1 = new LanguageQualifier("nb");
    LanguageQualifier language2 = new LanguageQualifier("no");
    RegionQualifier region1 = new RegionQualifier("NO");
    RegionQualifier region2 = new RegionQualifier("SE");

    assertEquals(Locale.ANY, Locale.ANY);
    assertFalse(Locale.ANY.hasLanguage());
    assertFalse(Locale.ANY.hasRegion());
    assertFalse(Locale.create(new LanguageQualifier(LanguageQualifier.FAKE_LANG_VALUE),
                              new RegionQualifier(RegionQualifier.FAKE_REGION_VALUE)).hasLanguage());
    assertFalse(Locale.create(new LanguageQualifier(LanguageQualifier.FAKE_LANG_VALUE),
                              new RegionQualifier(RegionQualifier.FAKE_REGION_VALUE)).hasRegion());

    assertEquals(Locale.create(language1), Locale.create(language1));
    assertTrue(Locale.create(language1).hasLanguage());
    assertFalse(Locale.create(language1).hasRegion());
    assertTrue(Locale.create(language1, region1).hasLanguage());
    assertTrue(Locale.create(language1, region1).hasRegion());

    assertEquals(Locale.create(language1, region1), Locale.create(language1, region1));
    assertEquals(Locale.create(language1), Locale.create(language1));
    assertTrue(Locale.create(language1).equals(Locale.create(language1)));
    assertTrue(Locale.create(language1, region1).equals(Locale.create(language1, region1)));
    assertFalse(Locale.create(language1, region1).equals(Locale.create(language1, region2)));
    assertFalse(Locale.create(language1).equals(Locale.create(language1, region1)));
    assertFalse(Locale.create(language1).equals(Locale.create(language2)));
    assertFalse(Locale.create(language1, region1).equals(Locale.create(language2, region1)));
    assertEquals("Locale{nb, __}", Locale.create(language1).toString());
    assertEquals("Locale{nb, NO}", Locale.create(language1, region1).toString());
  }
}
