/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.adapters;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.command.impl.DummyProject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MXmlAttribute implements XmlAttribute {
  MTag.Attribute myAttribute;
  MXmlAttribute(MTag.Attribute value){
    myAttribute = value;
  }

  @NotNull
  @Override
  public String getName() {
    return myAttribute.mAttribute;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    return null;
  }

  @NotNull
  @Override
  public String getLocalName() {
    return  myAttribute.mAttribute;
  }

  @Override
  public XmlElement getNameElement() {
    return null;
  }

  @NotNull
  @Override
  public String getNamespace() {
    return myAttribute.mNamespace;
  }

  @NotNull
  @Override
  public String getNamespacePrefix() {
    return myAttribute.mNamespace;
  }

  @NotNull
  @Override
  public Project getProject() throws PsiInvalidElementAccessException {
    return DummyProject.getInstance();
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return null;
  }

  @Override
  public PsiManager getManager() {
    return null;
  }

  @NotNull
  @Override
  public PsiElement[] getChildren() {
    return new PsiElement[0];
  }

  @Override
  public XmlTag getParent() {
    return null;
  }

  @Override
  public PsiElement getFirstChild() {
    return null;
  }

  @Override
  public PsiElement getLastChild() {
    return null;
  }

  @Override
  public PsiElement getNextSibling() {
    return null;
  }

  @Override
  public PsiElement getPrevSibling() {
    return null;
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return null;
  }

  @Override
  public TextRange getTextRange() {
    return null;
  }

  @Override
  public int getStartOffsetInParent() {
    return 0;
  }

  @Override
  public int getTextLength() {
    return 0;
  }

  @Nullable
  @Override
  public PsiElement findElementAt(int offset) {
    return null;
  }

  @Nullable
  @Override
  public PsiReference findReferenceAt(int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return 0;
  }

  @Override
  public String getText() {
    return null;
  }

  @NotNull
  @Override
  public char[] textToCharArray() {
    return new char[0];
  }

  @Override
  public PsiElement getNavigationElement() {
    return null;
  }

  @Override
  public PsiElement getOriginalElement() {
    return null;
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public boolean textContains(char c) {
    return false;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {

  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {

  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return null;
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {

  }

  @Override
  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    return null;
  }

  @Override
  public void delete() throws IncorrectOperationException {

  }

  @Override
  public void checkDelete() throws IncorrectOperationException {

  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {

  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Nullable
  @Override
  public PsiReference getReference() {
    return null;
  }

  @NotNull
  @Override
  public PsiReference[] getReferences() {
    return new PsiReference[0];
  }

  @Nullable
  @Override
  public <T> T getCopyableUserData(Key<T> key) {
    return null;
  }

  @Override
  public <T> void putCopyableUserData(Key<T> key, @Nullable T value) {

  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return false;
  }

  @Nullable
  @Override
  public PsiElement getContext() {
    return null;
  }

  @Override
  public boolean isPhysical() {
    return false;
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return null;
  }

  @Override
  public ASTNode getNode() {
    return null;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return false;
  }

  @Nullable
  @Override
  public String getValue() {
    return myAttribute.mValue;
  }

  @Nullable
  @Override
  public String getDisplayValue() {
    return null;
  }

  @Override
  public int physicalToDisplay(int offset) {
    return 0;
  }

  @Override
  public int displayToPhysical(int offset) {
    return 0;
  }

  @NotNull
  @Override
  public TextRange getValueTextRange() {
    return null;
  }

  @Override
  public boolean isNamespaceDeclaration() {
    return false;
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getDescriptor() {
    return null;
  }

  @Nullable
  @Override
  public XmlAttributeValue getValueElement() {
    return null;
  }

  @Override
  public void setValue(@NotNull String value) throws IncorrectOperationException {
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return false;
  }

  @Override
  public Icon getIcon(int flags) {
    return null;
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {

  }
}
