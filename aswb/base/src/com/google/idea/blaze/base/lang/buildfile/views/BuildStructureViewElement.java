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
package com.google.idea.blaze.base.lang.buildfile.views;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import java.util.Collection;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Handles nodes in Structure View. */
public class BuildStructureViewElement extends PsiTreeElementBase<BuildElement> {

  private final BuildElement element;

  public BuildStructureViewElement(BuildElement element) {
    super(element);
    this.element = element;
  }

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    if (!(element instanceof BuildFile)) {
      // TODO: show inner build rules in Skylark .bzl extensions
      return ImmutableList.of();
    }
    ImmutableList.Builder<StructureViewTreeElement> builder = ImmutableList.builder();
    for (BuildElement child : ((BuildFile) element).findChildrenByClass(BuildElement.class)) {
      builder.add(new BuildStructureViewElement(child));
    }
    return builder.build();
  }

  @Nullable
  @Override
  public String getPresentableText() {
    return element.getPresentableText();
  }

  @Override
  public String getLocationString() {
    return element.getLocationString();
  }
}
