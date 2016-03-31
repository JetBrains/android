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
package com.android.tools.idea.editors.theme.qualifiers;

import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;

public class RestrictedLocaleTest extends TestCase {

  /**
   * Tests {@link RestrictedLocale#intersect(RestrictedQualifier)}
   */
  public void testIntersect() {
    RestrictedLocale first = new RestrictedLocale();
    RestrictedLocale second = new RestrictedLocale();
    RestrictedLocale expected = new RestrictedLocale();
    first.setRestrictions(null, ImmutableList.<ResourceQualifier>of(new LocaleQualifier("kz")));
    second.setRestrictions(null, ImmutableList.<ResourceQualifier>of(new LocaleQualifier("en")));
    expected.setRestrictions(null, ImmutableList.<ResourceQualifier>of(new LocaleQualifier("en"), new LocaleQualifier("kz")));
    assertEquals(expected, first.intersect(second));

    first = new RestrictedLocale();
    second = new RestrictedLocale();
    expected = new RestrictedLocale();
    first.setRestrictions(new LocaleQualifier("en"), ImmutableList.<ResourceQualifier>of());
    second.setRestrictions(null, ImmutableList.<ResourceQualifier>of(new LocaleQualifier("kz"), new LocaleQualifier("ru")));
    expected.setRestrictions(new LocaleQualifier("en"), ImmutableList.<ResourceQualifier>of());
    assertEquals(expected, first.intersect(second));

    first = new RestrictedLocale();
    second = new RestrictedLocale();
    expected = new RestrictedLocale();
    first.setRestrictions(new LocaleQualifier("en"), ImmutableList.<ResourceQualifier>of());
    second.setRestrictions(null, ImmutableList.<ResourceQualifier>of(new LocaleQualifier("kz"), new LocaleQualifier("ru")));
    expected.setRestrictions(new LocaleQualifier("en"), ImmutableList.<ResourceQualifier>of());
    assertEquals(expected, first.intersect(second));
    assertEquals(expected, second.intersect(first));

    first = new RestrictedLocale();
    second = new RestrictedLocale();
    first.setRestrictions(new LocaleQualifier("en"), ImmutableList.<ResourceQualifier>of());
    second.setRestrictions(null, ImmutableList.<ResourceQualifier>of(new LocaleQualifier("kz"), new LocaleQualifier("en")));
    assertEquals(null, first.intersect(second));

    first = new RestrictedLocale();
    second = new RestrictedLocale();
    expected = new RestrictedLocale();
    first.setRestrictions(new LocaleQualifier("en"), ImmutableList.<ResourceQualifier>of());
    second.setRestrictions(new LocaleQualifier("en"), ImmutableList.<ResourceQualifier>of());
    expected.setRestrictions(new LocaleQualifier("en"), ImmutableList.<ResourceQualifier>of());
    assertEquals(expected, first.intersect(second));

    first = new RestrictedLocale();
    second = new RestrictedLocale();
    first.setRestrictions(new LocaleQualifier("en"), ImmutableList.<ResourceQualifier>of());
    second.setRestrictions(new LocaleQualifier("kz"), ImmutableList.<ResourceQualifier>of());
    assertEquals(null, first.intersect(second));
  }
}
