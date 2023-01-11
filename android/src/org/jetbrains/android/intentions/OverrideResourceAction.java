/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.intentions;

import static com.android.SdkConstants.ATTR_NAME;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.actions.ElementCreator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.android.actions.CreateResourceDirectoryDialog;
import org.jetbrains.android.actions.ElementCreatingValidator;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Action in XML files which lets you override a resource (by creating a new resource in a different resource folder
 * <p>
 * <ul>
 *   <li>Display validation errors when the folder name is invalid</li>
 *   <li>Offer to override the resource in a specific variant?</li>
 *   <li>Offer specific suggestions for folder configurations based on resource type. For example, for a string value
 *   it's probably a locale; for a style it's probably an API version, for a layout it's probably
 *   a screen size or an orientation, and so on.</li>
 * </ul>
 */
public class OverrideResourceAction extends AbstractIntentionAction {
  private static String getActionName(@Nullable String folder) {
    return "Override Resource in " + (folder != null ? folder : "Other Configuration...");
  }

  @NotNull
  @Override
  public String getText() {
    return getActionName(null);
  }

  @Override
  public boolean startInWriteAction() {
    return super.startInWriteAction();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (file instanceof XmlFile && file.isValid() && AndroidFacet.getInstance(file) != null) {
      ResourceFolderType folderType = IdeResourcesUtil.getFolderType(file);
      if (folderType == null) {
        return false;
      } else if (folderType != ResourceFolderType.VALUES) {
        return true;
      } else {
        return isAvailable(getValueTag(editor, file), file);
      }
    }

    return false;
  }

  public boolean isAvailable(@Nullable XmlTag tag, PsiFile file) {
    if (file instanceof XmlFile && file.isValid() && AndroidFacet.getInstance(file) != null) {
      ResourceFolderType folderType = IdeResourcesUtil.getFolderType(file);
      if (folderType == null) {
        return false;
      } else if (folderType != ResourceFolderType.VALUES) {
        return true;
      } else {
        // In value files, you can invoke this action if the caret is on or inside an element (other than the
        // root <resources> tag). Only accept the element if it has a known type with a known name.
        if (tag != null && tag.getAttributeValue(ATTR_NAME) != null) {
          return IdeResourcesUtil.getResourceTypeForResourceTag(tag) != null;
        }
      }
    }

    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    ResourceFolderType folderType = IdeResourcesUtil.getFolderType(file);
    if (folderType == null) {
      // shouldn't happen; we checked in isAvailable
      return;
    }

    if (folderType != ResourceFolderType.VALUES) {
      forkResourceFile((XmlFile)file, null, true);
    } else if (editor != null) {
      forkResourceValue(project, editor, file, null, true);
    }
  }

  private static void forkResourceValue(@NotNull Project project,
                                        @NotNull Editor editor,
                                        @NotNull PsiFile file,
                                        @Nullable PsiDirectory dir,
                                        boolean open) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    XmlTag tag = getValueTag(editor, file);
    if (tag == null) {
      return; // shouldn't happen; we checked in isAvailable
    }
    forkResourceValue(project, tag, file, dir, open);
  }

  @Nullable
  private static PsiDirectory findRes(@NotNull PsiFile file) {
    PsiDirectory resourceFolder = file.getParent();
    return resourceFolder == null ? null : resourceFolder.getParent();
  }

  /**
   * Create a variation (copy) of a given resource.
   * @param project Current project
   * @param tag Resource to be copied
   * @param file File containing the resource
   * @param dir Directory to contain the new resource, or null to ask the user
   * @param open if true, open the file containing the new resource
   */
  public static void forkResourceValue(@NotNull Project project,
                                       @NotNull XmlTag tag,
                                       @NotNull PsiFile file,
                                       @Nullable PsiDirectory dir,
                                       boolean open) {

    PsiDirectory resFolder = findRes(file);
    if (resFolder == null) {
      return; // shouldn't happen; we checked in isAvailable
    }
    String name = tag.getAttributeValue(ATTR_NAME);
    ResourceType type = IdeResourcesUtil.getResourceTypeForResourceTag(tag);
    if (name == null || type == null) {
      return; // shouldn't happen; we checked in isAvailable
    }
    if (dir == null) {
      dir = selectFolderDir(project, resFolder.getVirtualFile(), ResourceFolderType.VALUES);
    }
    if (dir != null) {
      String value = IdeResourcesUtil.getTextContent(tag).trim();
      createValueResource(project, resFolder, file, dir, name, value, type, tag.getText(), open);
    }
  }

  @Nullable
  private static XmlTag getEditorTag(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement psiElement = file.findElementAt(offset);
    if (psiElement != null) {
      return PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
    }
    return null;
  }

  @Nullable
  private static XmlTag getValueTag(Editor editor, PsiFile file) {
    return getValueTag(getEditorTag(editor, file));
  }

  @Nullable
  public static XmlTag getValueTag(@Nullable XmlTag tag) {
    XmlTag current = null;
    if (tag != null) {
      current = tag;
      XmlTag parent = current.getParentTag();
      while (parent != null) {
        XmlTag parentParent = parent.getParentTag();
        if (parentParent == null) {
          break;
        }
        current = parent;
        parent = parentParent;
      }
    }

    return current;
  }

  private static void createValueResource(@NotNull final Project project,
                                          @NotNull final PsiDirectory resDir,
                                          @NotNull PsiFile file,
                                          @NotNull PsiDirectory resourceSubdir,
                                          @NotNull final String resName,
                                          @NotNull final String value,
                                          @NotNull final ResourceType type,
                                          @NotNull final String oldTagText,
                                          boolean open) {
    final String filename = file.getName();
    final List<String> dirNames = Collections.singletonList(resourceSubdir.getName());
    final AtomicReference<PsiElement> openAfter = new AtomicReference<>();
    WriteCommandAction.writeCommandAction(project, file).withName("Override Resource " + resName).run(() -> {
      List<ResourceElement> elements = Lists.newArrayListWithExpectedSize(1);
      // AndroidResourcesIdeUtil.createValueResource will create a new resource value in the given resource
      // folder (and record the corresponding tags added in the elements list passed into it).
      // However, it only creates a new element and sets the name attribute on it; it does not
      // transfer attributes, child content etc. Therefore, we use this utility method first to
      // create the corresponding tag, and then *afterwards* we will replace the tag with a text copy
      // from the resource tag we are overriding. We do this all under a single write lock such
      // that it becomes a single atomic operation.
      IdeResourcesUtil.createValueResource(project, resDir.getVirtualFile(), resName, type, filename, dirNames, value, elements);
      if (elements.size() == 1) {
        final XmlTag tag = elements.get(0).getXmlTag();
        if (tag != null && tag.isValid()) {
          try {
            XmlTag tagFromText = XmlElementFactory.getInstance(tag.getProject()).createTagFromText(oldTagText);
            PsiElement replaced = tag.replace(tagFromText);
            openAfter.set(replaced);
          } catch (IncorrectOperationException e) {
            // The user tried to override an invalid XML fragment: don't attempt to do a replacement in that case
            openAfter.set(tag);
          }
        }
      }
    });
    PsiElement tag = openAfter.get();
    if (open && tag != null) {
      NavigationUtil.openFileWithPsiElement(tag, true, true);
    }
  }

  /**
   * Create a variation (copy) of a given resource file
   *
   * @param surface   the design surface for the resource file to fork
   * @param newFolder the resource folder to create, or null to ask the user
   * @param open      if true, open the file after creating it
   */
  public static void forkResourceFile(@NotNull EditorDesignSurface surface, @Nullable String newFolder, boolean open) {
    for (Configuration configuration : surface.getConfigurations()) {
      if (configuration == null) {
        assert false;
        return; // Should not happen
      }
      final VirtualFile file = configuration.getFile();
      if (file == null) {
        assert false;
        return; // Should not happen
      }
      Module module = configuration.getModule();
      if (module == null) {
        assert false;
        return; // Should not happen
      }
      XmlFile xmlFile = (XmlFile)configuration.getPsiFile();
      ResourceFolderType folderType = IdeResourcesUtil.getFolderType(xmlFile);
      if (folderType == null) {
        folderType = ResourceFolderType.LAYOUT;
      }
      forkResourceFile(module.getProject(), folderType, file, xmlFile, newFolder, configuration, open);
    }
  }

  /**
   * Create a variation (copy) of a given resource file (of a given type).
   *
   * @param xmlFile     the XML resource file to fork
   * @param myNewFolder the resource folder to create, or null to ask the user
   * @param open        if true, open the file after creating it
   */
  public static void forkResourceFile(@NotNull final XmlFile xmlFile, @Nullable String myNewFolder, boolean open) {
    VirtualFile file = xmlFile.getVirtualFile();
    if (file == null) {
      return;
    }
    Module module = AndroidPsiUtils.getModuleSafely(xmlFile);
    if (module == null) {
      return;
    }
    ResourceFolderType folderType = IdeResourcesUtil.getFolderType(xmlFile);
    if (folderType == null || folderType == ResourceFolderType.VALUES) {
      return;
    }
    Configuration configuration = null;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      configuration = ConfigurationManager.getOrCreateInstance(module).getConfiguration(file);
    }

    forkResourceFile(module.getProject(), folderType, file, xmlFile, myNewFolder, configuration, open);
  }

  private static void forkResourceFile(@NotNull Project project,
                                       @NotNull final ResourceFolderType folderType,
                                       @NotNull final VirtualFile file,
                                       @Nullable final XmlFile xmlFile,
                                       @Nullable String myNewFolder,
                                       @Nullable Configuration configuration,
                                       boolean open) {
    final FolderConfiguration folderConfig;
    if (myNewFolder == null) {
      // Open a file chooser to select the configuration to be created
      VirtualFile parentFolder = file.getParent();
      assert parentFolder != null;
      VirtualFile res = parentFolder.getParent();
      folderConfig = selectFolderConfig(project, res, folderType);
    }
    else {
      folderConfig = FolderConfiguration.getConfigForFolder(myNewFolder);
    }
    if (folderConfig == null) {
      return;
    }

    Pair<String, VirtualFile> result = WriteCommandAction.writeCommandAction(project).withName("Add Resource").compute(() -> {
      String folderName = folderConfig.getFolderName(folderType);
      try {
        VirtualFile parentFolder = file.getParent();
        assert parentFolder != null;
        VirtualFile res = parentFolder.getParent();
        VirtualFile newParentFolder = res.findChild(folderName);
        if (newParentFolder == null) {
          try {
            newParentFolder = res.createChildDirectory(res, folderName);
          }
          catch (IncorrectOperationException e) {
            String message = String.format("Could not create folder %1$s in %2$s, Reason:\n%3$s",
                                           folderName, res.getPath(), e.getMessage());
            return Pair.of(message, null);
          }
        }

        final VirtualFile existing = newParentFolder.findChild(file.getName());
        if (existing != null && existing.exists()) {
          String message = String.format("File 'res/%1$s/%2$s' already exists!", folderName, file.getName());
          return Pair.of(message, null);
        }
        // Attempt to get the document from the PSI file rather than the file on disk: get edited contents too
        String text;
        if (xmlFile != null && xmlFile.isValid()) {
          text = xmlFile.getText();
        }
        else {
          text = StreamUtil.readText(file.getInputStream(), "UTF-8");
        }
        VirtualFile newFile = newParentFolder.createChildData(newParentFolder, file.getName());
        VfsUtil.saveText(newFile, text);
        return Pair.of(null, newFile);
      }
      catch (IOException e2) {
        String message = String.format("Failed to create File 'res/%1$s/%2$s' : %3$s", folderName, file.getName(), e2.getMessage());
        return Pair.of(message, null);
      }
    });

    String error = result.getFirst();
    VirtualFile newFile = result.getSecond();
    if (error != null) {
      Messages.showErrorDialog(project, error, "Create Resource");
    }
    else {
      // First create a compatible configuration based on the current configuration
      if (configuration != null) {
        ConfigurationManager configurationManager = configuration.getConfigurationManager();
        configurationManager.createSimilar(newFile, file);
      }

      if (open) {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, newFile, -1);
        FileEditorManager.getInstance(project).openEditor(descriptor, true);
      }
    }
  }

  /** Allow unit tests to pick a folder instead of going through the interactive dialogs */
  @VisibleForTesting
  @Nullable
  public static String ourTargetFolderName;

  @Nullable
  public static PsiDirectory selectFolderDir(final Project project, VirtualFile res, ResourceFolderType folderType) {
    PsiDirectory directory = PsiManager.getInstance(project).findDirectory(res);
    if (directory == null) {
      return null;
    }
    if (ApplicationManager.getApplication().isUnitTestMode() && ourTargetFolderName != null) {
      PsiDirectory subDirectory = directory.findSubdirectory(ourTargetFolderName);
      if (subDirectory != null) {
        return subDirectory;
      }

      Computable<PsiDirectory> createDirComputable = () -> directory.createSubdirectory(ourTargetFolderName);
      return ApplicationManager.getApplication().runWriteAction(createDirComputable);
    }
    CreateResourceDirectoryDialog dialog = new CreateResourceDirectoryDialog(
      project, null, folderType, directory, null,
      resDirectory -> new ResourceDirectorySelector(project, resDirectory), true);
    dialog.setTitle("Select Resource Directory");
    if (!dialog.showAndGet()) {
      return null;
    }
    PsiElement[] createdElements = dialog.getCreatedElements();
    if (createdElements.length > 0) {
      return (PsiDirectory)createdElements[0];
    }

    return null;
  }

  @Nullable
  public static FolderConfiguration selectFolderConfig(final Project project, VirtualFile res, ResourceFolderType folderType) {
    PsiDirectory dir = selectFolderDir(project, res, folderType);
    if (dir != null) {
      return FolderConfiguration.getConfigForFolder(dir.getName());
    }

    return null;
  }

  /**
   * Selects (and optionally creates) a resource directory
   */
  private static class ResourceDirectorySelector extends ElementCreator implements ElementCreatingValidator {
    private final PsiDirectory myDirectory;
    private PsiElement[] myCreatedElements = PsiElement.EMPTY_ARRAY;

    public ResourceDirectorySelector(final Project project, final PsiDirectory directory) {
      super(project, "Select Resource Directory");
      myDirectory = directory;
    }

    @Override
    public boolean checkInput(final String inputString) {
      return ResourceFolderType.getFolderType(inputString) != null && FolderConfiguration.getConfigForFolder(inputString) != null;
    }

    @Override
    public PsiElement @NotNull [] create(@NotNull String newName) throws Exception {
      PsiDirectory subdirectory = myDirectory.findSubdirectory(newName);
      if (subdirectory == null) {
        subdirectory = myDirectory.createSubdirectory(newName);
      }
      return new PsiElement[] { subdirectory };
    }

    @Override
    public @NotNull String getActionName(@NotNull String newName) {
      return "Select Resource Directory";
    }

    @Override
    public boolean canClose(final String inputString) {
      // Already exists: ok
      PsiDirectory subdirectory = myDirectory.findSubdirectory(inputString);
      if (subdirectory != null) {
        myCreatedElements = new PsiDirectory[]{subdirectory};
        return true;
      }
      myCreatedElements = tryCreate(inputString);
      return myCreatedElements.length > 0;
    }

    @NotNull
    @Override
    public final PsiElement[] getCreatedElements() {
      return myCreatedElements;
    }
  }

  /** Create a lint quickfix which overrides the resource at the given {@link PsiElement} */
  public static LintIdeQuickFix createFix(@Nullable String folder) {
    return new OverrideElementFix(folder);
  }

  private static class OverrideElementFix extends DefaultLintQuickFix {
    private final String myFolder;

    private OverrideElementFix(@Nullable String folder) {
      super(getActionName(folder));
      myFolder = folder;
    }

    @Override
    public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
      PsiFile file = startElement.getContainingFile();
      if (file instanceof XmlFile) {
        ResourceFolderType folderType = IdeResourcesUtil.getFolderType(file);
        if (folderType != null) {
          if (folderType != ResourceFolderType.VALUES) {
            forkResourceFile((XmlFile)file, myFolder, true);
          } else {
            XmlTag tag = getValueTag(PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false));
            if (tag != null) {
              PsiDirectory dir = null;
              if (myFolder != null) {
                PsiDirectory resFolder = findRes(file);
                if (resFolder != null) {
                  dir = resFolder.findSubdirectory(myFolder);
                  if (dir == null) {
                    dir = resFolder.createSubdirectory(myFolder);
                  }
                }
              }
              forkResourceValue(startElement.getProject(), tag, file, dir, true);
            }
          }
        }
      }
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement startElement,
                                @NotNull PsiElement endElement,
                                @NotNull AndroidQuickfixContexts.ContextType contextType) {
      return super.isApplicable(startElement, endElement, contextType);
    }
  }
}
