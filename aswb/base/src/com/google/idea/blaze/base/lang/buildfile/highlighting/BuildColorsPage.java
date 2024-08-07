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
package com.google.idea.blaze.base.lang.buildfile.highlighting;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import java.util.Map;
import javax.swing.Icon;

/** Allows user to customize colors. */
public class BuildColorsPage
    implements ColorSettingsPage, InspectionColorSettingsPage, DisplayPrioritySortable {
  private static final AttributesDescriptor[] ATTRS =
      new AttributesDescriptor[] {
        new AttributesDescriptor("Keyword", BuildSyntaxHighlighter.BUILD_KEYWORD),
        new AttributesDescriptor("String", BuildSyntaxHighlighter.BUILD_STRING),
        new AttributesDescriptor("Number", BuildSyntaxHighlighter.BUILD_NUMBER),
        new AttributesDescriptor("Line Comment", BuildSyntaxHighlighter.BUILD_LINE_COMMENT),
        new AttributesDescriptor("Operation Sign", BuildSyntaxHighlighter.BUILD_OPERATION_SIGN),
        new AttributesDescriptor("Parentheses", BuildSyntaxHighlighter.BUILD_PARENS),
        new AttributesDescriptor("Brackets", BuildSyntaxHighlighter.BUILD_BRACKETS),
        new AttributesDescriptor("Braces", BuildSyntaxHighlighter.BUILD_BRACES),
        new AttributesDescriptor("Comma", BuildSyntaxHighlighter.BUILD_COMMA),
        new AttributesDescriptor("Dot", BuildSyntaxHighlighter.BUILD_DOT),
        new AttributesDescriptor("Function definition", BuildSyntaxHighlighter.BUILD_FN_DEFINITION),
        new AttributesDescriptor("Parameter", BuildSyntaxHighlighter.BUILD_PARAMETER),
        new AttributesDescriptor("Keyword argument", BuildSyntaxHighlighter.BUILD_KEYWORD_ARG),
        new AttributesDescriptor("Built-in name", BuildSyntaxHighlighter.BUILD_BUILTIN_NAME),
      };

  private static final Map<String, TextAttributesKey> ourTagToDescriptorMap =
      ImmutableMap.<String, TextAttributesKey>builder()
          .put("funcDef", BuildSyntaxHighlighter.BUILD_FN_DEFINITION)
          .put("param", BuildSyntaxHighlighter.BUILD_PARAMETER)
          .put("kwarg", BuildSyntaxHighlighter.BUILD_KEYWORD_ARG)
          .put("comma", BuildSyntaxHighlighter.BUILD_COMMA)
          .put("number", BuildSyntaxHighlighter.BUILD_NUMBER)
          .put("keyword", BuildSyntaxHighlighter.BUILD_KEYWORD)
          .put("builtin", BuildSyntaxHighlighter.BUILD_BUILTIN_NAME)
          .build();

  @Override
  public String getDisplayName() {
    return Blaze.defaultBuildSystemName() + " BUILD files";
  }

  @Override
  public Icon getIcon() {
    return BuildFileType.INSTANCE.getIcon();
  }

  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  public SyntaxHighlighter getHighlighter() {
    final SyntaxHighlighter highlighter =
        SyntaxHighlighterFactory.getSyntaxHighlighter(BuildFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @Override
  public String getDemoText() {
    return "def <funcDef>function</funcDef>(<param>x</param>, <kwarg>whatever</kwarg>=1):\n"
        + "    s = (\"Test\", 2+3, {'a': 'b'}, <param>x</param>)   # Comment\n"
        + "    <builtin>print</builtin> s[0].lower()\n"
        + "    <keyword>return</keyword> <builtin>True</builtin>"
        + "\n"
        + "<builtin>java_library</builtin>(\n"
        + "    <kwarg>name</kwarg> = \"lib\",\n"
        + "    <kwarg>srcs</kwarg> = glob([\"**/*.java\"]),\n"
        + ")\n";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.LANGUAGE_SETTINGS;
  }
}
