package org.jetbrains.android;

import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceFolderType;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
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
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidRenameHandler implements RenameHandler, TitledHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return false;
    }

    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return false;
    }

    if (AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file, true) != null) {
      return true;
    }
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    if (project == null) {
      return false;
    }
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    return element != null && isPackageAttributeInManifest(project, element);
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
    final XmlTag tag = AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file, true);

    if (tag != null) {
      // See if you've actually pointed at an XML value inside the value definition, e.g.
      //   <string name="my_alias">@string/my_string</string>
      // If the caret is on my_string, you expect to rename my_string, not my_alias (the XmlTag)
      ResourceUrl url = findResourceReferenceUnderCaret(editor, file);
      if (url != null && !url.framework) {
        performResourceReferenceRenaming(project, editor, dataContext, file, url);
      }
      else {
        performValueResourceRenaming(project, editor, dataContext, tag);
      }
    }
    else {
      performApplicationPackageRenaming(project, editor, dataContext);
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
    RenameDialog.showRenameDialog(dataContext, new RenameDialog(project, new ValueResourceElementWrapper(attributeValue), null, editor));
  }

  private static void performResourceReferenceRenaming(Project project,
                                                       Editor editor,
                                                       DataContext dataContext,
                                                       PsiFile file,
                                                       ResourceUrl url) {
    assert !url.framework;

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet != null) {
      // Treat the resource reference as if the user renamed the R field instead
      PsiField[] resourceFields = AndroidResourceUtil.findResourceFields(facet, url.type.getName(), url.name, false);
      if (resourceFields.length == 1) {
        RenameDialog.showRenameDialog(dataContext, new RenameDialog(project, resourceFields[0], null, editor));
      }
    }
  }

  @Nullable
  private static ResourceUrl findResourceReferenceUnderCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof XmlFile)) {
      return null;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }

    if (!AndroidResourceUtil.isInResourceSubdirectory(file, ResourceFolderType.VALUES.getName())) {
      return null;
    }

    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) {
      return null;
    }

    if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      final XmlText text = PsiTreeUtil.getParentOfType(element, XmlText.class);
      if (text != null) {
        return ResourceUrl.parse(text.getText().trim());
      }
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return;
    }

    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return;
    }

    invoke(project, editor, file, dataContext);
  }

  @Override
  public String getActionTitle() {
    return "Rename Android value resource";
  }

  static boolean isPackageAttributeInManifest(@NotNull Project project, @Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    final PsiFile psiFile = element.getContainingFile();

    if (!(psiFile instanceof XmlFile)) {
      return false;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(psiFile);

    if (facet == null) {
      return false;
    }
    final VirtualFile vFile = psiFile.getVirtualFile();

    if (vFile == null || !vFile.equals(AndroidRootUtil.getManifestFile(facet))) {
      return false;
    }
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
                                                        @NotNull DataContext context) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);

    if (!(element instanceof XmlAttributeValue)) {
      return;
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);

    if (module == null) {
      return;
    }
    RenameDialog.showRenameDialog(context, new RenameDialog(project, element, null, editor) {
      @NotNull
      @Override
      protected String getLabelText() {
        return "Rename Android application package of module '" + module.getName() + "' to:";
      }

      @Override
      protected void canRun() throws ConfigurationException {
        final String name = getNewName();

        if (name.length() == 0) {
          throw new ConfigurationException(AndroidBundle.message("specify.package.name.error"));
        }
        if (!AndroidUtils.isValidAndroidPackageName(name)) {
          throw new ConfigurationException(AndroidBundle.message("not.valid.package.name.error", name));
        }
        if (!AndroidCommonUtils.contains2Identifiers(name)) {
          throw new ConfigurationException(AndroidBundle.message("package.name.must.contain.2.ids.error"));
        }
        super.canRun();
      }
    });
  }
}
