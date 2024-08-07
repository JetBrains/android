/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.editor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.Set;

/** A collector that accumulates statistics about highlighted elements in a file */
public interface BlazeHighlightStatsCollector {

  ExtensionPointName<BlazeHighlightStatsCollector> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeHighlightStatsCollector");

  /** Fetches all collectors that can handle a given file. */
  static ImmutableSet<BlazeHighlightStatsCollector> getCollectorsSupportingFile(PsiFile file) {
    return BlazeHighlightStatsCollector.EP_NAME.getExtensionList().stream()
        .filter(e -> e.canProcessFile(file))
        .collect(ImmutableSet.toImmutableSet());
  }

  /** Returns a map from {@link HighlightInfoType} to the collectors that support them. */
  static Multimap<HighlightInfoType, BlazeHighlightStatsCollector>
      getCollectorsByHighlightInfoTypes(Set<BlazeHighlightStatsCollector> collectors) {
    Multimap<HighlightInfoType, BlazeHighlightStatsCollector> map = HashMultimap.create();
    for (BlazeHighlightStatsCollector collector : collectors) {
      for (HighlightInfoType highlightInfoType : collector.supportedHighlightInfoTypes()) {
        map.put(highlightInfoType, collector);
      }
    }
    return map;
  }

  /** Set of {@link HighlightInfoType} supported by a collector */
  Set<HighlightInfoType> supportedHighlightInfoTypes();

  /**
   * Returns whether the collector can process the file. It is used to filter collectors that can
   * handle a file.
   *
   * <p>If this method returns false, {@link #processHighlight(PsiElement, HighlightInfo)} will not
   * be called for the given file.
   */
  boolean canProcessFile(PsiFile file);

  /**
   * Processes the highlight. This method will be called once per highlight in the file. This will
   * be called under a read-lock, so be aware of how much time this takes.
   *
   * <p>Guarantees that {@link #canProcessFile(PsiFile)} returned true for
   * `psiElement.getContainingFile` and HighlightInfoType of `highlightInfo` is in the set returned
   * by {@link #supportedHighlightInfoTypes()}.
   */
  void processHighlight(PsiElement psiElement, HighlightInfo highlightInfo);
}
