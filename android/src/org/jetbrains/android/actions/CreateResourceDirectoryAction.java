package org.jetbrains.android.actions;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateResourceDirectoryAction extends CreateResourceActionBase {
  private final ResourceFolderType myResourceFolderType;

  @SuppressWarnings("UnusedDeclaration")
  public CreateResourceDirectoryAction() {
    this(null);
  }

  public CreateResourceDirectoryAction(@Nullable ResourceFolderType resourceFolderType) {
    super(AndroidBundle.message("new.resource.dir.action.title"), AndroidBundle.message("new.resource.action.description"),
          PlatformIcons.DIRECTORY_CLOSED_ICON);
    myResourceFolderType = resourceFolderType;
  }

  @NotNull
  public PsiElement[] invokeDialog(@NotNull final Project project, @NotNull PsiDirectory directory) {
    NewResourceCreationHandler newResourceHandler = NewResourceCreationHandler.getInstance(project);
    CreateResourceDirectoryDialogBase dialog = newResourceHandler.createNewResourceDirectoryDialog(
      project, AndroidPsiUtils.getModuleSafely(directory), myResourceFolderType, directory, null,
      resDirectory -> new MyInputValidator(project, resDirectory));
    dialog.setTitle(AndroidBundle.message("new.resource.dir.dialog.title"));
    if (!dialog.showAndGet()) {
      return PsiElement.EMPTY_ARRAY;
    }
    return dialog.getCreatedElements();
  }

  @NotNull
  @Override
  public PsiElement[] invokeDialog(@NotNull final Project project, @NotNull final DataContext dataContext) {

    ResourceFolderType folderType = myResourceFolderType;
    if (folderType == null) {
      VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      folderType = CreateResourceFileAction.getUniqueFolderType(files);
    }

    NewResourceCreationHandler newResourceHandler = NewResourceCreationHandler.getInstance(project);
    CreateResourceDirectoryDialogBase dialog = newResourceHandler.createNewResourceDirectoryDialog(
      project, LangDataKeys.MODULE.getData(dataContext), folderType, findResourceDirectory(dataContext), dataContext,
      resDirectory -> new MyInputValidator(project, resDirectory));
    dialog.setTitle(AndroidBundle.message("new.resource.dir.dialog.title"));
    if (!dialog.showAndGet()) {
      return PsiElement.EMPTY_ARRAY;
    }
    return dialog.getCreatedElements();
  }

  @NotNull
  @Override
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    return new PsiElement[]{directory.createSubdirectory(newName)};
  }

  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  @Override
  protected String getCommandName() {
    return AndroidBundle.message("new.resource.dir.command.name");
  }

  @Nullable
  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return AndroidBundle.message("new.resource.dir.action.name", directory.getName() + File.separator + newName);
  }

  @Override
  protected boolean isAvailable(DataContext context) {
    if (!super.isAvailable(context)) return false;
    return CreateResourceFileAction.isOutsideResourceTypeFolder(context);
  }
}
