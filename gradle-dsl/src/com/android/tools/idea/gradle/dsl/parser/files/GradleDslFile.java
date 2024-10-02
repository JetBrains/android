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

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.GradleDslTransformerFactory;
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter;
import com.android.tools.idea.gradle.dsl.parser.elements.ElementState;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementEnum;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslGlobalValue;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides Gradle-specific abstraction over Gradle build and related files, whatever their implementation language.
 */
public abstract class GradleDslFile extends GradlePropertiesDslElement {
  private static final Logger LOG = Logger.getInstance(GradleDslFile.class);

  @NotNull protected final ElementList myGlobalProperties = new ElementList();
  @NotNull private final VirtualFile myFile;
  @NotNull private final Project myProject;
  @NotNull private final GradleDslWriter myGradleDslWriter;
  @NotNull protected final GradleDslParser myGradleDslParser;
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

    List<GradleDslTransformerFactory> factories = GradleDslTransformerFactory.EXTENSION_POINT_NAME.getExtensionList();
    boolean foundFactory = false;

    // If we don't support the language we ignore the PsiElement and set stubs for the writer and parser.
    // This means this file will produce an empty model.
    @NotNull GradleDslParser dslParser = new GradleDslParser.Adapter(context);
    @NotNull GradleDslWriter dslWriter = new GradleDslWriter.Adapter(context);
    // Search for something that does support the build language.
    if (psiFile == null) {
      LOG.debug("Failed to find psiFile for virtualFile " + myFile.getName());
    }
    else {
      for (GradleDslTransformerFactory factory : factories) {
        if (factory.canTransform(psiFile)) {
          dslParser = factory.createParser(psiFile, context, this);
          dslWriter = factory.createWriter(context);
          setPsiElement(psiFile);
          foundFactory = true;
          break;
        }
      }
      if (!foundFactory) {
        LOG.debug("Failed to find transformer for file " + psiFile.getName() + " (" + psiFile.getClass().getCanonicalName() + ")");
      }
    }

    myGradleDslWriter = dslWriter;
    myGradleDslParser = dslParser;
    populateGlobalProperties();
  }

  private void populateGlobalProperties() {
    GradleDslElement rootDir =
      new GradleDslGlobalValue(this, new File(FileUtil.toCanonicalPath(Optional.ofNullable(myProject.getBasePath()).orElse(""))).getPath(), "rootDir");
    myGlobalProperties.addElement(rootDir, ElementState.DEFAULT, false);
    GradleDslElement projectDir = new GradleDslGlobalValue(this, getDirectoryPath().getPath(), "projectDir");
    myGlobalProperties.addElement(projectDir, ElementState.DEFAULT, false);

    // org.gradle.api.JavaVersion
    ImmutableMap.Builder<String,String> builder = ImmutableMap.builder();
    Arrays.asList("1_1", "1_2", "1_3", "1_4", "1_5", "1_6", "1_7", "1_8", "1_9", "1_10", "11", "12", "13", "HIGHER")
      .forEach(s -> builder.put("VERSION_" + s, "JavaVersion.VERSION_" + s));
    Map<String,String> javaVersionValues = builder.build();
    GradleDslElement javaVersion = new GradleDslElementEnum(this, GradleNameElement.fake("JavaVersion"), javaVersionValues);
    myGlobalProperties.addElement(javaVersion, ElementState.DEFAULT, false);
  }

  @Override
  protected GradleDslElement getElementWhere(@NotNull Predicate<ElementList.ElementItem> predicate) {
    GradleDslElement result = super.getElementWhere(predicate);
    if (result == null) {
      result = myGlobalProperties.getElementWhere(predicate);
    }
    return result;
  }

  @Override
  protected GradleDslElement getElementBeforeChildWhere(Predicate<ElementList.ElementItem> predicate,
                                                        @NotNull GradleDslElement element,
                                                        boolean includeSelf) {
    GradleDslElement result = super.getElementBeforeChildWhere(predicate, element, includeSelf);
    if (result == null) {
      result = myGlobalProperties.getElementBeforeChildWhere(predicate, element, includeSelf);
    }
    return result;
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
    // we might have textually-forward references that are nevertheless valid because of the prioritization of the buildscript block:
    // attempt resolution once more after the whole of the file is parsed.
    getContext().getDependencyManager().resolveAllIn(this, true);
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
  @NotNull
  public List<BuildModelNotification> getPublicNotifications() {
    return myBuildModelContext.getPublicNotifications(this);
  }

  public void saveAllChanges() {
    PsiElement element = getPsiElement();
    // Properties files do not have PsiElements.
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
    // Saving can alter the document, for example if any trailing spaces were present and were removed on save.
    if (!psiDocumentManager.isCommitted(document)) {
      psiDocumentManager.commitDocument(document);
    }
  }

  @Nullable
  public VirtualFile tryToFindSettingsFile() {
    if (this instanceof GradleSettingsFile) {
      return getFile();
    }

    VirtualFile buildFileParent = getFile().getParent();
    while (buildFileParent != null) {
      VirtualFile maybeSettingsFile = myBuildModelContext.getGradleSettingsFile(virtualToIoFile(buildFileParent));
      if (maybeSettingsFile != null) {
        return maybeSettingsFile;
      }
      buildFileParent = buildFileParent.getParent();
    }
    return null;
  }
}
