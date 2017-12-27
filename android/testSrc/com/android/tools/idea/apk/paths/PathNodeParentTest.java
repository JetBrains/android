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
package com.android.tools.idea.apk.paths;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Tests for {@link PathNodeParent}.
 */
public class PathNodeParentTest {
  private PathNodeParent myParent;

  @Before
  public void setUp() {
    myParent = new PathNodeParent() {};
  }

  @Test
  public void addChild() {
    List<String> segments = Arrays.asList("one", "two", "three");
    PathNode newChild = myParent.addChild(segments, 0, '/');

    List<PathNode> children = new ArrayList<>(myParent.getChildren());
    assertThat(children).hasSize(1);

    PathNode child = children.get(0);
    assertSame(child, newChild);
    assertEquals("one", child.getPathSegment());
    assertEquals("one", child.getPath());
    assertSame(myParent, child.getParent());

    children = new ArrayList<>(child.getChildren());
    assertThat(children).hasSize(1);

    child = children.get(0);
    assertEquals("two", child.getPathSegment());
    assertEquals("one/two", child.getPath());

    children = new ArrayList<>(child.getChildren());
    assertThat(children).hasSize(1);

    child = children.get(0);
    assertEquals("three", child.getPathSegment());
    assertEquals("one/two/three", child.getPath());
  }
}