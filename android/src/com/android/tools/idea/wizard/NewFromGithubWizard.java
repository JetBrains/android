/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.tools.idea.actions.AndroidImportModuleAction;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.templates.github.DownloadUtil;
import com.intellij.platform.templates.github.Outcome;
import com.intellij.platform.templates.github.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;

/**
 * A wizard to create a new object (either by inflating a template or by instantiating the object
 * directly) from a Github repository. This wizard will clone to the Github repository to the local
 * disk and then scan it for templates and samples.
 */
public class NewFromGithubWizard extends TemplateWizard implements TemplateWizardStep.UpdateListener {
  private static final Logger LOG = Logger.getInstance(NewFromGithubWizard.class);
  private static final String WIZARD_TITLE = "From GitHub";
  private static final long TIMEOUT = 60 * 60 * 1000; // 1 Hour's worth of Millis

  @Nullable private final VirtualFile myTargetFile;
  private Module myModule;
  private TemplateWizardState myWizardState = new TemplateWizardState();

  private ChooseGithubRepositoryStep myChooseGithubRepositoryStep;


  public NewFromGithubWizard(@Nullable Project project, @Nullable Module module, @Nullable VirtualFile targetFile) {
    super("From GitHub", project);
    myModule = module;
    myTargetFile = targetFile;
    init();
  }

  @Override
  protected void init() {
    myChooseGithubRepositoryStep = new ChooseGithubRepositoryStep(myWizardState, myProject, myModule, this);
    mySteps.add(0, myChooseGithubRepositoryStep);
    super.init();
  }


  /**
   * Container for results obtained by an attempt to download a repository from Github.
   */
  public static class GithubRepoContents {
    List<File> templateFolders;
    List<File> sampleRoots;
    File rootFolder;
    String errorMessage;
  }

  /**
   * Download a Github repository and search it for templates and Android Studio projects. Returns 2 lists of files, one pointing
   * to directories that contain templates and the other to directories that contain projects.
   * @param url The github URL to retrieve
   * @param branch The name of the branch to retrieve. If not specified, it is assumed to be "master"
   * @param cacheDirectory An optional location to cache the downloaded repository. If not specified it will default to a working
   *                       directory inside the operating system's temporary folder (e.g. /tmp)
   * @return A GithubRepoContents instance. If the download failed, then the errorMessage member will be set. If the errorMessage
   * is null, then both templateFolders and sampleRoots will NOT be null, but MAY be empty.
   */
  @NotNull
  public static GithubRepoContents downloadGithubRepo(@NotNull Project project, @NotNull String url, @Nullable String branch,
                                                      @Nullable File cacheDirectory) {
    GithubRepoContents returnValue = new GithubRepoContents();
    if (cacheDirectory == null) {
      cacheDirectory = new File(FileUtil.getTempDirectory(), "github_cache");
    }
    if (branch == null || branch.trim().isEmpty()) {
      branch = "master";
    }

    URL parsedUrl;
    try {
      parsedUrl = new URL(url);
    }
    catch (MalformedURLException e) {
      returnValue.errorMessage = "Malformed URL";
      return returnValue;
    }

    String repositoryName = "Github" + parsedUrl.getPath().replace('/', '-');
    final File outputFile = new File(cacheDirectory, repositoryName + ".zip");
    final String finalUrl = url + "/zipball/" + branch;
    File unzippedDir = new File(cacheDirectory, repositoryName);

    // TODO: Do some smarter caching here
    if (!unzippedDir.exists() || unzippedDir.lastModified() == 0 ||
        (System.currentTimeMillis() - unzippedDir.lastModified()) > TIMEOUT) {
      FileUtil.delete(unzippedDir);

      try {
        Outcome<File> outcome = DownloadUtil.provideDataWithProgressSynchronously(
          project,
          "Downloading project from GitHub",
          "Downloading zip archive" + DownloadUtil.CONTENT_LENGTH_TEMPLATE + " ...",
          new Callable<File>() {
            @Override
            public File call() throws Exception {
              ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
              DownloadUtil.downloadAtomically(progress, finalUrl, outputFile);
              return outputFile;
            }
          }, null
        );

        Exception e = outcome.getException();
        if (e != null) {
          throw e;
        }
        ZipUtil.unzip(ProgressManager.getInstance().getProgressIndicator(), unzippedDir, outputFile, null, null, true);
      }
      catch (Exception e) {
        returnValue.errorMessage = "Could not download specified project from Github. Check the URL and branchname.\n\n" + e.getMessage();

        return returnValue;
      }
      outputFile.delete();
    }
    /**
     * We don't know beforehand what kind of repo we just downloaded.
     * Our return value is a thin wrapper around 2 lists, one of files which point to the root
     * folder of samples, one of files which point to the root folder of templates.
     */
    returnValue.rootFolder = unzippedDir;
    returnValue.templateFolders = TemplateManager.getTemplatesFromDirectory(unzippedDir, true);
    returnValue.sampleRoots = findSamplesInDirectory(unzippedDir, true);
    return returnValue;
  }


  /**
   * Returns a list of files which point to the roots of projects/modules within the given folder.
   * If the recursive parameter is not set, we will only check to see if the given directory is a gradle based project and return
   * a singleton list. Otherwise, we recurse down, finding all project/module roots (folders containing a build.gradle file) and return
   * them as a list.
   * @param directory The root directory to check
   * @param recursive Whether we should look several levels down.
   * @return A list of root directories of projects and/or modules
   */
  @NotNull
  public static List<File> findSamplesInDirectory(@NotNull File directory, boolean recursive) {
    List<File> samples = Lists.newArrayList();
    if (new File(directory, FN_BUILD_GRADLE).exists() || new File(directory, FN_SETTINGS_GRADLE).exists()) {
      samples.add(directory);
    }
    if (recursive) {
      File[] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            samples.addAll(findSamplesInDirectory(file, true));
          }
        }
      }
    }
    return samples;
  }

  /**
   * Launch the NewTemplateObjectWizard to instantiate a template contained in the downloaded repository described by
   * the given GithubRepoContents.
   * @return null if the given repository is valid and contains at least one template, an error message otherwise.
   */
  @Nullable
  public static String runTemplateWizard(@Nullable Project project, @Nullable Module module, @Nullable VirtualFile targetLocation,
                                          @NotNull GithubRepoContents githubRepoContents) {
    if (githubRepoContents.templateFolders == null || githubRepoContents.templateFolders.isEmpty()) {
      String error;
      if (githubRepoContents.errorMessage != null) {
        error = githubRepoContents.errorMessage;
      } else {
        error = "No templates found. Please check the repository that you are attempting to import from.";
      }
      return error;
    }
    NewTemplateObjectWizard wizard;
    if (githubRepoContents.templateFolders.size() == 1) {
      wizard = new NewTemplateObjectWizard(project, module, targetLocation, WIZARD_TITLE, githubRepoContents.templateFolders.get(0));
    } else {
      wizard = new NewTemplateObjectWizard(project, module, targetLocation, WIZARD_TITLE, githubRepoContents.templateFolders);
    }
    wizard.show();
    if (wizard.isOK()) {
      wizard.createTemplateObject();
    }
    return null;
  }

  /**
   * Launch the ImportModuleWizard to import a module contained in the downloaded repository described by
   * the given GithubRepoContents.
   * @return null if the given repository is valid and contains at least one sample, an error message otherwise.
   */
  @Nullable
  private static String runImportWizard(Project project, Module module, GithubRepoContents githubRepoContents) {
    if (githubRepoContents.sampleRoots == null || githubRepoContents.sampleRoots.isEmpty()) {
      String error;
      if (githubRepoContents.errorMessage != null) {
        error = githubRepoContents.errorMessage;
      } else {
        error = "No importable samples found. Please check the repository that you are attempting to import from.";
      }
      return error;
    }
    VirtualFile sourceFolder;
    if (githubRepoContents.sampleRoots.size() == 1) {
      sourceFolder = VfsUtil.findFileByIoFile(githubRepoContents.rootFolder, false);
    } else {
      ChooseFromFileListDialog dialog = new ChooseFromFileListDialog(project, githubRepoContents.sampleRoots);
      dialog.show();
      if (dialog.isOK()) {
        sourceFolder = VfsUtil.findFileByIoFile(dialog.getChosenFile(), false);
      } else {
        return null;
      }
    }
    if (sourceFolder == null) {
      return "No file selected";
    }
    try {
      AndroidImportModuleAction.importGradleSubprojectAsModule(sourceFolder, project);
    }
    catch (IOException e) {
      return "An error occurred while importing a sample from github: " + e.getMessage();
    }
    return null;
  }

  private void setErrorHtml(String s) {
    ((TemplateWizardStep)mySteps.get(getCurrentStep())).setErrorHtml(s);
  }

  @Override
  protected void doOKAction() {
    GithubRepoContents downloadResult = downloadGithubRepo(myProject, myChooseGithubRepositoryStep.getUrl(),
                                                           myChooseGithubRepositoryStep.getBranch(), null);
    if (downloadResult.errorMessage != null) {
      setErrorHtml(downloadResult.errorMessage);
      return;
    }

    // Should never occur
    assert downloadResult.templateFolders != null && downloadResult.sampleRoots != null;
    super.doOKAction();
    String error;
    if (!downloadResult.templateFolders.isEmpty()) {
      error = runTemplateWizard(myProject, myModule, myTargetFile, downloadResult);
    } else {
      error = runImportWizard(myProject, myModule, downloadResult);
    }
    if (error != null) {
      LOG.error(error);
    }
  }
}
