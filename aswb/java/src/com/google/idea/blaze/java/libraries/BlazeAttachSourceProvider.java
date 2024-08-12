/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.util.Transactions;
import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Supports an 'attach sources' editor butter bar when navigating to .class files with associated
 * source jars.
 *
 * <p>Optionally also attaches sources automatically, on demand.
 */
public class BlazeAttachSourceProvider implements AttachSourcesProvider {

  private static final BoolExperiment attachAutomatically =
      new BoolExperiment("blaze.attach.source.jars.automatically.3", true);

  /* #api223 Use List<? extends LibraryOrderEntry> as parameter. */
  @Override
  public Collection<AttachSourcesAction> getActions(
      List untypedOrderEntries, final PsiFile psiFile) {
    Project project = psiFile.getProject();
    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      return ImmutableList.of();
    }
    List<? extends LibraryOrderEntry> orderEntries =
        (List<? extends LibraryOrderEntry>) untypedOrderEntries;
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    List<BlazeLibrary> librariesToAttachSourceTo = Lists.newArrayList();
    for (LibraryOrderEntry orderEntry : orderEntries) {
      Library library = orderEntry.getLibrary();
      if (library == null) {
        continue;
      }
      String name = library.getName();
      if (name == null) {
        continue;
      }
      LibraryKey libraryKey = LibraryKey.fromIntelliJLibraryName(name);
      if (AttachedSourceJarManager.getInstance(project).hasSourceJarAttached(libraryKey)) {
        continue;
      }
      BlazeJarLibrary blazeLibrary =
          LibraryActionHelper.findLibraryFromIntellijLibrary(project, blazeProjectData, library);
      if (blazeLibrary == null) {
        continue;
      }
      LibraryArtifact libraryArtifact = blazeLibrary.libraryArtifact;
      if (libraryArtifact.getSourceJars().isEmpty()) {
        continue;
      }
      librariesToAttachSourceTo.add(blazeLibrary);
    }

    if (librariesToAttachSourceTo.isEmpty()) {
      return ImmutableList.of();
    }

    // Hack: When sources are requested and we have them, we attach them automatically in the
    // background.
    if (attachAutomatically.getValue()) {
      TransactionGuard.getInstance()
          .submitTransactionLater(
              project,
              () -> {
                attachSources(project, blazeProjectData, librariesToAttachSourceTo);
              });
      return ImmutableList.of();
    }

    return ImmutableList.of(
        new AttachSourcesAction() {
          @Override
          public String getName() {
            return "Attach Blaze Source Jars";
          }

          @Override
          public String getBusyText() {
            return "Attaching source jars...";
          }

          /* #api223 Use List<? extends LibraryOrderEntry> as parameter. */
          @Override
          public ActionCallback perform(List orderEntriesContainingFile) {
            ActionCallback callback =
                new ActionCallback().doWhenDone(() -> navigateToSource(psiFile));
            Transactions.submitTransaction(
                project,
                () -> {
                  attachSources(project, blazeProjectData, librariesToAttachSourceTo);
                  callback.setDone();
                });
            return callback;
          }
        });
  }

  private static void navigateToSource(PsiFile psiFile) {
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              PsiFile psi = refreshPsiFile(psiFile);
              if (psi != null && psi.canNavigate()) {
                psi.navigate(false);
              }
            });
  }

  /** The previous {@link PsiFile} can be invalidated when source jars are attached. */
  @Nullable
  private static PsiFile refreshPsiFile(PsiFile psiFile) {
    return PsiManager.getInstance(psiFile.getProject())
        .findFile(psiFile.getViewProvider().getVirtualFile());
  }

  private static void attachSources(
      Project project,
      BlazeProjectData blazeProjectData,
      Collection<BlazeLibrary> librariesToAttachSourceTo) {
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              IdeModifiableModelsProvider modelsProvider =
                  new IdeModifiableModelsProviderImpl(project);
              for (BlazeLibrary blazeLibrary : librariesToAttachSourceTo) {
                // Make sure we don't do it twice
                if (AttachedSourceJarManager.getInstance(project)
                    .hasSourceJarAttached(blazeLibrary.key)) {
                  continue;
                }
                AttachedSourceJarManager.getInstance(project)
                    .setHasSourceJarAttached(blazeLibrary.key, true);
                LibraryEditor.updateLibrary(
                    project, blazeProjectData, modelsProvider, blazeLibrary);
              }
              modelsProvider.commit();
            });
  }
}
