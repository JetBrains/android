package com.android.tools.idea.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import icons.AndroidIcons;
import org.jetbrains.android.actions.NewAndroidComponentDialog;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

/**
 * @author Eugene.Kudelevsky
 */
public class LegacyNewAndroidComponentAction extends AnAction {

  protected LegacyNewAndroidComponentAction() {
    super(AndroidBundle.message("android.new.component.action.title.non.gradle"),
          AndroidBundle.message("android.new.component.action.description"),
          AndroidIcons.Android);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(isAvailable(e.getDataContext()));
  }

  private static boolean isAvailable(DataContext dataContext) {
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);

    if (module == null ||
        view == null ||
        view.getDirectories().length == 0) {
      return false;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(module);

    if (facet == null || facet.requiresAndroidModel()) {
      return false;
    }
    final ProjectFileIndex projectIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    final JavaDirectoryService dirService = JavaDirectoryService.getInstance();

    for (PsiDirectory dir : view.getDirectories()) {
      if (projectIndex.isUnderSourceRootOfType(dir.getVirtualFile(), JavaModuleSourceRootTypes.SOURCES) &&
          dirService.getPackage(dir) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }
    final Module module = LangDataKeys.MODULE.getData(dataContext);

    if (module == null) return;
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;

    final PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null) return;

    NewAndroidComponentDialog dialog = new NewAndroidComponentDialog(module, dir);
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    final PsiElement[] createdElements = dialog.getCreatedElements();

    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
  }
}
