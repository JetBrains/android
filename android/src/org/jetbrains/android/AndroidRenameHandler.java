package org.jetbrains.android;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ITEM;

import com.android.ide.common.resources.ValueResourceNameValidator;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.codeInsight.TargetElementUtil;
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
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.android.augment.AndroidLightField;
import org.jetbrains.android.dom.converters.AndroidResourceReference;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidRenameHandler implements RenameHandler, TitledHandler {
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    if (StudioFlags.RESOLVE_USING_REPOS.get()) return false;
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return false;
    }

    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return false;
    }

    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof SchemaPrefix) {
      return false; // Leave renaming of namespace prefixes to the default XML handler.
    }

    if (AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file, true) != null) {
      return true;
    }

    if (getResourceReferenceTarget(editor) != null) {
      return true;
    }

    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    if (project == null) {
      return false;
    }
    return isPackageAttributeInManifest(project, element);
  }

  /**
   * Determine if this editor's caret is currently on a reference to an Android resource and if so return the root definition of that
   * resource.
   */
  @Nullable
  private static PsiElement getResourceReferenceTarget(@NotNull Editor editor) {
    PsiReference reference = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
    if (!(reference instanceof AndroidResourceReference)) {
      return null;
    }

    Collection<PsiElement> elements = TargetElementUtil.getInstance().getTargetCandidates(reference);
    if (elements.isEmpty()) {
      return null;
    }

    ArrayList<PsiElement> elementList = new ArrayList<>(elements);
    Collections.sort(elementList, AndroidResourceUtil.RESOURCE_ELEMENT_COMPARATOR);
    return elementList.get(0);
  }

  @Override
  public boolean isRenaming(@NotNull DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file, @NotNull DataContext dataContext) {
    if (file == null || editor == null) {
      return;
    }
    XmlTag tag = AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file, true);

    if (tag != null) {
      // See if you've actually pointed at an XML value inside the value definition, e.g.
      //   <string name="my_alias">@string/my_string</string>
      // If the caret is on my_string, you expect to rename my_string, not my_alias (the XmlTag).
      ResourceUrl url = findResourceReferenceUnderCaret(editor, file);
      if (url != null && !url.isFramework()) {
        performResourceReferenceRenaming(project, editor, dataContext, file, url);
      }
      else {
        performValueResourceRenaming(project, editor, dataContext, tag);
      }
    }
    else {
      PsiElement element = getResourceReferenceTarget(editor);
      if (element != null) {
        performResourceReferenceRenaming(project, editor, dataContext, element);
      }
      else {
        performApplicationPackageRenaming(project, editor, dataContext);
      }
    }
  }

  private static void performValueResourceRenaming(@NotNull Project project,
                                                   @NotNull Editor editor,
                                                   @NotNull DataContext dataContext,
                                                   @NotNull XmlTag tag) {
    XmlAttribute nameAttribute = tag.getAttribute("name");
    if (nameAttribute == null) {
      return;
    }

    XmlAttributeValue attributeValue = nameAttribute.getValueElement();
    if (attributeValue == null) {
      return;
    }
    RenameDialog.showRenameDialog(dataContext,
                                  new ResourceRenameDialog(project, new ValueResourceElementWrapper(attributeValue), null, editor));
  }

  private static void performResourceReferenceRenaming(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull DataContext dataContext,
                                                       @NotNull PsiFile file,
                                                       @NotNull ResourceUrl url) {
    assert !url.isFramework();

    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet != null) {
      // Treat the resource reference as if the user renamed the R field instead.
      PsiField[] resourceFields = AndroidResourceUtil.findResourceFields(facet, url.type.getName(), url.name, false);
      if (resourceFields.length == 1) {
        PsiElement element = resourceFields[0];
        if (element instanceof AndroidLightField) {
          element = new ResourceFieldElementWrapper((AndroidLightField)element);
        }
        RenameDialog.showRenameDialog(dataContext, new ResourceRenameDialog(project, element, null, editor));
      }
    }
  }

  private static void performResourceReferenceRenaming(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull DataContext dataContext,
                                                       @NotNull PsiElement element) {
    RenameDialog.showRenameDialog(dataContext, new ResourceRenameDialog(project, element, null, editor));
  }

  @Nullable
  private static ResourceUrl findResourceReferenceUnderCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof XmlFile)) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }

    if (!AndroidResourceUtil.isInResourceSubdirectory(file, ResourceFolderType.VALUES.getName())) {
      return null;
    }

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) {
      return null;
    }
    if (element instanceof XmlToken) {
      IElementType tokenType = ((XmlToken)element).getTokenType();
      if (tokenType == XmlTokenType.XML_DATA_CHARACTERS) {
        XmlText text = PsiTreeUtil.getParentOfType(element, XmlText.class);
        if (text != null) {
          return ResourceUrl.parse(text.getText().trim());
        }
      }
      else if (tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
        XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (tag != null && tag.getLocalName().equals(TAG_ITEM)) {
          XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
          if (attribute != null && attribute.getLocalName().equals(ATTR_NAME)) {
            return ResourceUrl.parseAttrReference(element.getText());
          }
        }
      }
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @NotNull DataContext dataContext) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return;
    }

    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
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
    PsiFile psiFile = element.getContainingFile();

    if (!(psiFile instanceof XmlFile)) {
      return false;
    }
    AndroidFacet facet = AndroidFacet.getInstance(psiFile);

    if (facet == null) {
      return false;
    }
    VirtualFile vFile = psiFile.getVirtualFile();

    if (vFile == null || !vFile.equals(AndroidRootUtil.getPrimaryManifestFile(facet))) {
      return false;
    }
    if (!(element instanceof XmlAttributeValue)) {
      return false;
    }
    PsiElement parent = element.getParent();

    if (!(parent instanceof XmlAttribute)) {
      return false;
    }
    GenericAttributeValue attrValue = DomManager.getDomManager(project).getDomElement((XmlAttribute)parent);

    if (attrValue == null) {
      return false;
    }
    DomElement parentDomElement = attrValue.getParent();
    return parentDomElement instanceof Manifest && attrValue.equals(((Manifest)parentDomElement).getPackage());
  }

  private static void performApplicationPackageRenaming(@NotNull Project project,
                                                        @NotNull Editor editor,
                                                        @NotNull DataContext context) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);

    if (!(element instanceof XmlAttributeValue)) {
      return;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(element);

    if (module == null) {
      return;
    }
    RenameDialog.showRenameDialog(context, new RenameDialog(project, element, null, editor) {
      @Override
      @NotNull
      protected String getLabelText() {
        return "Rename Android application package of module '" + module.getName() + "' to:";
      }

      @Override
      protected void canRun() throws ConfigurationException {
        String name = getNewName();

        if (name.isEmpty()) {
          throw new ConfigurationException(AndroidBundle.message("specify.package.name.error"));
        }
        if (!AndroidUtils.isValidAndroidPackageName(name)) {
          throw new ConfigurationException(AndroidBundle.message("not.valid.package.name.error", name));
        }
        if (!AndroidBuildCommonUtils.contains2Identifiers(name)) {
          throw new ConfigurationException(AndroidBundle.message("package.name.must.contain.2.ids.error"));
        }
        super.canRun();
      }
    });
  }

  private static class ResourceRenameDialog extends RenameDialog {
    ResourceRenameDialog(@NotNull Project project,
                         @NotNull PsiElement psiElement,
                         @Nullable PsiElement nameSuggestionContext,
                         @Nullable Editor editor) {
      super(project, psiElement, nameSuggestionContext, editor);
    }

    @Override
    protected void canRun() throws ConfigurationException {
      String name = getNewName();
      String errorText = ValueResourceNameValidator.getErrorText(name, null);
      if (errorText != null ) {
        throw new ConfigurationException(errorText);
      }
    }
  }
}
