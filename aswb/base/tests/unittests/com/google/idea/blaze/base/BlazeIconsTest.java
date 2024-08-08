/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base;

import static org.junit.Assert.assertNotNull;

import icons.BlazeIcons;
import javax.swing.Icon;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for BlazeIcons */
@RunWith(JUnit4.class)
public class BlazeIconsTest {

  @Test
  public void testIcon() {
    Icon icon = BlazeIcons.Logo;

    assertNotNull(icon);
  }
}
