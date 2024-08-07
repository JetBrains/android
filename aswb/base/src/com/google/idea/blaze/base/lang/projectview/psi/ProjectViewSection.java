/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.projectview.psi;

import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.Sections;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Psi element for a list or scalar section. */
public abstract class ProjectViewSection extends ProjectViewPsiElement implements NavigationItem {

  public ProjectViewSection(ASTNode node) {
    super(node);
  }

  @Override
  public ItemPresentation getPresentation() {
    final ProjectViewSection element = this;
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return getSectionName();
      }

      @Override
      public String getLocationString() {
        return null;
      }

      @Override
      public Icon getIcon(boolean unused) {
        return element.getIcon(0);
      }
    };
  }

  @Override
  public String getName() {
    return getSectionName();
  }

  @Nullable
  protected abstract String getSectionName();

  @Nullable
  public SectionParser getSectionParser() {
    String text = getSectionName();
    if (text == null) {
      return null;
    }
    for (SectionParser parser : Sections.getParsers()) {
      if (text.equals(parser.getName())) {
        return parser;
      }
    }
    return null;
  }
}
