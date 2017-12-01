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
package com.android.tools.idea.gradle.dsl.parser.java;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslReference;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.gradle.dsl.parser.java.LanguageLevelUtil.parseFromGradleString;

public class JavaVersionDslElement extends GradleDslElement {
  private GradleDslExpression myInternalVersionElement;
  @Nullable private LanguageLevel myUnsavedValue;

  public JavaVersionDslElement(@Nullable GradleDslElement parent, @NotNull GradleDslExpression dslElement, @NotNull String name) {
    super(parent, null, name);
    assert dslElement instanceof GradleDslLiteral || dslElement instanceof GradleDslReference;
    setUseAssignment(dslElement.shouldUseAssignment());
    myInternalVersionElement = dslElement;
  }

  public JavaVersionDslElement(@Nullable GradleDslElement parent, @NotNull String name, boolean useAssignment) {
    super(parent, null, name);
    setUseAssignment(useAssignment);
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    if (myInternalVersionElement != null) {
      return myInternalVersionElement.getPsiElement();
    }
    return null;
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
    // This class just co-ordinates different notations of Java versions and doesn't represent any real elements on the file.
  }

  /**
   * This method should <b>not</b> be called outside of the GradleDslWriter classes.
   * <p>
   * If you need to change the java version please use {@link #setVersion(LanguageLevel) setVersion}
   * followed by a call to {@link #apply() apply} to ensure the changes are written to the underlying file.
   */
  public void setVersionElement(@NotNull GradleDslExpression element) {
    myInternalVersionElement = element;
  }

  @Nullable
  public LanguageLevel getUnsavedValue() {
    return myUnsavedValue;
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return Collections.emptyList();
  }

  @Nullable
  public LanguageLevel getVersion() {
    if (myUnsavedValue != null) {
      return myUnsavedValue;
    }
    String text = getVersionText();
    if (text != null) {
      return parseFromGradleString(text);
    }
    return null;
  }

  @Nullable
  public String getVersionText() {
    GradleDslLiteral valueLiteral = null;

    if (myInternalVersionElement instanceof GradleDslReference) {
      GradleDslReference referenceElement = (GradleDslReference)myInternalVersionElement;
      valueLiteral = referenceElement.getValue(GradleDslLiteral.class);
      if (valueLiteral == null) {
        String resolvedReference = referenceElement.getValue(String.class);
        if (resolvedReference != null) {
          return resolvedReference;
        }
        else {
          return referenceElement.getReferenceText();
        }
      }
    }

    if (myInternalVersionElement instanceof GradleDslLiteral) {
      valueLiteral = (GradleDslLiteral)myInternalVersionElement;
    }

    if (valueLiteral != null) {
      return valueLiteral.getRawText();
    }

    return null;
  }

  /**
   * Sets new Java version while keeping the original version format.
   */
  public void setVersion(@NotNull LanguageLevel languageLevel) {
    myUnsavedValue = languageLevel;
    setModified(true);
  }

  @Override
  protected void apply() {
    getDslFile().getWriter().applyDslJavaVersionElement(this);
  }

  @Override
  protected void reset() {
    myUnsavedValue = null;
  }

  @Override
  @Nullable
  public PsiElement create() {
    return getDslFile().getWriter().createDslJavaVersionElement(this);
  }

  @Override
  protected void delete() {
    getDslFile().getWriter().deleteDslJavaVersionElement(this);
  }
}
