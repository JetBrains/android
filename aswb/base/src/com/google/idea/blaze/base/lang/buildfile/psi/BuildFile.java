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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import icons.BlazeIcons;
import java.io.File;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Build file PSI element */
public class BuildFile extends PsiFileBase implements BuildElement, DocStringOwner {

  /** The blaze file type */
  public enum BlazeFileType {
    SkylarkExtension,
    BuildPackage, // "BUILD", "BUILD.bazel"
    Workspace, // the top-level WORKSPACE file
    MODULE, // the top-level MODULE.bazel file
  }

  public static String getBuildFileString(Project project, String filePath) {
    Label label = WorkspaceHelper.getBuildLabel(project, new File(filePath));
    if (label == null) {
      return "BUILD file: " + filePath;
    }
    String labelString = label.toString();
    return labelString.replace(":__pkg__", "/" + PathUtil.getFileName(filePath));
  }

  public BuildFile(FileViewProvider viewProvider) {
    super(viewProvider, BuildFileType.INSTANCE.getLanguage());
  }

  @Override
  public FileType getFileType() {
    return BuildFileType.INSTANCE;
  }

  public BlazeFileType getBlazeFileType() {
    String fileName = getFileName();
    switch (fileName) {
      case "BUILD":
        return BlazeFileType.BuildPackage;
      case "WORKSPACE":
        return BlazeFileType.Workspace;
      case "MODULE.bazel":
        return BlazeFileType.MODULE;
      case "SkylarkExtension":
        return BlazeFileType.SkylarkExtension;
    }
    if (fileName.startsWith("BUILD")) {
      return BlazeFileType.BuildPackage;
    }
    if (fileName.startsWith("WORKSPACE")) {
      return BlazeFileType.Workspace;
    }
    if (fileName.equals("MODULE.bazel")) {
      return BlazeFileType.MODULE;
    }
    return BlazeFileType.SkylarkExtension;
  }

  @Nullable
  @Override
  public StringLiteral getDocString() {
    if (getBlazeFileType() != BlazeFileType.SkylarkExtension) {
      return null;
    }
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof StringLiteral
          && ((StringLiteral) cur).getQuoteType() == QuoteType.TripleDouble) {
        return (StringLiteral) cur;
      }
      if (cur instanceof BuildElement) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public BlazePackage getBlazePackage() {
    return BlazePackage.getContainingPackage(this);
  }

  public String getFileName() {
    return getViewProvider().getVirtualFile().getName();
  }

  public String getFilePath() {
    return getOriginalFile().getViewProvider().getVirtualFile().getPath();
  }

  public File getFile() {
    return new File(getFilePath());
  }

  /**
   * The label of the containing blaze package (this is always the parent directory for BUILD files,
   * but may be a more distant ancestor for Skylark extensions)
   */
  @Nullable
  public Label getPackageLabel() {
    BlazePackage parentPackage = getBlazePackage();
    return parentPackage != null ? parentPackage.getPackageLabel() : null;
  }

  /** The path for this file, formatted as a BUILD label. */
  @Nullable
  public Label getBuildLabel() {
    BlazePackage containingPackage = getBlazePackage();
    return containingPackage != null
        ? containingPackage.getBuildLabelForChild(getFilePath())
        : null;
  }

  /** Finds a top-level rule with a "name" keyword argument with the given value. */
  @Nullable
  public FuncallExpression findRule(String name) {
    for (FuncallExpression expr : findChildrenByClass(FuncallExpression.class)) {
      String ruleName = expr.getNameArgumentValue();
      if (name.equals(ruleName)) {
        return expr;
      }
    }
    return null;
  }

  @Nullable
  public FunctionStatement findDeclaredFunction(String name) {
    for (FunctionStatement fn : getFunctionDeclarations()) {
      if (name.equals(fn.getName())) {
        return fn;
      }
    }
    return null;
  }

  @Nullable
  public FunctionStatement findLoadedFunction(String name) {
    for (LoadStatement loadStatement : findChildrenByClass(LoadStatement.class)) {
      for (LoadedSymbol loadedSymbol : loadStatement.getImportedSymbolElements()) {
        if (name.equals(loadedSymbol.getSymbolString())) {
          PsiElement element = loadedSymbol.getLoadedElement();
          return element instanceof FunctionStatement ? (FunctionStatement) element : null;
        }
      }
    }
    return null;
  }

  public BuildElement findSymbolInScope(String name) {
    BuildElement[] resultHolder = new BuildElement[1];
    Processor<BuildElement> processor =
        buildElement -> {
          if (buildElement instanceof LoadedSymbol) {
            buildElement =
                BuildElement.asBuildElement(((LoadedSymbol) buildElement).getVisibleElement());
          }
          if (buildElement instanceof PsiNamedElement && name.equals(buildElement.getName())) {
            resultHolder[0] = buildElement;
            return false;
          }
          return true;
        };
    searchSymbolsInScope(processor, null);
    return resultHolder[0];
  }

  /**
   * Iterates over all top-level assignment statements, function definitions and loaded symbols.
   *
   * @return false if searching was stopped (e.g. because the desired element was found).
   */
  public boolean searchSymbolsInScope(
      Processor<BuildElement> processor, @Nullable PsiElement stopAtElement) {
    for (BuildElement child : findChildrenByClass(BuildElement.class)) {
      if (child == stopAtElement) {
        break;
      }
      if (child instanceof AssignmentStatement) {
        TargetExpression target = ((AssignmentStatement) child).getLeftHandSideExpression();
        if (target != null && !processor.process(target)) {
          return false;
        }
      } else if (child instanceof FunctionStatement) {
        if (!processor.process(child)) {
          return false;
        }
      }
    }
    // search nested load statements last (breadth-first search)
    for (BuildElement child : findChildrenByClass(BuildElement.class)) {
      if (child == stopAtElement) {
        break;
      }
      if (child instanceof LoadStatement) {
        for (LoadedSymbol importedSymbol : ((LoadStatement) child).getImportedSymbolElements()) {
          if (!processor.process(importedSymbol)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /** Searches functions declared in this file, then loaded Skylark extensions, if relevant. */
  @Nullable
  public FunctionStatement findFunctionInScope(String name) {
    FunctionStatement localFn = findDeclaredFunction(name);
    if (localFn != null) {
      return localFn;
    }
    return findLoadedFunction(name);
  }

  public FunctionStatement[] getFunctionDeclarations() {
    return findChildrenByClass(FunctionStatement.class);
  }

  @Override
  public Icon getIcon(int flags) {
    return BlazeIcons.BuildFile;
  }

  @Override
  public String getPresentableText() {
    return toString();
  }

  @Override
  public ItemPresentation getPresentation() {
    final BuildFile element = this;
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return element.getName();
      }

      @Override
      public String getLocationString() {
        String label = getBuildFileString(element.getProject(), element.getFilePath());
        return String.format("(%s)", label);
      }

      @Override
      public Icon getIcon(boolean unused) {
        return element.getIcon(0);
      }
    };
  }

  @Override
  public String toString() {
    return getBuildFileString(getProject(), getFilePath());
  }

  @Nullable
  @Override
  public PsiElement getReferencedElement() {
    return null;
  }

  @Override
  public <P extends PsiElement> P[] childrenOfClass(Class<P> psiClass) {
    return findChildrenByClass(psiClass);
  }

  @Override
  public <P extends PsiElement> P firstChildOfClass(Class<P> psiClass) {
    return findChildByClass(psiClass);
  }
}
