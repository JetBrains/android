package org.jetbrains.android.actions;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
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
  public PsiElement[] invokeDialog(@NotNull final Project project, @NotNull final PsiDirectory directory) {
    final CreateResourceDirectoryDialog dialog = new CreateResourceDirectoryDialog(project, myResourceFolderType, directory,
                                                                                   AndroidPsiUtils.getModuleSafely(directory)) {
      @Override
      protected InputValidator createValidator() {
        return CreateResourceDirectoryAction.this.createValidator(project, directory);
      }
    };
    dialog.setTitle(AndroidBundle.message("new.resource.dir.dialog.title"));
    dialog.show();
    final InputValidator validator = dialog.getValidator();
    if (validator == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    return ((MyInputValidator)validator).getCreatedElements();
  }

  @NotNull
  @Override
  public PsiElement[] invokeDialog(@NotNull final Project project, @NotNull final DataContext dataContext) {

    ResourceFolderType folderType = myResourceFolderType;
    if (folderType == null) {
      VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      folderType = CreateResourceFileAction.getUniqueFolderType(files);
    }

    final CreateResourceDirectoryDialog dialog = new CreateResourceDirectoryDialog(project, folderType,
                                                                                   findResourceDirectory(dataContext),
                                                                                   LangDataKeys.MODULE.getData(dataContext)) {
      @Override
      protected InputValidator createValidator() {
        Module module = LangDataKeys.MODULE.getData(dataContext);
        assert module != null;
        PsiDirectory resourceDirectory = getResourceDirectory(dataContext, true);
        return CreateResourceDirectoryAction.this.createValidator(module.getProject(), resourceDirectory);
      }
    };
    dialog.setTitle(AndroidBundle.message("new.resource.dir.dialog.title"));
    dialog.show();
    final InputValidator validator = dialog.getValidator();
    if (validator == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    return ((MyInputValidator)validator).getCreatedElements();
  }

  @NotNull
  private MyInputValidator createValidator(Project project, final PsiDirectory resDir) {
    return new MyInputValidator(project, resDir);
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
