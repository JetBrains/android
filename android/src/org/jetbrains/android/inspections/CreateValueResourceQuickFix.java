package org.jetbrains.android.inspections;


import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.dom.resources.ResourcesDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
public class CreateValueResourceQuickFix implements LocalQuickFix, IntentionAction, HighPriorityAction {
  private final AndroidFacet myFacet;
  private final ResourceType myResourceType;
  private final String myResourceName;
  private final PsiFile myFile;
  private final boolean myChooseName;

  public CreateValueResourceQuickFix(@NotNull AndroidFacet facet,
                                     @NotNull ResourceType resourceType,
                                     @NotNull String resourceName,
                                     @NotNull PsiFile file,
                                     boolean chooseName) {
    myFacet = facet;
    myResourceType = resourceType;
    myResourceName = resourceName;
    myFile = file;
    myChooseName = chooseName;
  }

  @Override
  @NotNull
  public String getName() {
    return AndroidBundle.message("create.value.resource.quickfix.name", myResourceName,
                                 AndroidResourceUtil.getDefaultResourceFileName(myResourceType));
  }

  @NotNull
  @Override
  public String getText() {
    return AndroidBundle.message("create.value.resource.intention.name", myResourceType, myResourceName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return AndroidBundle.message("quick.fixes.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    doInvoke();
  }

  protected boolean doInvoke() {
    Project project = myFile.getProject();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final String fileName = AndroidResourceUtil.getDefaultResourceFileName(myResourceType);
      assert fileName != null;
      VirtualFile resourceDir = myFacet.getPrimaryResourceDir();
      assert resourceDir != null;
      if (!AndroidResourceUtil.createValueResource(project, resourceDir, myResourceName, myResourceType, fileName,
                                                   Collections.singletonList(SdkConstants.FD_RES_VALUES), "a")) {
        return false;
      }
    }
    else {
      final String value = myResourceType == ResourceType.STYLEABLE ||
                           myResourceType == ResourceType.ATTR ? "\n" : null;

      VirtualFile defaultFileToCreate = null;

      if (myFile instanceof XmlFile && myFile.isWritable() && myFile.getManager().isInProject(myFile)) {
        final DomFileDescription<?> description = DomManager.getDomManager(project)
          .getDomFileDescription((XmlFile)myFile);

        if (description instanceof ResourcesDomFileDescription) {
          final VirtualFile defaultFile = myFile.getVirtualFile();
          if (defaultFile != null) {
            defaultFileToCreate = defaultFile;
          }
        }
      }
      final CreateXmlResourceDialog dialog =
        new CreateXmlResourceDialog(myFacet.getModule(), myResourceType, myResourceName, value, myChooseName, defaultFileToCreate,
                                    myFile.getVirtualFile());
      dialog.setTitle("New " + StringUtil.capitalize(myResourceType.getDisplayName()) + " Value Resource");
      if (!dialog.showAndGet()) {
        return false;
      }

      final VirtualFile resourceDir = dialog.getResourceDirectory();
      if (resourceDir == null) {
        AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", myFacet.getModule()));
        return false;
      }
      final String fileName = dialog.getFileName();
      final List<String> dirNames = dialog.getDirNames();
      final String resValue = dialog.getValue();
      final String resName = dialog.getResourceName();
      if (!AndroidResourceUtil.createValueResource(project, resourceDir, resName, myResourceType, fileName, dirNames, resValue)) {
        return false;
      }
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    UndoUtil.markPsiFileForUndo(myFile);
    AndroidLayoutPreviewToolWindowManager.renderIfApplicable(project);
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    // todo: implement local fix
  }
}
