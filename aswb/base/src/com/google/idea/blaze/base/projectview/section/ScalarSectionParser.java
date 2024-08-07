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
package com.google.idea.blaze.base.projectview.section;

import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import javax.annotation.Nullable;

/** Parses scalar values */
public abstract class ScalarSectionParser<T> extends SectionParser {

  private final SectionKey<T, ScalarSection<T>> key;
  private final char divider;

  protected ScalarSectionParser(SectionKey<T, ScalarSection<T>> key, char divider) {
    this.key = key;
    this.divider = divider;
  }

  @Override
  public SectionKey<T, ScalarSection<T>> getSectionKey() {
    return key;
  }

  @Nullable
  @Override
  public final ScalarSection<T> parse(ProjectViewParser parser, ParseContext parseContext) {
    if (parseContext.atEnd()) {
      return null;
    }

    String name = getName();
    ParseContext.Line line = parseContext.current();

    if (!line.text.startsWith(name + divider)) {
      return null;
    }
    String rest = line.text.substring(name.length() + 1).trim();
    parseContext.consume();
    T item = parseItem(parser, parseContext, rest);
    return item != null ? new ScalarSection<>(key, item) : null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void print(StringBuilder sb, Section<?> section) {
    sb.append(getName()).append(divider);
    if (divider != ' ') {
      sb.append(' ');
    }
    printItem(sb, ((ScalarSection<T>) section).getValue());
    sb.append('\n');
  }

  /** Used by psi-parser for validation. */
  public char getDivider() {
    return divider;
  }

  @Nullable
  protected abstract T parseItem(ProjectViewParser parser, ParseContext parseContext, String rest);

  protected abstract void printItem(StringBuilder sb, T value);
}
