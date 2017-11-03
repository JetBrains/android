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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.rendering.Locale;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

public final class AddLocaleActionTest {
  private Collection<Locale> myLocales;

  @Before
  public void getLocales() {
    myLocales = AddLocaleAction.getLocales(Collections.emptySet());
  }

  @Test
  public void localesContainLanguageLocale() {
    assertTrue(myLocales.contains(Locale.create("de")));
  }

  @Test
  public void localesContainLanguageRegionLocale() {
    assertTrue(myLocales.contains(Locale.create("en-rUS")));
    assertTrue(myLocales.contains(Locale.create("en-rGB")));
    assertTrue(myLocales.contains(Locale.create("en-rCA")));
  }
}
