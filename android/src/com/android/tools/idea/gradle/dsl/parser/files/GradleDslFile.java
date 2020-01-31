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
package com.android.tools.idea.gradle.dsl.parser.files;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleSettingsFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslParser;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslWriter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslParser;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * Provides Gradle specific abstraction over a {@link GroovyFile}.
 */
public abstract class GradleDslFile extends GradlePropertiesDslElement {
  @NotNull private final VirtualFile myFile;
  @NotNull private final Project myProject;
  @NotNull private final Set<GradleDslFile> myChildModuleDslFiles = Sets.newHashSet();
  @NotNull private final GradleDslWriter myGradleDslWriter;
  @NotNull private final GradleDslParser myGradleDslParser;

  @Nullable private GradleDslFile myParentModuleDslFile;
  @Nullable private GradleDslFile mySiblingDslFile;

  @Nullable private ApplyDslElement myApplyDslElement;
  @NotNull private final BuildModelContext myBuildModelContext;

  protected GradleDslFile(@NotNull VirtualFile file,
                          @NotNull Project project,
                          @NotNull String moduleName,
                          @NotNull BuildModelContext context) {
    super(null, null, GradleNameElement.fake(moduleName));
    myFile = file;
    myProject = project;
    myBuildModelContext = context;

    Application application = ApplicationManager.getApplication();
    PsiFile psiFile = application.runReadAction((Computable<PsiFile>)() -> PsiManager.getInstance(myProject).findFile(myFile));

    // Pick the language that should be used by this GradleDslFile, we do this by selecting the parser implementation.
    if (psiFile instanceof GroovyFile) {
      GroovyFile groovyPsiFile = (GroovyFile)psiFile;
      myGradleDslParser = new GroovyDslParser(groovyPsiFile, this);
      myGradleDslWriter = new GroovyDslWriter();
      setPsiElement(groovyPsiFile);
    }
    else if (psiFile instanceof KtFile && StudioFlags.KOTLIN_DSL_PARSING.get()) {
      KtFile ktFile = (KtFile)psiFile;
      myGradleDslParser = new KotlinDslParser(ktFile, this);
      myGradleDslWriter = new KotlinDslWriter();
      setPsiElement(ktFile);
    }
    else {
      // If we don't support the language we ignore the PsiElement and set stubs for the writer and parser.
      // This means this file will produce an empty model.
      myGradleDslParser = new GradleDslParser.Adapter();
      myGradleDslWriter = new GradleDslWriter.Adapter();
    }
  }

  /**
   * Parses the gradle file again. This is a convenience method when an already parsed gradle file needs to be parsed again
   * (for example, after making changes to the PSI elements.)
   */
  public void reparse() {
    clear();
    parse();
  }

  public void parse() {
    myGradleDslParser.parse();
    // Attempt to resolve all the remaining dependencies. Ideally we would not have to do this here, but when elements
    // are created there parents are not necessarily attached to the tree. This means references to their siblings will not
    // be resolved, for example take:
    //  ext.vars = [
    //    key: "value",
    //    key1: ext.vars.key
    //  ]
    //
    // When key1 is parsed it can't find ext.vars.key. This is a bug with the parser that should be fixed in the future.
    // For now however we call resolveAll() here.
    getContext().getDependencyManager().resolveAll();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public File getDirectoryPath() {
    return virtualToIoFile(getFile().getParent());
  }

  @NotNull
  public List<GradleDslFile> getApplyDslElement() {
    return myApplyDslElement == null ? ImmutableList.of() : myApplyDslElement.getAppliedDslFiles();
  }

  public void setParentModuleDslFile(@NotNull GradleDslFile parentModuleDslFile) {
    myParentModuleDslFile = parentModuleDslFile;
    myParentModuleDslFile.myChildModuleDslFiles.add(this);
  }

  @Nullable
  public GradleDslFile getParentModuleDslFile() {
    return myParentModuleDslFile;
  }

  @NotNull
  public Collection<GradleDslFile> getChildModuleDslFiles() {
    return myChildModuleDslFiles;
  }

  /**
   * Sets the sibling dsl file of this file.
   *
   * <p>build.gradle and gradle.properties files belongs to the same module are considered as sibling files.
   */
  public void setSiblingDslFile(@NotNull GradleDslFile siblingDslFile) {
    mySiblingDslFile = siblingDslFile;
  }

  /**
   * Returns the sibling dsl file of this file.
   *
   * <p>build.gradle and gradle.properties files belongs to the same module are considered as sibling files.
   */
  @Nullable
  public GradleDslFile getSiblingDslFile() {
    return mySiblingDslFile;
  }

  @NotNull
  public GradleDslWriter getWriter() {
    return myGradleDslWriter;
  }

  @NotNull
  public GradleDslParser getParser() {
    return myGradleDslParser;
  }

  @NotNull
  public BuildModelContext getContext() {
    return myBuildModelContext;
  }

  @Override
  protected void apply() {
    // First make sure we update all our applied files.
    if (myApplyDslElement != null) {
      for (GradleDslFile file : myApplyDslElement.getAppliedDslFiles()) {
        file.apply();
      }
    }

    // And update us.
    super.apply();
  }

  public void registerApplyElement(@NotNull ApplyDslElement applyElement) {
    myApplyDslElement = applyElement;
  }

  @NotNull
  public List<BuildModelNotification> getPublicNotifications() {
    return myBuildModelContext.getPublicNotifications(this);
  }

  public void saveAllChanges() {
    PsiElement element = getPsiElement();
    // Properties files to not have PsiElements.
    if (element == null) {
      return;
    }

    // Check for any postponed psi operations and complete them to unblock the underlying document for further modifications.
    assert element instanceof PsiFile;

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
    Document document = psiDocumentManager.getDocument((PsiFile)element);
    if (document == null) {
      return;
    }

    if (psiDocumentManager.isDocumentBlockedByPsi(document)) {
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
    }

    // Save the file to disk to ensure the changes exist when it is read.
    FileDocumentManager.getInstance().saveDocument(document);
  }

  @Nullable
  public VirtualFile tryToFindSettingsFile() {
    if (this instanceof GradleSettingsFile) {
      return getFile();
    }

    VirtualFile buildFileParent = getFile().getParent();
    while (buildFileParent != null) {
      VirtualFile maybeSettingsFile = getGradleSettingsFile(virtualToIoFile(buildFileParent));
      if (maybeSettingsFile != null) {
        return maybeSettingsFile;
      }
      buildFileParent = buildFileParent.getParent();
    }
    return null;
  }
}
