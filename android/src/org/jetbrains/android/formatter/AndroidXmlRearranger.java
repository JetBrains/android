/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.formatter;

import com.intellij.psi.codeStyle.arrangement.ArrangementSettingsSerializer;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementSettingsSerializer;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementSettingsSerializer.Mixin;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Regexp;
import com.intellij.xml.arrangement.XmlRearranger;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AndroidXmlRearranger extends XmlRearranger {
  private AndroidXmlRearranger() {
  }

  @NotNull
  public static StdArrangementMatchRule newAttributeRule(@NotNull String qualifiedNameRegex,
                                                         @NotNull String xmlNamespaceRegex,
                                                         @NotNull ArrangementSettingsToken order) {
    StdArrangementEntryMatcher matcher = new StdArrangementEntryMatcher(ArrangementUtil.combine(
      new ArrangementAtomMatchCondition(EntryType.XML_ATTRIBUTE),
      new ArrangementAtomMatchCondition(Regexp.NAME, qualifiedNameRegex),
      new ArrangementAtomMatchCondition(Regexp.XML_NAMESPACE, xmlNamespaceRegex)));

    return new StdArrangementMatchRule(matcher, order);
  }

  @NotNull
  @Override
  public ArrangementSettingsSerializer getSerializer() {
    StdArrangementEntryMatcher matcher = new StdArrangementEntryMatcher(new ArrangementAtomMatchCondition(Regexp.NAME, "xmlns:.*"));
    StdArrangementMatchRule rule = new StdArrangementMatchRule(matcher);
    StdArrangementSettings settings = StdArrangementSettings.createByMatchRules(Collections.emptyList(), Collections.singletonList(rule));

    return new DefaultArrangementSettingsSerializer(new AndroidXmlMixin(), settings);
  }

  @Override
  public @NotNull Collection<ArrangementTabInfo> getArrangementTabInfos() {
    // No need to duplicate what's already returned by XmlRearranger. Otherwise, there will be two identical 'XML' items in the popup on the Actions on Save page.
    return Collections.emptyList();
  }

  private static final class AndroidXmlMixin implements Mixin {
    @Nullable
    @Override
    public ArrangementSettingsToken deserializeToken(@NotNull String id) {
      return id.equals(AndroidAttributeOrder.ID) ? AndroidAttributeOrder.INSTANCE : null;
    }
  }
}
