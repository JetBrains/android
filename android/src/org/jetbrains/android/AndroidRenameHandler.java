package org.jetbrains.android;

import com.android.annotations.Nullable;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRenameHandler implements RenameHandler, TitledHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return false;
    }

    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return false;
    }

    if (AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file) != null) {
      return true;
    }
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    if (project == null) {
      return false;
    }
    return isPackageAttributeInManifest(project, editor, file);
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    if (file == null || editor == null) {
      return;
    }
    final XmlTag tag = AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file);

    if (tag != null) {
      performValueResourceRenaming(project, editor, dataContext, tag);
    }
    else {
      performApplicationPackageRenaming(project, editor, file, dataContext);
    }
  }

  private static void performValueResourceRenaming(Project project, Editor editor, DataContext dataContext, XmlTag tag) {
    final XmlAttribute nameAttribute = tag.getAttribute("name");
    if (nameAttribute == null) {
      return;
    }

    final XmlAttributeValue attributeValue = nameAttribute.getValueElement();
    if (attributeValue == null) {
      return;
    }
    showRenameDialog(dataContext, new RenameDialog(project, new ValueResourceElementWrapper(attributeValue), null, editor));
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return;
    }

    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return;
    }

    invoke(project, editor, file, dataContext);
  }

  @Override
  public String getActionTitle() {
    return "Rename Android value resource";
  }

  private static boolean isPackageAttributeInManifest(@NotNull Project project,
                                                      @NotNull Editor editor,
                                                      @NotNull PsiFile psiFile) {
    final VirtualFile vFile = psiFile.getVirtualFile();

    if (vFile == null) {
      return false;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(psiFile);

    if (facet == null || !vFile.equals(AndroidRootUtil.getManifestFile(facet))) {
      return false;
    }
    PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    element = element != null ? element.getParent() : null;
    return isPackageAttributeInManifest(project, element);
  }

  static boolean isPackageAttributeInManifest(@NotNull Project project, @Nullable PsiElement element) {
    if (!(element instanceof XmlAttributeValue)) {
      return false;
    }
    final PsiElement parent = element.getParent();

    if (!(parent instanceof XmlAttribute)) {
      return false;
    }
    final GenericAttributeValue attrValue = DomManager.getDomManager(project).getDomElement((XmlAttribute)parent);

    if (attrValue == null) {
      return false;
    }
    final DomElement parentDomElement = attrValue.getParent();
    return parentDomElement instanceof Manifest && attrValue.equals(((Manifest)parentDomElement).getPackage());
  }

  private static void performApplicationPackageRenaming(@NotNull Project project,
                                                        @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    element = element != null ? element.getParent() : null;

    if (!(element instanceof XmlAttributeValue)) {
      return;
    }
    showRenameDialog(context, new RenameDialog(project, element, null, editor) {
      @NotNull
      @Override
      protected String getLabelText() {
        return "Rename Android application package to:";
      }

      @Override
      protected void canRun() throws ConfigurationException {
        final String name = getNewName();

        if (name.length() == 0) {
          throw new ConfigurationException(AndroidBundle.message("specify.package.name.error"));
        }
        if (!AndroidUtils.isValidPackageName(name)) {
          throw new ConfigurationException(AndroidBundle.message("not.valid.package.name.error", name));
        }
        if (!AndroidCommonUtils.contains2Identifiers(name)) {
          throw new ConfigurationException(AndroidBundle.message("package.name.must.contain.2.ids.error"));
        }
        super.canRun();
      }
    });
  }

  private static void showRenameDialog(DataContext dataContext, RenameDialog dialog) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final String name = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
      //noinspection TestOnlyProblems
      dialog.performRename(name);
      dialog.close(DialogWrapper.OK_EXIT_CODE);
    }
    else {
      dialog.show();
    }
  }
}
