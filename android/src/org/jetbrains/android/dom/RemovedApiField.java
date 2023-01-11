/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.dom;

import com.intellij.lang.Language;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import java.util.Locale;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a field in Android public API that was removed.
 */
public class RemovedApiField extends ProvidedDocumentationPsiElement implements PsiNamedElement {
  private static final String API_LEVELS_URL =
      "https://developer.android.com/guide/topics/manifest/uses-sdk-element.html#ApiLevels";

  private final String myName;
  private final PsiClass myContext;
  private final int mySince;
  private final int myDeprecatedIn;
  private final int myRemovedIn;

  public RemovedApiField(@NotNull String name, @NotNull PsiClass context, int since, int deprecatedIn, int removedIn) {
    super(context.getManager(), Language.ANY, name, "");
    myName = name;
    myContext = context;
    mySince = since;
    myDeprecatedIn = deprecatedIn;
    myRemovedIn = removedIn;
  }

  public int getSince() {
    return mySince;
  }

  public int getDeprecatedIn() {
    return myDeprecatedIn;
  }

  public int getRemovedIn() {
    return myRemovedIn;
  }

  @Override
  public boolean isEquivalentTo(PsiElement element) {
    if (element == this) {
      return true;
    }
    if (!(element instanceof RemovedApiField)) {
      return false;
    }
    RemovedApiField other = (RemovedApiField)element;
    return myName.equals(other.myName) && myContext.isEquivalentTo(other.myContext);
  }

  @NotNull
  @Override
  public PsiElement getParent() {
    return myContext;
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myContext.getContainingFile();
  }

  @NotNull
  @Override
  public String getDocumentation() {
    StringBuilder sb = new StringBuilder();
    sb.append("<HTML>\n");
    sb.append(String.format(Locale.US, "<p><b>%s</b>\n", myName));
    sb.append(String.format(Locale.US, "<p>Added in <a href=\"%s\">API level %d</a>\n", API_LEVELS_URL, mySince));
    if (myDeprecatedIn != 0 && myDeprecatedIn < myRemovedIn) {
      sb.append(String.format(Locale.US, "<br>Deprecated in <a href=\"%s\">API level %d</a>\n", API_LEVELS_URL, myDeprecatedIn));
    }
    sb.append(String.format(Locale.US, "<br>Removed in <a href=\"%s\">API level %d</a>\n", API_LEVELS_URL, myRemovedIn));
    sb.append("</HTML>\n");
    return sb.toString();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + myName;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  protected boolean isVisibilitySupported() {
    return super.isVisibilitySupported();
  }

  @Override
  public Icon getElementIcon(int flags) {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    RemovedApiField field = (RemovedApiField)o;

    return myName.equals(field.myName) && myContext.equals(field.myContext)
           && mySince == field.mySince && myDeprecatedIn == field.myDeprecatedIn && myRemovedIn == field.myRemovedIn;
  }

  @Override
  public int hashCode() {
    return 31 * myName.hashCode() + myContext.hashCode();
  }
}
