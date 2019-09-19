/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.dom.wrappers;

import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FileResourceElementWrapper implements PsiFile, ResourceElementWrapper, PsiElementNavigationItem {
  @NotNull private final PsiFile myWrappedElement;
  @Nullable private final PsiDirectory myResourceDir;

  public FileResourceElementWrapper(@NotNull PsiFile wrappeeElement) {
    myWrappedElement = wrappeeElement;
    myResourceDir = getContainingFile().getContainingDirectory();
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
  public VirtualFile getVirtualFile() {
    return myWrappedElement.getVirtualFile();
  }

  @Override
  public PsiDirectory getContainingDirectory() {
    return myWrappedElement.getContainingDirectory();
  }

  @Override
  public boolean isDirectory() {
    return myWrappedElement.isDirectory();
  }

  @Override
  public PsiDirectory getParent() {
    return myWrappedElement.getParent();
  }

  @Override
  public long getModificationStamp() {
    return myWrappedElement.getModificationStamp();
  }

  @Override
  @NotNull
  public PsiFile getOriginalFile() {
    return myWrappedElement.getOriginalFile();
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return myWrappedElement.getFileType();
  }

  @SuppressWarnings("deprecation")
  @Override
  @NotNull
  public PsiFile[] getPsiRoots() {
    return myWrappedElement.getPsiRoots();
  }

  @Override
  @NotNull
  public FileViewProvider getViewProvider() {
    return myWrappedElement.getViewProvider();
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
  public FileASTNode getNode() {
    return myWrappedElement.getNode();
  }

  @Override
  public void subtreeChanged() {
    myWrappedElement.subtreeChanged();
  }

  @NonNls
  public String toString() {
    return myWrappedElement.toString();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (another instanceof FileResourceElementWrapper) {
      another = ((FileResourceElementWrapper)another).getWrappedElement();
    }
    return myWrappedElement == another || myWrappedElement.isEquivalentTo(another);
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
  @NotNull
  public String getName() {
    return myWrappedElement.getName();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return myWrappedElement.setName(name);
  }

  @Override
  public boolean processChildren(@NotNull PsiElementProcessor<PsiFileSystemItem> processor) {
    return myWrappedElement.processChildren(processor);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        String name = myWrappedElement.getName();
        if (myResourceDir == null) {
          return name;
        }
        return name + " (" + myResourceDir.getName() + ')';
      }

      @Override
      public String getLocationString() {
        return null;
      }

      @Override
      public Icon getIcon(boolean open) {
        return null;
      }
    };
  }

  @Override
  public void navigate(boolean requestFocus) {
    myWrappedElement.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myWrappedElement.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myWrappedElement.canNavigateToSource();
  }

  @Override
  public void checkSetName(String name) throws IncorrectOperationException {
    myWrappedElement.checkSetName(name);
  }

  @Override
  public PsiElement getTargetElement() {
    return myWrappedElement;
  }
}
