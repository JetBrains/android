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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.lang.buildfile.globbing.UnixGlob;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.Expression;
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.ListLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiPolyVariantCachingReference;
import com.intellij.util.IncorrectOperationException;
import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/** References from a glob to a list of files contained in the same blaze package. */
public class GlobReference extends PsiPolyVariantCachingReference {

  private static final Logger logger = Logger.getInstance(GlobReference.class);

  private final GlobExpression element;

  public GlobReference(GlobExpression element) {
    this.element = element;
  }

  /**
   * Returns true iff the complete, resolved glob references the specified file.
   *
   * <p>In particular, it's not concerned with individual patterns referencing the file, only
   * whether the overall glob does (i.e. returns false if the file is explicitly excluded).
   */
  public boolean matches(String packageRelativePath, boolean isDirectory) {
    if (isDirectory && element.areDirectoriesExcluded()) {
      return false;
    }
    for (String exclude : resolveListContents(element.getExcludes())) {
      if (UnixGlob.matches(exclude, packageRelativePath)) {
        return false;
      }
    }
    for (String include : resolveListContents(element.getIncludes())) {
      if (UnixGlob.matches(include, packageRelativePath)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true iff an include pattern *without wildcards* matches the given path and it's not
   * excluded.
   */
  public boolean matchesDirectly(String packageRelativePath, boolean isDirectory) {
    if (isDirectory && element.areDirectoriesExcluded()) {
      return false;
    }
    for (String exclude : resolveListContents(element.getExcludes())) {
      if (UnixGlob.matches(exclude, packageRelativePath)) {
        return false;
      }
    }
    for (String include : resolveListContents(element.getIncludes())) {
      if (!hasWildcard(include) && UnixGlob.matches(include, packageRelativePath)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasWildcard(String pattern) {
    return pattern.contains("*");
  }

  @Override
  protected ResolveResult[] resolveInner(boolean incompleteCode, PsiFile containingFile) {
    File containingDirectory = ((BuildFile) containingFile).getFile().getParentFile();
    if (containingDirectory == null) {
      return ResolveResult.EMPTY_ARRAY;
    }
    List<String> includes = resolveListContents(element.getIncludes());
    List<String> excludes = resolveListContents(element.getExcludes());
    boolean directoriesExcluded = element.areDirectoriesExcluded();
    if (includes.isEmpty()) {
      return ResolveResult.EMPTY_ARRAY;
    }
    Project project = element.getProject();
    try {
      List<File> files =
          UnixGlob.forPath(containingDirectory)
              .addPatterns(includes)
              .addExcludes(excludes)
              .setExcludeDirectories(directoriesExcluded)
              .setDirectoryFilter(directoryFilter(project, containingDirectory.getPath()))
              .glob();

      List<ResolveResult> results = Lists.newArrayListWithCapacity(files.size());
      for (File file : files) {
        PsiFileSystemItem psiFile = BuildReferenceManager.getInstance(project).resolveFile(file);
        if (psiFile != null) {
          results.add(new PsiElementResolveResult(psiFile));
        }
      }
      return results.toArray(ResolveResult.EMPTY_ARRAY);

    } catch (Exception e) {
      return ResolveResult.EMPTY_ARRAY;
    }
  }

  /** Don't traverse sub-directories which are themselves blaze packages */
  private static Predicate<File> directoryFilter(Project project, String base) {
    BuildSystemProvider provider = Blaze.getBuildSystemProvider(project);
    return file -> {
      if (base.equals(file.getPath())) {
        return true;
      }
      return provider.findBuildFileInDirectory(file) == null;
    };
  }

  private static List<String> resolveListContents(Expression expr) {
    if (expr == null) {
      return ImmutableList.of();
    }
    PsiElement rootElement = PsiUtils.getReferencedTargetValue(expr);
    if (!(rootElement instanceof ListLiteral)) {
      return ImmutableList.of();
    }
    Expression[] children = ((ListLiteral) rootElement).getElements();
    List<String> strings = Lists.newArrayListWithCapacity(children.length);
    for (Expression child : children) {
      if (child instanceof StringLiteral) {
        strings.add(((StringLiteral) child).getStringContents());
      }
    }
    return strings;
  }

  @Override
  public GlobExpression getElement() {
    return element;
  }

  @Override
  public TextRange getRangeInElement() {
    return element.getReferenceTextRange();
  }

  @Override
  public boolean isSoft() {
    return true;
  }

  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  @Override
  public String getCanonicalText() {
    return getValue();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return element;
  }

  @Override
  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return this.element;
  }

  public String getValue() {
    String text = element.getText();
    final TextRange range = getRangeInElement();
    try {
      return range.substring(text);
    } catch (StringIndexOutOfBoundsException e) {
      logger.error(
          "Wrong range in reference " + this + ": " + range + ". Reference text: '" + text + "'",
          e);
      return text;
    }
  }
}
