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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.idea.blaze.base.lang.buildfile.completion.BuildLookupElement;
import com.google.idea.blaze.base.lang.buildfile.completion.LabelRuleLookupElement;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.lang.buildfile.search.ResolveUtil;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import javax.annotation.Nullable;

/** Converts a blaze label into an absolute path, then resolves that path to a PsiElements */
public class LabelReference extends PsiReferenceBase<StringLiteral> {

  public LabelReference(StringLiteral element, boolean soft) {
    super(element, new TextRange(0, element.getTextLength()), soft);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    /* Possibilities:
     * - target
     * - data file (.java, .txt, etc.)
     * - glob contents
     */
    return resolveTarget(myElement.getStringContents());
  }

  @Nullable
  private PsiElement resolveTarget(String labelString) {
    Label label = getLabel(labelString);
    if (label == null) {
      return null;
    }
    if (!validLabelLocation(myElement)) {
      return null;
    }
    if (!LabelUtils.isAbsolute(labelString) && insideSkylarkExtension(myElement)) {
      return getReferenceManager().resolveLabel(label, true);
    }
    return getReferenceManager().resolveLabel(label);
  }

  /**
   * Hack: don't include 'name' keyword arguments -- they'll be a reference to the enclosing
   * function call / rule, and show up as unnecessary references to that rule.
   */
  private static boolean validLabelLocation(StringLiteral element) {
    PsiElement parent = element.getParent();
    if (parent instanceof Argument.Keyword) {
      String argName = ((Argument.Keyword) parent).getName();
      if ("name".equals(argName)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Object[] getVariants() {
    if (!validLabelLocation(myElement)) {
      return EMPTY_ARRAY;
    }
    String labelString = LabelUtils.trimToDummyIdentifier(myElement.getStringContents());
    return ArrayUtil.mergeArrays(getRuleLookups(labelString), getFileLookups(labelString));
  }

  private BuildLookupElement[] getRuleLookups(String labelString) {
    if (labelString.endsWith("/")
        || (labelString.startsWith("/") && !labelString.contains(":"))
        || skylarkExtensionReference(myElement)) {
      return BuildLookupElement.EMPTY_ARRAY;
    }
    String packagePrefix = LabelUtils.getPackagePathComponent(labelString);
    BuildFile referencedBuildFile =
        LabelUtils.getReferencedBuildFile(myElement.getContainingFile(), packagePrefix);
    if (referencedBuildFile == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }
    String self = null;
    if (referencedBuildFile == myElement.getContainingFile()) {
      FuncallExpression funcall =
          PsiUtils.getParentOfType(myElement, FuncallExpression.class, true);
      if (funcall != null) {
        self = funcall.getName();
      }
    }
    return LabelRuleLookupElement.collectAllRules(
        referencedBuildFile, labelString, packagePrefix, self, myElement.getQuoteType());
  }

  private BuildLookupElement[] getFileLookups(String labelString) {
    if (labelString.startsWith("//") || labelString.equals("/")) {
      return getNonLocalFileLookups(labelString);
    }
    return getPackageLocalFileLookups(labelString);
  }

  private BuildLookupElement[] getNonLocalFileLookups(String labelString) {
    BuildLookupElement[] skylarkExtLookups = getSkylarkExtensionLookups(labelString);
    FileLookupData lookupData = FileLookupData.nonLocalFileLookup(labelString, myElement);
    BuildLookupElement[] packageLookups =
        lookupData != null
            ? getReferenceManager().resolvePackageLookupElements(lookupData)
            : BuildLookupElement.EMPTY_ARRAY;
    return ArrayUtil.mergeArrays(skylarkExtLookups, packageLookups);
  }

  private BuildLookupElement[] getPackageLocalFileLookups(String labelString) {
    if (skylarkExtensionReference(myElement)) {
      return getSkylarkExtensionLookups(labelString);
    }
    FileLookupData lookupData = FileLookupData.packageLocalFileLookup(labelString, myElement);
    return lookupData != null
        ? getReferenceManager().resolvePackageLookupElements(lookupData)
        : BuildLookupElement.EMPTY_ARRAY;
  }

  private BuildLookupElement[] getSkylarkExtensionLookups(String labelString) {
    if (!skylarkExtensionReference(myElement)) {
      return BuildLookupElement.EMPTY_ARRAY;
    }
    String packagePrefix = LabelUtils.getPackagePathComponent(labelString);
    BuildFile parentFile = myElement.getContainingFile();
    if (parentFile == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }
    BlazePackage containingPackage = BlazePackage.getContainingPackage(parentFile);
    if (containingPackage == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }
    BuildFile referencedBuildFile =
        LabelUtils.getReferencedBuildFile(containingPackage.buildFile, packagePrefix);

    // Directories before the colon are already covered.
    // We're only concerned with package-local directories.
    boolean hasColon = labelString.indexOf(':') != -1;
    VirtualFileFilter filter =
        file ->
            ("bzl".equals(file.getExtension()) && !file.getPath().equals(parentFile.getFilePath()))
                || (hasColon && file.isDirectory());
    FileLookupData lookupData =
        FileLookupData.packageLocalFileLookup(labelString, myElement, referencedBuildFile, filter);

    return lookupData != null
        ? getReferenceManager().resolvePackageLookupElements(lookupData)
        : BuildLookupElement.EMPTY_ARRAY;
  }

  private BuildReferenceManager getReferenceManager() {
    return BuildReferenceManager.getInstance(myElement.getProject());
  }

  @Override
  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    PsiFile file = ResolveUtil.asFileSearch(element);
    if (file == null) {
      return super.bindToElement(element);
    }
    if (file.equals(resolve())) {
      return myElement;
    }
    BlazePackage currentPackageDir = myElement.getBlazePackage();
    if (currentPackageDir == null) {
      return myElement;
    }
    BlazePackage newPackageDir = BlazePackage.getContainingPackage(file);
    if (!currentPackageDir.equals(newPackageDir)) {
      return myElement;
    }

    String newRuleName =
        newPackageDir.getPackageRelativePath(file.getViewProvider().getVirtualFile().getPath());
    return handleRename(newRuleName);
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    String currentString = myElement.getStringContents();
    Label label = getLabel(currentString);
    if (label == null) {
      return myElement;
    }
    String ruleName = label.targetName().toString();
    String newRuleName = newElementName;

    // handle subdirectories
    int lastSlashIndex = ruleName.lastIndexOf('/');
    if (lastSlashIndex != -1) {
      newRuleName = ruleName.substring(0, lastSlashIndex + 1) + newElementName;
    }

    String packageString = LabelUtils.getPackagePathComponent(currentString);
    if (packageString.isEmpty() && !currentString.contains(":")) {
      return handleRename(newRuleName);
    }
    return handleRename(packageString + ":" + newRuleName);
  }

  private PsiElement handleRename(String newStringContents) {
    ASTNode node = myElement.getNode();
    node.replaceChild(
        node.getFirstChildNode(),
        PsiUtils.createNewLabel(myElement.getProject(), newStringContents));
    return myElement;
  }

  @Nullable
  private Label getLabel(String labelString) {
    if (labelString.indexOf('*') != -1) {
      // don't even try to handle globs, yet.
      return null;
    }
    BlazePackage blazePackage = myElement.getBlazePackage();
    return LabelUtils.createLabelFromString(blazePackage, labelString);
  }

  private static boolean skylarkExtensionReference(StringLiteral element) {
    PsiElement parent = element.getParent();
    if (!(parent instanceof LoadStatement)) {
      return false;
    }
    return ((LoadStatement) parent).getImportPsiElement() == element;
  }

  private static boolean insideSkylarkExtension(StringLiteral element) {
    BuildFile containingFile = element.getContainingFile();
    return containingFile != null
        && containingFile.getBlazeFileType() == BlazeFileType.SkylarkExtension;
  }
}
