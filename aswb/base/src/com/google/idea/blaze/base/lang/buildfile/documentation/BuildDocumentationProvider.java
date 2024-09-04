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
package com.google.idea.blaze.base.lang.buildfile.documentation;

import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.DocStringOwner;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.ParameterList;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import javax.annotation.Nullable;

/** Provides quick docs for some BUILD elements. */
public class BuildDocumentationProvider extends AbstractDocumentationProvider {

  private static final String LINK_TYPE_FILE = "#file#";

  @Nullable
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (element instanceof DocStringOwner) {
      return buildDocs((DocStringOwner) element);
    }
    if (element instanceof FuncallExpression) {
      return docsForBuiltInRule(
          element.getProject(), ((FuncallExpression) element).getFunctionName());
    }
    return null;
  }

  /** Returns the corresponding built-in rule in the BUILD file language, if one exists. */
  @Nullable
  private static RuleDefinition getBuiltInRule(Project project, @Nullable String ruleName) {
    BuildLanguageSpec spec = BuildLanguageSpecProvider.getInstance(project).getLanguageSpec();
    return spec != null ? spec.getRule(ruleName) : null;
  }

  @Nullable
  private static String docsForBuiltInRule(Project project, @Nullable String ruleName) {
    RuleDefinition rule = getBuiltInRule(project, ruleName);
    if (rule == null) {
      return null;
    }
    String link = Blaze.getBuildSystemProvider(project).getRuleDocumentationUrl(rule);
    if (link == null) {
      return null;
    }
    return String.format(
        "External documentation for %s:<br><a href=\"%s\">%s</a>", rule.getName(), link, link);
  }

  private static void describeFile(PsiFile file, StringBuilder builder, boolean linkToFile) {
    if (!(file instanceof BuildFile)) {
      return;
    }
    BuildFile buildFile = (BuildFile) file;
    Label label = buildFile.getBuildLabel();
    String name = label != null ? label.toString() : buildFile.getPresentableText();
    if (linkToFile) {
      builder
          .append("<a href=\"")
          .append(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)
          .append(LINK_TYPE_FILE)
          .append("\">")
          .append(name)
          .append("</a>");
    } else {
      builder.append(String.format("<b>%s</b>", name));
    }
    builder.append("<br><br>");
  }

  private static String buildDocs(DocStringOwner element) {
    StringBuilder docs = new StringBuilder();
    describeFile(element.getContainingFile(), docs, !(element instanceof BuildFile));

    if (element instanceof FunctionStatement) {
      describeFunction((FunctionStatement) element, docs);
    }
    StringLiteral docString = element.getDocString();
    if (docString != null) {
      docs.append(DocStringFormatter.formatDocString(docString, element));
    }
    return wrapDocInHtml(docs.toString());
  }

  private static void describeFunction(FunctionStatement function, StringBuilder builder) {
    // just show the function declaration verbatim, including the parameter list.
    ParameterList paramList = function.getParameterList();
    if (paramList == null) {
      return;
    }
    builder
        .append("def ")
        .append("<b>")
        .append(function.getName())
        .append("</b>")
        .append(paramList.getNode().getChars())
        .append("<br><br>");
  }

  private static String wrapDocInHtml(String doc) {
    return "<html><body><code>" + doc + "</code></body></html>";
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLink(
      PsiManager psiManager, String link, PsiElement context) {
    if (link.equals(LINK_TYPE_FILE)) {
      return context.getContainingFile();
    }
    return null;
  }
}
