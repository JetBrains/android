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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;

/**
 * A block of text, like comments or whitespace.
 *
 * <p>A text block will be entirely of one type (comment/whitespace), and should all have the same
 * indentation.
 */
public class TextBlock implements Serializable {
  private static final long serialVersionUID = 1L;
  private final ImmutableList<String> lines;

  public TextBlock(ImmutableList<String> lines) {
    this.lines = lines;
  }

  /** Returns raw lines, including any indentation and surrounding whitespace */
  public ImmutableList<String> lines() {
    return lines;
  }

  public static TextBlock of(String... lines) {
    return new TextBlock(ImmutableList.<String>builder().add(lines).build());
  }

  /** A text block that is a single newline */
  public static TextBlock newLine() {
    return new TextBlock(ImmutableList.of(""));
  }

  public void print(StringBuilder sb) {
    for (String line : lines) {
      sb.append(line);
      sb.append('\n');
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TextBlock textBlock = (TextBlock) o;
    return Objects.equal(lines, textBlock.lines);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(lines);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    print(sb);
    return sb.toString();
  }
}
