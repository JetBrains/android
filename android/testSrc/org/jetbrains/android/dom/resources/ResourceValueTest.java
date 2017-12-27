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
package org.jetbrains.android.dom.resources;

import com.android.resources.ResourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.junit.Assert.*;

public class ResourceValueTest {
  private static void assertValue(@NotNull String resName, @Nullable ResourceValue value) {
    assertNotNull(value);
    assertEquals(ResourceType.SAMPLE_DATA.getName(), value.getResourceType());
    assertEquals(resName, value.getResourceName());
  }

  @Test
  public void sampleDataParsing() {
    ResourceValue value = ResourceValue.parse("@sample/", true, true, false);
    assertValue("", value);

    value = ResourceValue.parse("@sample/test", true, true, false);
    assertValue("test", value);
    assertNull(value.getNamespace());

    value = ResourceValue.parse("@sample/test[1]", true, true, false);
    assertValue("test[1]", value);
    assertNull(value.getNamespace());

    value = ResourceValue.parse("@sample/test[:1]", true, true, false);
    assertValue("test[:1]", value);
    assertNull(value.getNamespace());

    value = ResourceValue.parse("@tools:sample/test[1:]", true, true, false);
    assertValue("test[1:]", value);
    assertEquals("tools", value.getNamespace());

    value = ResourceValue.parse("@tools:sample/test", true, true, false);
    assertValue("test", value);
    assertEquals("tools", value.getNamespace());

    value = ResourceValue.parse("@tools:sample/test.json/data[:10]", true, true, false);
    assertValue("test.json/data[:10]", value);
  }
}