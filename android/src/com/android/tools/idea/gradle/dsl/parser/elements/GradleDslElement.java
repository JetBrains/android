/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT_BLOCK_NAME;

/**
 * Provide Gradle specific abstraction over a {@link PsiElement}.
 */
public abstract class GradleDslElement {
  @NotNull protected final String myName;

  @Nullable protected GradleDslElement myParent;
  @NotNull protected List<GradlePropertiesDslElement> myHolders = new ArrayList<>();

  @NotNull private final String myQualifiedName;
  @NotNull private final GradleDslFile myDslFile;

  @Nullable private PsiElement myPsiElement;

  @Nullable private GradleDslClosure myClosureElement;

  private volatile boolean myModified;

  // Whether or not that DslElement should be represented with the assignment syntax i.e "name = 'value'" or
  // the method call syntax i.e "name 'value'". This is needed since on some element types as we do not carry
  // the information to make this distinction. GradleDslElement will set this to a default of false.
  protected boolean myUseAssignment;

  @NotNull private PropertyType myElementType;

  /**
   * Creates an in stance of a {@link GradleDslElement}
   *
   * @param parent     the parent {@link GradleDslElement} of this element. The parent element should always be a not-null value except if
   *                   this element is the root element, i.e a {@link GradleDslFile}.
   * @param psiElement the {@link PsiElement} of this dsl element.
   * @param name       the name of this element.
   */
  protected GradleDslElement(@Nullable GradleDslElement parent, @Nullable PsiElement psiElement, @NotNull String name) {
    assert parent != null || this instanceof GradleDslFile;

    myParent = parent;
    myPsiElement = psiElement;
    myName = name;

    if (parent == null || parent instanceof GradleDslFile) {
      myQualifiedName = name;
    }
    else {
      myQualifiedName = parent.myQualifiedName + "." + name;
    }

    if (parent == null) {
      myDslFile = (GradleDslFile)this;
    }
    else {
      myDslFile = parent.myDslFile;
    }

    myUseAssignment = false;
    // Default to DERIVED, this is overwritten in the parser if required for the given element type.
    myElementType = DERIVED;
  }

  public void setParsedClosureElement(@NotNull GradleDslClosure closureElement) {
    myClosureElement = closureElement;
  }

  @Nullable
  public GradleDslClosure getClosureElement() {
    return myClosureElement;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public GradleDslElement getParent() {
    return myParent;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  public void setPsiElement(@Nullable PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  public boolean shouldUseAssignment() {
    return myUseAssignment;
  }

  public void setUseAssignment(boolean useAssignment) {
    myUseAssignment = useAssignment;
  }

  @NotNull
  public PropertyType getElementType() {
    return myElementType;
  }

  public void setElementType(@NotNull PropertyType propertyType) {
    myElementType = propertyType;
  }

  @NotNull
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @NotNull
  public GradleDslFile getDslFile() {
    return myDslFile;
  }

  @NotNull
  public Collection<GradleReferenceInjection> getResolvedVariables() {
    ImmutableList.Builder<GradleReferenceInjection> resultBuilder = ImmutableList.builder();
    for (GradleDslElement child : getChildren()) {
      resultBuilder.addAll(child.getResolvedVariables());
    }
    return resultBuilder.build();
  }

  /**
   * Creates the {@link PsiElement} by adding this element to the .gradle file.
   *
   * <p>It creates a new {@link PsiElement} only when {@link #getPsiElement()} return {@code null}.
   *
   * <p>Returns the final {@link PsiElement} corresponds to this element or {@code null} when failed to create the
   * {@link PsiElement}.
   */
  @Nullable
  public PsiElement create() {
    return myDslFile.getWriter().createDslElement(this);
  }

  /**
   * Deletes this element and all it's children from the .gradle file.
   */
  protected void delete() {
    for (GradleDslElement element : getChildren()) {
      element.delete();
    }

    this.getDslFile().getWriter().deleteDslElement(this);
  }

  public void setModified(boolean modified) {
    myModified = modified;
    if (myParent != null && modified) {
      myParent.setModified(true);
    }
  }

  public boolean isModified() {
    return myModified;
  }

  /**
   * Returns {@code true} if this element represents a Block element (Ex. android, productFlavors, dependencies etc.),
   * {@code false} otherwise.
   */
  public boolean isBlockElement() {
    return false;
  }

  /**
   * Returns {@code true} if this element represents an element which is insignificant if empty.
   */
  public boolean isInsignificantIfEmpty() {
    return true;
  }

  @NotNull
  public abstract Collection<GradleDslElement> getChildren();

  public final void applyChanges() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    apply();
    setModified(false);
  }

  protected abstract void apply();

  public final void resetState() {
    reset();
    setModified(false);
  }

  protected abstract void reset();

  /**
   * Computes a list of properties and variables that are declared or assigned to in this scope.
   * Override in subclasses to return meaningful values.
   */
  public List<GradleDslElement> getContainedElements(boolean includeProperties) {
    return Collections.emptyList();
  }

  /**
   * Computes a list of properties and variables that are visible from this GradleDslElement.
   */
  public Map<String, GradleDslElement> getInScopeElements() {
    Map<String, GradleDslElement> results = new LinkedHashMap<>();

    if (this instanceof GradlePropertiesDslElement) {
      GradlePropertiesDslElement thisElement = (GradlePropertiesDslElement)this;
      results.putAll(thisElement.getVariableElements());
    }

    // Trace parents finding any variable elements present.
    GradleDslElement currentElement = this;
    while (currentElement != null && currentElement.getParent() != null) {
      currentElement = currentElement.getParent();
      if (currentElement instanceof GradlePropertiesDslElement) {
        GradlePropertiesDslElement element = (GradlePropertiesDslElement)currentElement;
        results.putAll(element.getVariableElements());
      }
    }

    // Get Ext properties from the GradleDslFile.
    if (currentElement instanceof GradleDslFile) {
      GradleDslFile file = (GradleDslFile)currentElement;
      while (file != null) {
        ExtDslElement ext = file.getPropertyElement(EXT_BLOCK_NAME, ExtDslElement.class);
        if (ext != null) {
          results.putAll(ext.getPropertyElements());
        }
        // Add properties file properties if it exists.
        GradleDslFile propertiesFile = file.getSiblingDslFile();
        if (propertiesFile != null) {
          results.putAll(propertiesFile.getPropertyElements());
        }
        file = file.getParentModuleDslFile();
      }
    }

    return results;
  }
}
