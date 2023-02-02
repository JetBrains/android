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

import static com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.LITERAL;
import static com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.REFERENCE;

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
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
    replaceVersionRefsWithInjections();
    replaceLibraryRefsInBundlesWithInjections();
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
    GradleDslVersionLiteral(
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
      @NotNull Object value
    ) {
      super(parent, name);
      ref = value instanceof ReferenceTo;
      initialRef = ref;
    }

    private boolean ref;
    final private boolean initialRef;

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
      myDependencies.forEach(e -> e.getToBeInjected().unregisterDependent(e));
      myDependencies.clear();
    }

    private void setupNewDependency(GradleDslElement targetVersion) {
      if (getPsiElement() != null) { // cannot create injection for new element
        GradleReferenceInjection injection = new GradleReferenceInjection(this, targetVersion, getPsiElement(), targetVersion.getName());
        targetVersion.registerDependent(injection);
        addDependency(injection);
      }
    }

    @Override
    public @Nullable PsiElement create() {
      GradleNameElement name = getNameElement();
      if (ref && getPsiElement() == null) {
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

  protected void replaceVersionRefsWithInjections() {
    GradleDslExpressionMap libraries = getPropertyElement("libraries", GradleDslExpressionMap.class);
    GradleDslExpressionMap plugins = getPropertyElement("plugins", GradleDslExpressionMap.class);
    GradleDslExpressionMap versions = getPropertyElement("versions", GradleDslExpressionMap.class);
    if (versions == null) return;
    Consumer<GradleDslExpressionMap> versionRefReplacer = (library) -> {
      GradleDslElement versionProperty = library.getPropertyElement("version");
      if (versionProperty instanceof GradleDslExpressionMap) {
        GradleDslExpressionMap version = (GradleDslExpressionMap)versionProperty;
        GradleDslElement refProperty = version.getPropertyElement("ref");
        if (refProperty instanceof GradleDslLiteral) {
          GradleDslLiteral ref = (GradleDslLiteral)refProperty;
          String targetName = ref.getValue(String.class);
          if (targetName != null) {
            GradleDslElement targetProperty = versions.getPropertyElement(targetName);
            if (targetProperty != null) {
              GradleDslLiteral reference =
                new GradleDslVersionLiteral(library, ref.getPsiElement(), versionProperty.getNameElement(), ref.getPsiElement(), REFERENCE);
              // TODO(xof): this pre-resolution of the injection is (probably) fine if we are happy with the changes in property
              //  visibility that implies.  If we wanted to avoid the surgery below, to make sure that dependencies are properly
              //  registered in both directions, we should be able to use a proper targetName (I think it should be
              //    `"versions." + targetName`
              //  so that the natural walk up the properties tree finds the correct element.)
              GradleReferenceInjection injection = new GradleReferenceInjection(reference, targetProperty, ref.getPsiElement(), targetName);
              targetProperty.registerDependent(injection);
              reference.addDependency(injection);
              library.substituteElement(versionProperty, reference);
            }
          }
        }
      }
      else if (versionProperty instanceof GradleDslLiteral) {
        GradleDslLiteral version = (GradleDslLiteral)versionProperty;
        GradleDslLiteral literal =
          new GradleDslVersionLiteral(library, version.getPsiElement(), version.getNameElement(), version.getPsiElement(), LITERAL);
        library.substituteElement(versionProperty, literal);
      }
    };
    if (libraries != null) {
      libraries.getPropertyElements(GradleDslExpressionMap.class).forEach(versionRefReplacer);
    }
    if (plugins != null) {
      plugins.getPropertyElements(GradleDslExpressionMap.class).forEach(versionRefReplacer);
    }
  }

  protected void replaceLibraryRefsInBundlesWithInjections() {
    GradleDslExpressionMap libraries = getPropertyElement("libraries", GradleDslExpressionMap.class);
    GradleDslExpressionMap bundles = getPropertyElement("bundles", GradleDslExpressionMap.class);

    if (bundles == null) return;

    Consumer<GradlePropertiesDslElement> libraryRefReplacer = (bundle) -> {
      List<GradleDslElement> elements = bundle.getCurrentElements();
      elements.forEach(element -> {
        if (element instanceof GradleDslLiteral) {
          GradleDslLiteral ref = (GradleDslLiteral)element;
          String targetName = ref.getValue(String.class);
          GradleDslElement targetProperty = libraries.getPropertyElement(targetName);
          if (targetProperty != null) {
            GradleDslLiteral reference =
              new GradleBundleRefLiteral(bundle, ref.getPsiElement(), targetProperty.getNameElement(),
                                          ref.getPsiElement(), REFERENCE);
            GradleReferenceInjection injection =
              new GradleReferenceInjection(reference, targetProperty, ref.getPsiElement(), targetName);
            targetProperty.registerDependent(injection);
            reference.addDependency(injection);
            bundle.substituteElement(element, reference);
          }
        }
      });
    };

    bundles.getPropertyElements(GradlePropertiesDslElement.class).forEach(libraryRefReplacer);
  }

}
