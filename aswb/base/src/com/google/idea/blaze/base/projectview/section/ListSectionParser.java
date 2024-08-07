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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.sections.ItemOrTextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import java.util.List;
import javax.annotation.Nullable;

/** List section parser base class. */
public abstract class ListSectionParser<T> extends SectionParser {
  private final SectionKey<T, ListSection<T>> key;

  protected ListSectionParser(SectionKey<T, ListSection<T>> key) {
    this.key = key;
  }

  @Override
  public SectionKey<T, ListSection<T>> getSectionKey() {
    return key;
  }

  @Nullable
  @Override
  public final ListSection<T> parse(ProjectViewParser parser, ParseContext parseContext) {
    if (parseContext.atEnd()) {
      return null;
    }

    String name = getName();
    if (!parseContext.current().text.equals(name + ':')) {
      return null;
    }
    parseContext.consume();

    ImmutableList.Builder<ItemOrTextBlock<T>> builder = ImmutableList.builder();

    boolean correctIndentationRun = true;
    List<ItemOrTextBlock<T>> savedTextBlocks = Lists.newArrayList();
    while (!parseContext.atEnd()) {
      boolean isIndented = parseContext.current().indent == SectionParser.INDENT;
      if (!isIndented && correctIndentationRun) {
        parseContext.savePosition();
      }
      correctIndentationRun = isIndented;

      ItemOrTextBlock<T> itemOrTextBlock = null;
      TextBlock textBlock = TextBlockSection.parseTextBlock(parseContext);
      if (textBlock != null) {
        itemOrTextBlock = new ItemOrTextBlock<>(textBlock);
      } else if (isIndented) {
        T item = parseItem(parser, parseContext);
        if (item != null) {
          parseContext.consume();
          itemOrTextBlock = new ItemOrTextBlock<>(item);
        }
      }

      if (itemOrTextBlock == null) {
        break;
      }

      if (isIndented) {
        builder.addAll(savedTextBlocks);
        builder.add(itemOrTextBlock);
        savedTextBlocks.clear();
        parseContext.clearSavedPosition();
      } else {
        savedTextBlocks.add(new ItemOrTextBlock<>(textBlock));
      }
    }
    parseContext.resetToSavedPosition();

    ImmutableList<ItemOrTextBlock<T>> items = builder.build();
    if (items.isEmpty()) {
      parseContext.addError(String.format("Empty section: '%s'", name));
    }

    return new ListSection<>(key, items);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void print(StringBuilder sb, Section<?> section) {
    ListSection<T> listSection = (ListSection<T>) section;

    // Omit empty sections completely
    if (listSection.itemsOrComments().isEmpty()) {
      return;
    }

    sb.append(getName()).append(':').append('\n');
    for (ItemOrTextBlock<T> item : listSection.itemsOrComments()) {
      if (item.item != null) {
        for (int i = 0; i < SectionParser.INDENT; ++i) {
          sb.append(' ');
        }
        printItem(item.item, sb);
        sb.append('\n');
      } else if (item.textBlock != null) {
        item.textBlock.print(sb);
      }
    }
  }

  @Nullable
  protected abstract T parseItem(ProjectViewParser parser, ParseContext parseContext);

  protected abstract void printItem(T item, StringBuilder sb);
}
