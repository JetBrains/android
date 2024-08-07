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

import com.google.idea.blaze.base.lang.buildfile.completion.FilePathLookupElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import icons.BlazeIcons;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** The data relevant to finding file lookups. */
public class FileLookupData {

  /** The type of path string format */
  public enum PathFormat {
    /** BUILD label without a leading '//', which can only reference targets in the same package. */
    PackageLocal,
    /** a BUILD label with leading '//', which can reference targets in other packages. */
    NonLocal,
    /** a path string which can reference any files, and has no leading '//'. */
    NonLocalWithoutInitialBackslashes,
    /** a path string referencing only directories, with no leading '//'. */
    NonLocalWithoutInitialBackslashesOnlyDirectories,
  }

  @Nullable
  public static FileLookupData nonLocalFileLookup(String originalLabel, StringLiteral element) {
    return nonLocalFileLookup(
        originalLabel, element.getContainingFile(), element.getQuoteType(), PathFormat.NonLocal);
  }

  @Nullable
  public static FileLookupData nonLocalFileLookup(
      String originalLabel,
      @Nullable BuildFile containingFile,
      QuoteType quoteType,
      PathFormat pathFormat) {
    if (originalLabel.indexOf(':') != -1) {
      // it's a package-local reference
      return null;
    }
    // handle the single '/' case by calling twice.
    String relativePath = StringUtil.trimStart(StringUtil.trimStart(originalLabel, "/"), "/");
    if (relativePath.startsWith("/")) {
      return null;
    }
    boolean onlyDirectories = pathFormat != PathFormat.NonLocalWithoutInitialBackslashes;
    VirtualFileFilter filter = vf -> !onlyDirectories || vf.isDirectory();
    return new FileLookupData(
        originalLabel, containingFile, null, relativePath, pathFormat, quoteType, filter);
  }

  @Nullable
  public static FileLookupData packageLocalFileLookup(String originalLabel, StringLiteral element) {
    if (originalLabel.startsWith("/")) {
      return null;
    }
    BlazePackage blazePackage = element.getBlazePackage();
    BuildFile baseBuildFile = blazePackage != null ? blazePackage.buildFile : null;
    return packageLocalFileLookup(originalLabel, element, baseBuildFile, null);
  }

  @Nullable
  public static FileLookupData packageLocalFileLookup(
      String originalLabel,
      StringLiteral element,
      @Nullable BuildFile basePackage,
      @Nullable VirtualFileFilter fileFilter) {
    if (basePackage == null) {
      return null;
    }
    Label packageLabel = basePackage.getPackageLabel();
    if (packageLabel == null) {
      return null;
    }
    String basePackagePath = packageLabel.blazePackage().relativePath();
    String filePath = basePackagePath + "/" + LabelUtils.getRuleComponent(originalLabel);
    return new FileLookupData(
        originalLabel,
        basePackage,
        basePackagePath,
        filePath,
        PathFormat.PackageLocal,
        element.getQuoteType(),
        fileFilter);
  }

  private final String originalLabel;
  private final BuildFile containingFile;
  @Nullable private final String containingPackage;
  public final String filePathFragment;
  public final PathFormat pathFormat;
  private final QuoteType quoteType;
  @Nullable private final VirtualFileFilter fileFilter;

  private FileLookupData(
      String originalLabel,
      @Nullable BuildFile containingFile,
      @Nullable String containingPackage,
      String filePathFragment,
      PathFormat pathFormat,
      QuoteType quoteType,
      @Nullable VirtualFileFilter fileFilter) {

    this.originalLabel = originalLabel;
    this.containingFile = containingFile;
    this.containingPackage = containingPackage;
    this.fileFilter = fileFilter;
    this.filePathFragment = filePathFragment;
    this.pathFormat = pathFormat;
    this.quoteType = quoteType;

    assert (pathFormat != PathFormat.PackageLocal
        || (containingPackage != null && containingFile != null));
  }

  public boolean acceptFile(Project project, VirtualFile file) {
    if (fileFilter != null && !fileFilter.accept(file)) {
      return false;
    }
    if (pathFormat != PathFormat.PackageLocal) {
      return true;
    }
    if (file.equals(containingFile.getOriginalFile().getVirtualFile())) {
      return false;
    }
    boolean blazePackage =
        Blaze.getBuildSystemProvider(project).findBuildFileInDirectory(file) != null;
    return !blazePackage;
  }

  public FilePathLookupElement lookupElementForFile(
      Project project, VirtualFile file, @Nullable WorkspacePath workspacePath) {
    NullableLazyValue<Icon> icon =
        new NullableLazyValue<Icon>() {
          @Override
          protected Icon compute() {
            if (file.findChild("BUILD") != null) {
              return BlazeIcons.BuildFile;
            }
            if (file.isDirectory()) {
              return PlatformIcons.FOLDER_ICON;
            }
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            return psiFile != null ? psiFile.getIcon(0) : AllIcons.FileTypes.Any_type;
          }
        };
    String fullLabel =
        workspacePath != null ? getFullLabel(workspacePath.relativePath()) : file.getPath();
    String itemText = workspacePath != null ? getItemText(workspacePath.relativePath()) : fullLabel;
    return new FilePathLookupElement(fullLabel, itemText, quoteType, icon);
  }

  private String getFullLabel(String relativePath) {
    if (pathFormat != PathFormat.PackageLocal) {
      if (pathFormat == PathFormat.NonLocal) {
        relativePath = "//" + relativePath;
      }
      return relativePath;
    }
    String prefix;
    int colonIndex = originalLabel.indexOf(':');
    if (originalLabel.startsWith("/")) {
      prefix = colonIndex == -1 ? originalLabel + ":" : originalLabel.substring(0, colonIndex + 1);
    } else {
      prefix = originalLabel.substring(0, colonIndex + 1);
    }
    return prefix + getItemText(relativePath);
  }

  private String getItemText(String relativePath) {
    if (pathFormat == PathFormat.PackageLocal) {
      if (containingPackage.length() > relativePath.length()) {
        return "";
      }
      return StringUtil.trimStart(relativePath.substring(containingPackage.length()), "/");
    }
    String parentPath = PathUtil.getParentPath(relativePath);
    while (!parentPath.isEmpty()) {
      if (filePathFragment.startsWith(parentPath + "/")) {
        return StringUtil.trimStart(relativePath, parentPath + "/");
      } else if (filePathFragment.startsWith(parentPath)) {
        return StringUtil.trimStart(relativePath, parentPath);
      }
      parentPath = PathUtil.getParentPath(parentPath);
    }
    return relativePath;
  }
}
