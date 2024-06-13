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

package com.android.tools.idea.observable.constraints;
import org.junit.Test;

import com.android.tools.idea.avdmanager.AvdNameVerifier;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AvdNameVerifier}
 */
public class AvdNameVerifierTest {

  @Test
  public void testIsValid() throws Exception {

    assertThat( AvdNameVerifier.isValid("Simple") ).isTrue();
    assertThat( AvdNameVerifier.isValid("this.name is-also_(OK) 45") ).isTrue();

    assertThat( AvdNameVerifier.isValid("either/or") ).isFalse();
    assertThat( AvdNameVerifier.isValid("9\" nails") ).isFalse();
    assertThat( AvdNameVerifier.isValid("6' under") ).isFalse();
    assertThat( AvdNameVerifier.isValid("") ).isFalse();
  }

  @Test
  public void testStripBadCharacters() throws Exception {

    assertThat( AvdNameVerifier.stripBadCharacters("Simple") ).isEqualTo("Simple");
    assertThat( AvdNameVerifier.stripBadCharacters("this.name is-also_(OK) 45") ).isEqualTo("this.name is-also_(OK) 45");

    assertThat( AvdNameVerifier.stripBadCharacters("either/or") ).isEqualTo("either or");
    assertThat( AvdNameVerifier.stripBadCharacters("9\" nails") ).isEqualTo("9  nails");
    assertThat( AvdNameVerifier.stripBadCharacters("6' under") ).isEqualTo("6  under");
  }

  @Test
  public void testStripBadCharactersAndCollapse() throws Exception {

    assertThat( AvdNameVerifier.stripBadCharactersAndCollapse("") ).isEqualTo("");
    assertThat( AvdNameVerifier.stripBadCharactersAndCollapse("Simple") ).isEqualTo("Simple");
    assertThat( AvdNameVerifier.stripBadCharactersAndCollapse("no_change.f0r_this-string") ).isEqualTo("no_change.f0r_this-string");

    assertThat( AvdNameVerifier.stripBadCharactersAndCollapse(" ") ).isEqualTo("");
    assertThat( AvdNameVerifier.stripBadCharactersAndCollapse("this.name is-also_(OK) 45") ).isEqualTo("this.name_is-also_OK_45");
    assertThat( AvdNameVerifier.stripBadCharactersAndCollapse("  either/or _ _more ") ).isEqualTo("either_or_more");
    assertThat( AvdNameVerifier.stripBadCharactersAndCollapse("9\" nails__  ") ).isEqualTo("9_nails_");
    assertThat( AvdNameVerifier.stripBadCharactersAndCollapse("'6' under'") ).isEqualTo("6_under");
  }

}
