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
package com.android.tools.idea.avdmanager;

import com.google.common.base.Splitter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

public class SkinLayoutDefinitionTest {
  SkinLayoutDefinition mySkinLayoutDefinition;

  @Before
  public void setUp() throws Exception {
    Iterator<String> tokens = Splitter.on(SkinLayoutDefinition.ourWhitespacePattern).split("layouts {\n" +
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
                                                         "            name     controls\n" +
                                                         "            x        0\n" +
                                                         "            y        0\n" +
                                                         "        }\n" +
                                                         "    }\n" +
                                                         "}").iterator();
    mySkinLayoutDefinition = SkinLayoutDefinition.loadFromTokens(tokens);
  }

  @Test
  public void testNonExistentKeys() throws Exception {
    Assert.assertNull(mySkinLayoutDefinition.get("foo"));
    Assert.assertNull(mySkinLayoutDefinition.get("layouts.bar"));
    Assert.assertNull(mySkinLayoutDefinition.get("layouts.portrait.zzz"));
    Assert.assertNull(mySkinLayoutDefinition.get("layouts.portrait.width.foo"));
  }

  @Test
  public void testIncompleteKeys() throws Exception {
    Assert.assertNull(mySkinLayoutDefinition.get("layouts"));
    Assert.assertNull(mySkinLayoutDefinition.get("layouts.portrait"));
  }

  @Test
  public void testCompleteKeys() throws Exception {
    Assert.assertEquals("device", mySkinLayoutDefinition.get("layouts.portrait.part2.name"));
    Assert.assertEquals("30", mySkinLayoutDefinition.get("layouts.portrait.part2.x"));
    Assert.assertEquals("0", mySkinLayoutDefinition.get("layouts.portrait.part3.y"));
    Assert.assertEquals("100", mySkinLayoutDefinition.get("layouts.portrait.onion.alpha"));
  }

  @Test
  public void testToString() throws Exception {
    Assert.assertEquals("{\n" +
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
                        "}\n", mySkinLayoutDefinition.toString());
  }
}