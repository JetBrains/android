/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.projectview.formatting;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.lang.projectview.formatting.ProjectViewRearranger.Entry;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiFile;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiListItem;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiListSection;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.ArrangementSettingsSerializer;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementSettingsSerializer;
import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsAware;
import com.intellij.psi.codeStyle.arrangement.std.CompositeArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Used when formatting with 'rearrange code' checked (or when running 'rearrange code' directly) to
 * reorder project view file elements.
 *
 * <p>Currently limited to ordering list elements by string.
 */
public class ProjectViewRearranger implements Rearranger<Entry>, ArrangementStandardSettingsAware {

  static class Entry extends DefaultArrangementEntry implements NameAwareArrangementEntry {
    private final String name;

    Entry(@Nullable ArrangementEntry parent, String name, TextRange range, boolean canBeMatched) {
      super(parent, range.getStartOffset(), range.getEndOffset(), canBeMatched);
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  @Nullable
  @Override
  public Pair<Entry, List<Entry>> parseWithNew(
      PsiElement root,
      @Nullable Document document,
      Collection<? extends TextRange> ranges,
      PsiElement element,
      ArrangementSettings settings) {
    // no support for generating new elements
    return null;
  }

  @Override
  public List<Entry> parse(
      PsiElement root,
      @Nullable Document document,
      Collection<? extends TextRange> ranges,
      ArrangementSettings settings) {
    if (root instanceof ProjectViewPsiListSection) {
      Entry entry = fromListSection(ranges, (ProjectViewPsiListSection) root);
      return entry != null ? ImmutableList.of(entry) : ImmutableList.of();
    }
    if (root instanceof ProjectViewPsiFile) {
      return Arrays.stream(
              ((ProjectViewPsiFile) root).findChildrenByClass(ProjectViewPsiListSection.class))
          .map(section -> fromListSection(ranges, section))
          .filter(Objects::nonNull)
          .collect(toImmutableList());
    }
    return ImmutableList.of();
  }

  @Nullable
  private static Entry fromListSection(
      Collection<? extends TextRange> ranges, ProjectViewPsiListSection listSection) {
    if (!isWithinBounds(ranges, listSection.getTextRange())) {
      return null;
    }
    String name = listSection.getSectionName();
    if (name == null) {
      return null;
    }
    Entry parent = new Entry(null, name, listSection.getTextRange(), /* canBeMatched= */ false);
    List<Entry> children =
        Arrays.stream(listSection.childrenOfClass(ProjectViewPsiListItem.class))
            .filter(item -> isWithinBounds(ranges, item.getTextRange()))
            .map(
                item ->
                    new Entry(
                        parent, item.getText(), item.getTextRange(), /* canBeMatched= */ true))
            .collect(toImmutableList());
    if (children.size() < 2) {
      return null;
    }
    children.forEach(parent::addChild);
    return parent;
  }

  private static boolean isWithinBounds(Collection<? extends TextRange> ranges, TextRange range) {
    return ranges.stream().anyMatch(r -> r.intersects(range));
  }

  @Override
  public int getBlankLines(
      CodeStyleSettings settings, @Nullable Entry parent, @Nullable Entry previous, Entry target) {
    // no blank lines adjustment; only sorting within individual list sections
    return -1;
  }

  // accept everything with a name, then order by name
  private static final StdArrangementMatchRule MATCH_RULE =
      new StdArrangementMatchRule(
          new StdArrangementEntryMatcher(
              new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, ".*")),
          StdArrangementTokens.Order.BY_NAME);

  private static final StdArrangementSettings DEFAULT_SETTINGS =
      StdArrangementSettings.createByMatchRules(ImmutableList.of(), ImmutableList.of(MATCH_RULE));

  @Override
  public ArrangementSettingsSerializer getSerializer() {
    return new DefaultArrangementSettingsSerializer(DEFAULT_SETTINGS);
  }

  @Override
  public StdArrangementSettings getDefaultSettings() {
    return DEFAULT_SETTINGS;
  }

  @Nullable
  @Override
  public List<CompositeArrangementSettingsToken> getSupportedGroupingTokens() {
    return null;
  }

  @Nullable
  @Override
  public List<CompositeArrangementSettingsToken> getSupportedMatchingTokens() {
    return null;
  }

  @Override
  public boolean isEnabled(
      ArrangementSettingsToken token, @Nullable ArrangementMatchCondition current) {
    return true;
  }

  @Override
  public ArrangementEntryMatcher buildMatcher(ArrangementMatchCondition condition) {
    throw new IllegalArgumentException("Can't build a matcher for condition " + condition);
  }

  @Override
  public Collection<Set<ArrangementSettingsToken>> getMutexes() {
    return ImmutableList.of();
  }
}
