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
package com.android.tools.adtui.device;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class SkinLayoutDefinitionTest {
  SkinLayoutDefinition mySkinLayoutDefinition;

  @Before
  public void setUp() {
    String text = "" +
        "layouts {\n" +
        "    portrait {\n" +
        "        width     380\n" +
        "        height    380\n" +
        "        color     0x1f1f1f\n" +
        "        event     EV_SW:0:1\n" +
        "\n" +
        "        onion {\n" +
        "            image     circle_mask_380px_onion.png\n" +
        "            alpha     100\n" +
        "            rotation  0\n" +
        "        }\n" +
        "\n" +
        "        part1 {\n" +
        "            name    portrait\n" +
        "            x       0\n" +
        "            y       0\n" +
        "        }\n" +
        "\n" +
        "        part2 {\n" +
        "            name    device\n" +
        "            x       30\n" +
        "            y       30\n" +
        "        }\n" +
        "\n" +
        "        part3 {\n" +
        "            # the framework _always_ assume that the DPad\n" +
        "            # has been physically rotated in landscape mode.\n" +
        "            # however, with this skin, this is not the case\n" +
        "            #\n" +
        "            name     controls\n" +
        "            x        0\n" +
        "            y        0\n" +
        "        }\n" +
        "    }\n" +
        "}";
    mySkinLayoutDefinition = SkinLayoutDefinition.parseString(text);
  }

  @Test
  public void testGetValue() {
    assertThat(mySkinLayoutDefinition.getValue("layouts.portrait.part2.name")).isEqualTo("device");
    assertThat(mySkinLayoutDefinition.getValue("layouts.portrait.part2.x")).isEqualTo("30");
    assertThat(mySkinLayoutDefinition.getValue("layouts.portrait.part3.y")).isEqualTo("0");
    assertThat(mySkinLayoutDefinition.getValue("layouts.portrait.onion.alpha")).isEqualTo("100");

    // Nonexistent keys.
    assertThat(mySkinLayoutDefinition.getValue("foo")).isNull();
    assertThat(mySkinLayoutDefinition.getValue("layouts.bar")).isNull();
    assertThat(mySkinLayoutDefinition.getValue("layouts.portrait.zzz")).isNull();
    assertThat(mySkinLayoutDefinition.getValue("layouts.portrait.width.foo")).isNull();

    // Incomplete keys.
    assertThat(mySkinLayoutDefinition.getValue("layouts")).isNull();
    assertThat(mySkinLayoutDefinition.getValue("layouts.portrait")).isNull();
  }

  @Test
  public void testGetNode() {
    assertThat(mySkinLayoutDefinition.getNode("layouts.portrait")).isNotNull();
    // Nonexistent node.
    assertThat(mySkinLayoutDefinition.getNode("layouts.portrait.part2.name")).isNull();
    // Invalid query.
    assertThat(mySkinLayoutDefinition.getNode("")).isNull();
  }

  @Test
  public void testGetChildren() {
    SkinLayoutDefinition portrait = mySkinLayoutDefinition.getNode("layouts.portrait");
    assertThat(portrait).isNotNull();
    Map<String, SkinLayoutDefinition> children = portrait.getChildren();
    assertThat(children.keySet()).containsExactly("onion", "part1", "part2", "part3");
  }

  @Test
  public void testToString() {
    assertThat(mySkinLayoutDefinition.toString()).isEqualTo("" +
        "{\n" +
        "  layouts    {\n" +
        "    portrait    {\n" +
        "      color    0x1f1f1f\n" +
        "      event    EV_SW:0:1\n" +
        "      height    380\n" +
        "      width    380\n" +
        "      onion    {\n" +
        "        alpha    100\n" +
        "        image    circle_mask_380px_onion.png\n" +
        "        rotation    0\n" +
        "      }\n" +
        "      part1    {\n" +
        "        name    portrait\n" +
        "        x    0\n" +
        "        y    0\n" +
        "      }\n" +
        "      part2    {\n" +
        "        name    device\n" +
        "        x    30\n" +
        "        y    30\n" +
        "      }\n" +
        "      part3    {\n" +
        "        name    controls\n" +
        "        x    0\n" +
        "        y    0\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n");
  }
}