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
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Section with a text block. */
public final class TextBlockSection extends Section<TextBlock> {
  public static final SectionKey<TextBlock, TextBlockSection> KEY = SectionKey.of("textblock");
  public static final TextBlockSectionParser PARSER = new TextBlockSectionParser();

  private final TextBlock textBlock;

  public TextBlockSection(TextBlock textBlock) {
    super(KEY);
    this.textBlock = textBlock;
  }

  public TextBlock getTextBlock() {
    return textBlock;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    TextBlockSection that = (TextBlockSection) o;
    return Objects.equal(textBlock, that.textBlock);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), textBlock);
  }

  public static TextBlockSection of(TextBlock textBlock) {
    return new TextBlockSection(textBlock);
  }

  /** Parses a single text block with equal indentation. */
  public static TextBlock parseTextBlock(ParseContext parseContext) {
    return TextBlockSectionParser.parseTextBlock(parseContext);
  }

  /** Text block section. */
  private static final class TextBlockSectionParser extends SectionParser {
    private static final Pattern COMMENT_REGEX = Pattern.compile("^\\s*#.*$");
    private static final Pattern WHITESPACE_REGEX = Pattern.compile("^\\s*$");
    private static final Pattern[] REGEXES = {COMMENT_REGEX, WHITESPACE_REGEX};

    @Override
    public SectionKey<TextBlock, TextBlockSection> getSectionKey() {
      return KEY;
    }

    @Nullable
    @Override
    public Section<?> parse(ProjectViewParser parser, ParseContext parseContext) {
      TextBlock textBlock = parseTextBlock(parseContext);
      if (textBlock == null) {
        return null;
      }
      return new TextBlockSection(textBlock);
    }

    @Nullable
    private static TextBlock parseTextBlock(ParseContext parseContext) {
      for (Pattern regex : REGEXES) {
        TextBlock textBlock = parseTextBlock(parseContext, regex);
        if (textBlock != null) {
          return textBlock;
        }
      }
      return null;
    }

    @Nullable
    private static TextBlock parseTextBlock(ParseContext parseContext, Pattern regex) {
      ImmutableList.Builder<String> lines = null;
      int indent = -1;
      while (!parseContext.atEnd()) {
        if (indent >= 0 && parseContext.current().indent != indent) {
          break;
        }
        if (!regex.matcher(parseContext.currentRawLine()).matches()) {
          break;
        }
        // First line we match?
        if (lines == null) {
          lines = ImmutableList.builder();
          indent = parseContext.current().indent;
        }
        lines.add(parseContext.currentRawLine());
        parseContext.consume();
      }
      if (lines == null) {
        return null;
      }
      return new TextBlock(lines.build());
    }

    @Override
    public void print(StringBuilder sb, Section<?> section) {
      TextBlockSection textBlockSection = (TextBlockSection) section;
      textBlockSection.getTextBlock().print(sb);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Nullable
    @Override
    public String quickDocs() {
      return null;
    }
  }
}
