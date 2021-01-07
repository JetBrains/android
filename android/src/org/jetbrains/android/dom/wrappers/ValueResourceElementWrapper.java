// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.dom.wrappers;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import java.io.File;
import javax.swing.*;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ValueResourceElementWrapper implements XmlAttributeValue, ResourceElementWrapper, PsiNamedElement, PsiElementNavigationItem {
  @NotNull private final XmlAttributeValue myWrappedElement;
  @Nullable private final String myFileName;
  @Nullable private final String myDirName;

  public ValueResourceElementWrapper(@NotNull XmlAttributeValue wrappedElement) {
    if (!(wrappedElement instanceof NavigationItem)) {
      throw new IllegalArgumentException();
    }
    if (!(wrappedElement instanceof PsiMetaOwner)) {
      throw new IllegalArgumentException();
    }
    myWrappedElement = wrappedElement;
    final PsiFile file = getContainingFile();
    myFileName = file != null ? file.getName() : null;
    final PsiDirectory dir = file != null ? file.getContainingDirectory() : null;
    myDirName = dir != null ? dir.getName() : null;
  }

  @NotNull
  @Override
  public PsiElement getWrappedElement() {
    return myWrappedElement;
  }

  @Override
  @NotNull
  public Project getProject() throws PsiInvalidElementAccessException {
    return myWrappedElement.getProject();
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return myWrappedElement.getLanguage();
  }

  @Override
  public PsiManager getManager() {
    return myWrappedElement.getManager();
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return myWrappedElement.getChildren();
  }

  @Override
  public PsiElement getParent() {
    return myWrappedElement.getParent();
  }

  @Override
  @Nullable
  public PsiElement getFirstChild() {
    return myWrappedElement.getFirstChild();
  }

  @Override
  @Nullable
  public PsiElement getLastChild() {
    return myWrappedElement.getLastChild();
  }

  @Override
  @Nullable
  public PsiElement getNextSibling() {
    return myWrappedElement.getNextSibling();
  }

  @Override
  @Nullable
  public PsiElement getPrevSibling() {
    return myWrappedElement.getPrevSibling();
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return myWrappedElement.getContainingFile();
  }

  @Override
  public TextRange getTextRange() {
    return myWrappedElement.getTextRange();
  }

  @Override
  public int getStartOffsetInParent() {
    return myWrappedElement.getStartOffsetInParent();
  }

  @Override
  public int getTextLength() {
    return myWrappedElement.getTextLength();
  }

  @Override
  @Nullable
  public PsiElement findElementAt(int offset) {
    return myWrappedElement.findElementAt(offset);
  }

  @Override
  @Nullable
  public PsiReference findReferenceAt(int offset) {
    return myWrappedElement.findReferenceAt(offset);
  }

  @Override
  public int getTextOffset() {
    return myWrappedElement.getTextOffset();
  }

  @Override
  @NonNls
  public String getText() {
    return myWrappedElement.getText();
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
    return myWrappedElement.textToCharArray();
  }

  @Override
  public PsiElement getNavigationElement() {
    return myWrappedElement.getNavigationElement();
  }

  @Override
  public PsiElement getOriginalElement() {
    return myWrappedElement.getOriginalElement();
  }

  @Override
  public boolean textMatches(@NotNull @NonNls CharSequence text) {
    return myWrappedElement.textMatches(text);
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return myWrappedElement.textMatches(element);
  }

  @Override
  public boolean textContains(char c) {
    return myWrappedElement.textContains(c);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    myWrappedElement.accept(visitor);
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    myWrappedElement.acceptChildren(visitor);
  }

  @Override
  public PsiElement copy() {
    return myWrappedElement.copy();
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return myWrappedElement.add(element);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myWrappedElement.addBefore(element, anchor);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myWrappedElement.addAfter(element, anchor);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    myWrappedElement.checkAdd(element);
  }

  @Override
  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return myWrappedElement.addRange(first, last);
  }

  @Override
  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return myWrappedElement.addRangeBefore(first, last, anchor);
  }

  @Override
  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    return myWrappedElement.addRangeAfter(first, last, anchor);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    myWrappedElement.delete();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void checkDelete() throws IncorrectOperationException {
    myWrappedElement.checkDelete();
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    myWrappedElement.deleteChildRange(first, last);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return myWrappedElement.replace(newElement);
  }

  @Override
  public boolean isValid() {
    return myWrappedElement.isValid();
  }

  @Override
  public boolean isWritable() {
    return myWrappedElement.isWritable();
  }

  @Override
  @Nullable
  public PsiReference getReference() {
    return myWrappedElement.getReference();
  }

  @Override
  @NotNull
  public PsiReference[] getReferences() {
    return myWrappedElement.getReferences();
  }

  @Override
  @Nullable
  public <T> T getCopyableUserData(Key<T> key) {
    return myWrappedElement.getCopyableUserData(key);
  }

  @Override
  public <T> void putCopyableUserData(Key<T> key, T value) {
    myWrappedElement.putCopyableUserData(key, value);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return myWrappedElement.processDeclarations(processor, state, lastParent, place);
  }

  @Override
  @Nullable
  public PsiElement getContext() {
    return myWrappedElement.getContext();
  }

  @Override
  public boolean isPhysical() {
    return false;
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myWrappedElement.getResolveScope();
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return myWrappedElement.getUseScope();
  }

  @Override
  @Nullable
  public ASTNode getNode() {
    return myWrappedElement.getNode();
  }

  @NonNls
  public String toString() {
    return myWrappedElement.toString();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (another instanceof ResourceElementWrapper) {
      another = ((ResourceElementWrapper)another).getWrappedElement();
    }
    return myWrappedElement == another || myWrappedElement.isEquivalentTo(another);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ValueResourceElementWrapper that = (ValueResourceElementWrapper)o;

    if (!myWrappedElement.equals(that.myWrappedElement)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myWrappedElement.hashCode();
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myWrappedElement.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myWrappedElement.putUserData(key, value);
  }

  @Override
  public Icon getIcon(int flags) {
    return myWrappedElement.getIcon(flags);
  }

  @Override
  public String getName() {
    String value = myWrappedElement.getValue();
    if (value.startsWith(SdkConstants.NEW_ID_PREFIX)) {
      return IdeResourcesUtil.getResourceNameByReferenceText(value);
    }
    return ((NavigationItem)myWrappedElement).getName();
  }

  @Override
  @Nullable
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    if (IdeResourcesUtil.isIdDeclaration(myWrappedElement)) {
      XmlAttribute attribute = (XmlAttribute)myWrappedElement.getParent();
      attribute.setValue(name);
    }
    else {
      // then it is a value resource
      if (myWrappedElement.isValid()) {
        XmlTag tag = PsiTreeUtil.getParentOfType(myWrappedElement, XmlTag.class);
        DomElement domElement = DomManager.getDomManager(getProject()).getDomElement(tag);
        assert domElement instanceof ResourceElement || domElement instanceof Attr;
        if (domElement instanceof ResourceElement) {
          ResourceElement resElement = (ResourceElement)domElement;
          resElement.getName().setValue(name);
        }
        else {
          Attr attr = (Attr)domElement;
          ResourceReference resourceReference = attr.getName().getValue();
          assert resourceReference != null;
          attr.getName().setValue(new ResourceReference(resourceReference.getNamespace(), resourceReference.getResourceType(), name));
        }
      }
    }
    return null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      @Nullable
      public String getPresentableText() {
        String name = ((NavigationItem)myWrappedElement).getName();
        if (myDirName == null || myFileName == null) {
          return name;
        }
        return name + " (..." + File.separatorChar + myDirName +
               File.separatorChar + myFileName + ')';
      }

      @Override
      public Icon getIcon(boolean open) {
        return null;
      }
    };
  }

  @Override
  public void navigate(boolean requestFocus) {
    ((NavigationItem)myWrappedElement).navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return ((NavigationItem)myWrappedElement).canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return ((NavigationItem)myWrappedElement).canNavigateToSource();
  }

  @NotNull
  @Override
  public String getValue() {
    return myWrappedElement.getValue();
  }

  @Override
  public TextRange getValueTextRange() {
    return myWrappedElement.getValueTextRange();
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return myWrappedElement.processElements(processor, place);
  }

  @Override
  public PsiElement getTargetElement() {
    return getWrappedElement();
  }
}
