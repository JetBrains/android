/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.files;

import static com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.REFERENCE;

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleVersionCatalogFile extends GradleDslFile {
  private final @NotNull String catalogName;

  GradleVersionCatalogFile(@NotNull VirtualFile file,
                           @NotNull Project project,
                           @NotNull String moduleName,
                           @NotNull String catalogName,
                           @NotNull BuildModelContext context) {
    super(file, project, moduleName, context);
    this.catalogName = catalogName;
  }

  public @NotNull String getCatalogName() {
    return catalogName;
  }

  @Override
  public void parse() {
    myGradleDslParser.parse();
  }

  /**
   * Represents reference to libraries from bundles.
   * Difference from literal is that bundle reference cannot become an expression
   */
  public static class GradleBundleRefLiteral extends GradleDslLiteral {

    public GradleBundleRefLiteral(@NotNull GradleDslElement parent,
                                  @NotNull GradleNameElement name) {
      super(parent, name);
    }

    public GradleBundleRefLiteral(@NotNull GradleDslElement parent,
                                  @NotNull PsiElement psiElement,
                                  @NotNull GradleNameElement name,
                                  @NotNull PsiElement literal,
                                  @NotNull LiteralType literalType) {
      super(parent, psiElement, name, literal, literalType);
    }
  }

  public static class GradleDslVersionLiteral extends GradleDslLiteral {
    public GradleDslVersionLiteral(
      @NotNull GradleDslElement parent,
      @NotNull PsiElement psiElement,
      @NotNull GradleNameElement name,
      @NotNull PsiElement literal,
      @NotNull LiteralType literalType
    ) {
      super(parent, psiElement, name, literal, literalType);
      ref = literalType == REFERENCE;
      initialRef = ref;
    }

    public GradleDslVersionLiteral(
      @NotNull GradleDslElement parent,
      @NotNull GradleNameElement name,
      @NotNull Class<?> valueClass
    ) {
      super(parent, name);
      ref = ReferenceTo.class.isAssignableFrom(valueClass);
      initialRef = ref;
    }

    private boolean ref;
    private boolean initialRef;

    @Override
    public boolean isReference() {
      return ref;
    }

    @Override
    public void setValue(@NotNull Object value) {
      if (value instanceof ReferenceTo) {
        if(isReference()) deleteOldDependencies();

        GradleDslElement referredElement = ((ReferenceTo) value).getReferredElement();
        super.setValue(referredElement.getName());
        setupNewDependency(referredElement);

        ref = true;
        return;
      }
      super.setValue(value);
      ref = false;
    }

    private void deleteOldDependencies() {
      myDependencies.forEach(e -> {
        if (e.getToBeInjected() != null) e.getToBeInjected().unregisterDependent(e);
      });
      myDependencies.clear();
    }

    private void setupNewDependency(GradleDslElement targetVersion) {
      if (getCurrentElement() != null) { // cannot create injection for new element
        GradleReferenceInjection injection = new GradleReferenceInjection(this, targetVersion, getCurrentElement(), targetVersion.getName());
        targetVersion.registerDependent(injection);
        addDependency(injection);
      }
    }

    @Override
    public @Nullable PsiElement create() {
      GradleNameElement name = getNameElement();
      if(getPsiElement() == null) initialRef = ref;
      if (ref && getPsiElement() == null) {
        // TODO need to fix this as in fact dot notation means a map with property - b/300075092
        setNameElement(GradleNameElement.create("version.ref"));
      }
      PsiElement psiElement = super.create();
      if (psiElement != null) {
        // we need to set the final name up to be "version" from the Psi for deletion to work correctly.
        setNameElement(GradleNameElement.from(psiElement.getParent().getFirstChild().getFirstChild(), getDslFile().getParser()));
      }
      else {
        // creation failed, so re-use the original name element.
        setNameElement(name);
      }
      return psiElement;
    }

    @Override
    protected void apply() {
      if (ref != initialRef) {
        delete();
        setPsiElement(null);
        create();
      }
      super.apply();
    }

    @Override
    public void delete() {
      if (getPsiElement() != null && getNameElement().getNamedPsiElement() != null) {
        // Note: this depends on the deletion routine in TomlDslWriter being implemented by finding the KeyValue parent of the given
        // PsiElement.  Normally the PsiElement of a DslLiteral is the value; here, just prior to deletion, we set the psiElement to be the
        // `version` key fragment so that deletion of both version.ref = ... and version = { ref = ... } forms works correctly.
        setPsiElement(getNameElement().getNamedPsiElement());
      }
      super.delete();
    }
  }

  public List<GradleReferenceInjection> getInjection(GradleDslSimpleExpression expression, PsiElement psiElement) {
    List<GradleReferenceInjection> result = new ArrayList<>();
    GradleDslExpressionMap versions = getPropertyElement(GradleDslExpressionMap.VERSIONS);
    if (versions != null && expression.isReference() && expression instanceof GradleDslVersionLiteral) {
      String targetName = expression.getValue(String.class);
      if (targetName != null) {
        GradleDslElement targetProperty = versions.getPropertyElement(targetName);
        if (targetProperty != null) {
          GradleReferenceInjection injection = new GradleReferenceInjection(expression, targetProperty, psiElement, targetName);
          result.add(injection);
        }
      }
    }
    return result;
  }
}
