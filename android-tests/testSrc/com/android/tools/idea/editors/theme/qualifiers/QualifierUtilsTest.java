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

import com.android.ide.common.resources.configuration.*;
import com.android.resources.LayoutDirection;
import org.jetbrains.android.AndroidTestCase;

public class QualifierUtilsTest extends AndroidTestCase {

  /**
   * Tests {@link QualifierUtils#getValue(ResourceQualifier)}
   */
  public void testGetValue() {
    assertEquals(120, QualifierUtils.getValue(new ScreenWidthQualifier(120)));
    assertEquals(LayoutDirection.LTR, QualifierUtils.getValue(new LayoutDirectionQualifier(LayoutDirection.LTR)));
  }

  /**
   * Tests {@link QualifierUtils#getValueReturnType(Class)}
   */
  public void testGetValueReturnType() {
    assertEquals(LayoutDirection.class, QualifierUtils.getValueReturnType(LayoutDirectionQualifier.class));
    assertEquals(int.class, QualifierUtils.getValueReturnType(ScreenWidthQualifier.class));
    assertEquals(null, QualifierUtils.getValueReturnType(VersionQualifier.class));
  }

  /**
   * Tests {@link QualifierUtils#createNewResourceQualifier(Class, Object)}
   */
  public void testCreateNewResourceQualifier() {
    assertEquals(new VersionQualifier(12), QualifierUtils.createNewResourceQualifier(VersionQualifier.class, Integer.valueOf(12)));
    assertEquals(new LayoutDirectionQualifier(LayoutDirection.LTR), QualifierUtils.createNewResourceQualifier(LayoutDirectionQualifier.class, LayoutDirection.LTR));
  }
}
