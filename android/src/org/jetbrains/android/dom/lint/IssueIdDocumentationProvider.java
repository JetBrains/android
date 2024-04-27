/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.dom.lint;

import static com.android.tools.lint.detector.api.TextFormat.TEXT;

import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.Vendor;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Option;
import com.android.tools.lint.detector.api.TextFormat;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import java.util.List;
import org.jetbrains.android.dom.ProvidedDocumentationPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IssueIdDocumentationProvider implements DocumentationProvider {

  @Nullable
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    // Check whether the element is attribute value
    if (!(element instanceof XmlAttributeValue)) {
      return null;
    }

    final PsiFile file = element.getContainingFile();
    if (!(file instanceof XmlFile)) {
      return null;
    }

    // Check whether the current XML file is lint.xml file
    final DomFileElement<LintDomElement> fileElement =
      DomManager.getDomManager(element.getProject()).getFileElement((XmlFile)file, LintDomElement.class);
    if (fileElement == null) {
      return null;
    }

    final Issue issue = IssueIdConverter.getIdSet().get(((XmlAttributeValue)element).getValue());
    if (issue == null) {
      return null;
    }

    return getDocumentation(issue);
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLookupItem(@NotNull PsiManager psiManager, @NotNull Object object, @NotNull PsiElement element) {
    if (!(object instanceof Issue)) {
      return null;
    }

    final Issue issue = (Issue)object;
    return getDocumentationElementForLookupItem(psiManager, issue);
  }

  @VisibleForTesting
  PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Issue issue) {
    String documentation = getDocumentation(issue);
    return new ProvidedDocumentationPsiElement(psiManager, Language.ANY, issue.getId(), documentation);
  }

  @NotNull
  private String getDocumentation(Issue issue) {
    String documentation = issue.getExplanation(TextFormat.HTML);

    List<Option> options = issue.getOptions();
    if (!options.isEmpty()) {
      String optionsHtml = Option.Companion.describe(options, TextFormat.HTML, true);
      documentation = documentation + "<br/>\n" + optionsHtml;
    }

    Vendor vendor = issue.getVendor();
    IssueRegistry registry = issue.getRegistry();
    if (vendor == null && registry != null) {
      vendor = registry.getVendor();
    }
    if (vendor != null && vendor != IssueRegistry.Companion.getAOSP_VENDOR()) {
      documentation = documentation + "<br/>\n" + vendor.describe(TextFormat.HTML);
    }
    return documentation;
  }
}
