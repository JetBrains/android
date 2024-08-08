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
package com.google.idea.blaze.base.lang.projectview.references;

import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.lang.buildfile.completion.BuildLookupElement;
import com.google.idea.blaze.base.lang.buildfile.completion.LabelRuleLookupElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.lang.buildfile.references.FileLookupData;
import com.google.idea.blaze.base.lang.buildfile.references.FileLookupData.PathFormat;
import com.google.idea.blaze.base.lang.buildfile.references.LabelUtils;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiSectionItem;
import com.google.idea.blaze.base.lang.projectview.psi.util.ProjectViewElementGenerator;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import java.io.File;
import javax.annotation.Nullable;

/** A blaze label reference. */
public class ProjectViewLabelReference extends PsiReferenceBase<ProjectViewPsiSectionItem> {

  private final PathFormat pathFormat;

  public ProjectViewLabelReference(ProjectViewPsiSectionItem element, PathFormat pathFormat) {
    super(element, new TextRange(0, element.getTextLength()));
    this.pathFormat = pathFormat;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    if (pathFormat == PathFormat.NonLocalWithoutInitialBackslashes
        || pathFormat == PathFormat.NonLocalWithoutInitialBackslashesOnlyDirectories) {
      return resolveFile(myElement.getText());
    }
    Label label = getLabel(myElement.getText());
    if (label == null) {
      return null;
    }
    return BuildReferenceManager.getInstance(getProject()).resolveLabel(label);
  }

  @Nullable
  private PsiFileSystemItem resolveFile(String path) {
    if (path.startsWith("/") || path.contains(":")) {
      return null;
    }
    BuildReferenceManager manager = BuildReferenceManager.getInstance(getProject());
    path = StringUtil.trimStart(path, "-");
    File file = manager.resolvePackage(WorkspacePath.createIfValid(path));
    if (file == null) {
      return null;
    }
    VirtualFile vf =
        VirtualFileSystemProvider.getInstance().getSystem().findFileByPath(file.getPath());
    if (vf == null) {
      return null;
    }
    PsiManager psiManager = PsiManager.getInstance(getProject());
    return vf.isDirectory() ? psiManager.findDirectory(vf) : psiManager.findFile(vf);
  }

  @Nullable
  private Label getLabel(@Nullable String labelString) {
    if (labelString == null
        || !labelString.startsWith("//")
        || labelString.contains("...")
        || labelString.indexOf('*') != -1) {
      return null;
    }
    return LabelUtils.createLabelFromString(null, labelString);
  }

  @Override
  public Object[] getVariants() {
    String labelString = LabelUtils.trimToDummyIdentifier(myElement.getText());
    return ArrayUtil.mergeArrays(getRuleLookups(labelString), getFileLookups(labelString));
  }

  private BuildLookupElement[] getRuleLookups(String labelString) {
    if (!labelString.startsWith("//") || !labelString.contains(":")) {
      return BuildLookupElement.EMPTY_ARRAY;
    }
    String packagePrefix = LabelUtils.getPackagePathComponent(labelString);
    BuildFile referencedBuildFile =
        BuildReferenceManager.getInstance(getProject()).resolveBlazePackage(packagePrefix);
    if (referencedBuildFile == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }
    return LabelRuleLookupElement.collectAllRules(
        referencedBuildFile, labelString, packagePrefix, null, QuoteType.NoQuotes);
  }

  private BuildLookupElement[] getFileLookups(String labelString) {
    if (pathFormat == PathFormat.NonLocalWithoutInitialBackslashes
        || pathFormat == PathFormat.NonLocalWithoutInitialBackslashesOnlyDirectories) {
      labelString = StringUtil.trimStart(labelString, "-");
    }
    FileLookupData lookupData =
        FileLookupData.nonLocalFileLookup(labelString, null, QuoteType.NoQuotes, pathFormat);
    if (lookupData == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }
    return BuildReferenceManager.getInstance(getProject()).resolvePackageLookupElements(lookupData);
  }

  @Override
  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return myElement;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    String currentString = myElement.getText();
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
    ASTNode replacement =
        ProjectViewElementGenerator.createReplacementItemNode(myElement, newStringContents);
    if (replacement != null) {
      myElement.getNode().replaceAllChildrenToChildrenOf(replacement);
    }
    return myElement;
  }

  private Project getProject() {
    return myElement.getProject();
  }
}
