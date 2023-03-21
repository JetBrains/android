/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.inspections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23;
import static com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M;
import static com.android.tools.lint.checks.PermissionDetector.MISSING_PERMISSION;

import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.lint.checks.PermissionDetector;
import com.android.tools.lint.checks.PermissionRequirement;
import com.android.tools.lint.detector.api.LintFix;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtIfExpression;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.psi.KtStatementExpression;

public class AndroidLintMissingPermissionInspection extends AndroidLintInspectionBase {
  public AndroidLintMissingPermissionInspection() {
    super(AndroidLintBundle.message("android.lint.inspections.missing.permission"), MISSING_PERMISSION);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message,
                                         LintFix quickfixData) {
    if (quickfixData instanceof LintFix.DataMap) {
      LintFix.DataMap map = (LintFix.DataMap)quickfixData;

      List<String> names = map.getStringList(PermissionDetector.KEY_MISSING_PERMISSIONS);
      if (names == null) {
        return super.getQuickFixes(startElement, endElement, message, quickfixData);
      }

      AndroidFacet facet = AndroidFacet.getInstance(startElement);
      if (facet == null) {
        return super.getQuickFixes(startElement, endElement, message, quickfixData);
      }

      int lastApplicableApi = map.getInt(PermissionDetector.KEY_LAST_API, -1);

      String requirementString = map.getString(PermissionDetector.KEY_REQUIREMENT, null);
      PermissionRequirement requirement = requirementString != null ? PermissionRequirement.deserialize(requirementString) : null;
      if (lastApplicableApi != -1) {
        // [missing permissions: Set<String>, maxSdkVersion: Integer] :
        // Add quickfixes for the missing permissions
        List<LintIdeQuickFix> fixes = Lists.newArrayListWithExpectedSize(4);
        for (String name : names) {
          fixes.add(new AddPermissionFix(facet, name, lastApplicableApi));
        }
        return fixes.toArray(LintIdeQuickFix.EMPTY_ARRAY);
      }
      else if (requirement != null) {
        // [revocable permissions: Set<String>, requirement: PermissionRequirement] :
        // Add quickfix for requesting permissions
        return new LintIdeQuickFix[]{
          new AddCheckPermissionFix(facet, requirement, startElement, names)
        };
      }
    }

    return super.getQuickFixes(startElement, endElement, message, quickfixData);
  }

  static class AddPermissionFix extends DefaultLintQuickFix {
    private final AndroidFacet myFacet;
    private final String myPermissionName;
    private final int myMaxVersion;

    AddPermissionFix(@NotNull AndroidFacet facet, @NotNull String permissionName, int maxVersion) {
      super(null);
      myFacet = facet;
      myPermissionName = permissionName;
      myMaxVersion = maxVersion;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return String.format("Add Permission %1$s", myPermissionName.substring(myPermissionName.lastIndexOf('.') + 1));
    }

    @Override
    public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
      final VirtualFile manifestFile = AndroidRootUtil.getPrimaryManifestFile(myFacet);
      if (manifestFile == null || !ReadonlyStatusHandler.ensureFilesWritable(myFacet.getModule().getProject(), manifestFile)) {
        return;
      }

      final Manifest manifest = Manifest.getMainManifest(myFacet);
      if (manifest == null) {
        return;
      }

      // I tried manipulating the file using DOM apis, using this:
      //    Permission permission = manifest.addPermission();
      //    permission.getName().setValue(myPermissionName);
      // (which required adding
      //      Permission addPermission();
      // to org.jetbrains.android.dom.manifest.Manifest).
      //
      // However, that will append a <permission name="something"/> element to the
      // *end* of the manifest, which is not right (and will trigger a lint warning).
      // So, instead we manipulate the XML document directly via PSI. (This is
      // incidentally also how the AndroidModuleBuilder#configureManifest method works.)
      final XmlTag manifestTag = manifest.getXmlTag();
      if (manifestTag == null) {
        return;
      }

      XmlTag permissionTag = manifestTag.createChildTag(TAG_USES_PERMISSION, "", null, false);
      if (permissionTag != null) {

        XmlTag before = null;
        // Find best insert position:
        //   (1) attempt to insert alphabetically among any permission tags
        //   (2) if no permission tags are found, put it before the application tag
        for (XmlTag tag : manifestTag.getSubTags()) {
          String tagName = tag.getName();
          if (tagName.equals(TAG_APPLICATION)) {
            before = tag;
            break;
          }
          else if (tagName.equals(TAG_USES_PERMISSION)
                   || tagName.equals(TAG_USES_PERMISSION_SDK_23)
                   || tagName.equals(TAG_USES_PERMISSION_SDK_M)) {
            String name = tag.getAttributeValue(ATTR_NAME, ANDROID_URI);
            if (name != null && name.compareTo(myPermissionName) > 0) {
              before = tag;
              break;
            }
          }
        }
        if (before == null) {
          permissionTag = manifestTag.addSubTag(permissionTag, false);
        }
        else {
          permissionTag = (XmlTag)manifestTag.addBefore(permissionTag, before);
        }

        // Do this *after* adding the tag to the document; otherwise, setting the
        // namespace prefix will not work correctly
        permissionTag.setAttribute(ATTR_NAME, ANDROID_URI, myPermissionName);

        // Some permissions only apply for a range of API levels - for example,
        // the MANAGE_ACCOUNTS permission is only needed pre Marshmallow. In that
        // case set a maxSdkVersion attribute on the uses-permission element.
        if (myMaxVersion != Integer.MAX_VALUE
            && myMaxVersion >= StudioAndroidModuleInfo.getInstance(myFacet).getMinSdkVersion().getApiLevel()) {
          permissionTag.setAttribute("maxSdkVersion", ANDROID_URI, Integer.toString(myMaxVersion));
        }

        Project project = myFacet.getModule().getProject();
        CodeStyleManager.getInstance(project).reformat(permissionTag);

        FileDocumentManager.getInstance().saveAllDocuments();
        PsiFile containingFile = permissionTag.getContainingFile();
        // No edits were made to the current document, so trigger a rescan to ensure
        // that the inspection discovers that there is now a new available inspection
        if (containingFile != null) {
          DaemonCodeAnalyzer.getInstance(project).restart();
        }
      }
    }
  }

  private static class AddCheckPermissionFix extends DefaultLintQuickFix {
    private final AndroidFacet myFacet;
    private final PermissionRequirement myRequirement;
    private final Collection<String> myRevocablePermissions;
    private final SmartPsiElementPointer<PsiElement> myCall;

    private AddCheckPermissionFix(@NotNull AndroidFacet facet, @NotNull PermissionRequirement requirement, @NotNull PsiElement call,
                                  @NotNull Collection<String> revocablePermissions) {
      super("Add permission check");
      myFacet = facet;
      myRequirement = requirement;
      myCall = SmartPointerManager.getInstance(call.getProject()).createSmartPsiElementPointer(call);
      myRevocablePermissions = revocablePermissions;
    }

    @Override
    public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
      Project project = myFacet.getModule().getProject();
      PsiElement call = myCall.getElement();
      if (call == null) {
        return;
      }

      // Find the statement containing the method call;
      boolean isKotlin = false;
      PsiElement statement = PsiTreeUtil.getParentOfType(call, PsiStatement.class, true);
      if (statement == null) {
        // Kotlin?
        PsiElement curr = call;
        while (curr != null) {
          PsiElement parent = curr.getParent();
          if (curr instanceof KtStatementExpression) {
            break;
          }
          statement = curr;
          isKotlin = true;
          curr = parent;
        }

        if (statement == null) {
          return;
        }
      }
      PsiElement parent = statement.getParent();
      if (parent == null) {
        return; // highly unlikely
      }

      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithLibrariesScope(myFacet.getModule());
      PsiClass manifest = facade.findClass("android.Manifest.permission", moduleScope);
      Map<String, PsiField> permissionNames;

      if (manifest != null) {
        PsiField[] fields = manifest.getFields();
        permissionNames = Maps.newHashMapWithExpectedSize(fields.length);
        for (PsiField field : fields) {
          PsiExpression initializer = field.getInitializer();
          if (initializer instanceof PsiLiteralExpression) {
            Object value = ((PsiLiteralExpression)initializer).getValue();
            if (value instanceof String) {
              permissionNames.put((String)value, field);
            }
          }
        }
      }
      else {
        permissionNames = Collections.emptyMap();
      }

      // Look up the operator combining the requirements, and *reverse it*.
      // That's because we're adding a check to exit if the permissions are *not* met.
      // For example, take the case of location permissions: you need COARSE OR FINE.
      // In that case, we check that you do not have COARSE, *and* that you do not have FINE,
      // before we exit.
      IElementType operator = myRequirement.getOperator();
      if (operator == null || operator == JavaTokenType.ANDAND) {
        operator = JavaTokenType.OROR;
      }
      else if (operator == JavaTokenType.OROR) {
        operator = JavaTokenType.ANDAND;
      }

      StringBuilder sb = new StringBuilder(200);
      sb.append("if (");
      boolean first = true;

      PsiClass activityCompat = facade.findClass("androidx.core.app.ActivityCompat", moduleScope);
      if (activityCompat == null) {
        activityCompat = facade.findClass("android.support.v4.app.ActivityCompat", moduleScope);
      }
      boolean usingAppCompat = activityCompat != null;
      if (usingAppCompat && (activityCompat.findMethodsByName("requestPermissions", false).length == 0)) {
        // Using an older version of appcompat than 23.0.1. Later we should prompt the user to
        // see if they'd like to upgrade instead; for now, revert to platform version.
        usingAppCompat = false;
      }

      for (String permission : myRevocablePermissions) {
        if (first) {
          first = false;
        }
        else {
          sb.append(' ');
          if (operator == JavaTokenType.ANDAND) {
            sb.append("&&");
          }
          else if (operator == JavaTokenType.OROR) {
            sb.append("||");
          }
          else if (operator == JavaTokenType.XOR) {
            sb.append("^");
          }
          sb.append(' ');
        }
        if (usingAppCompat) {
          sb.append(activityCompat.getQualifiedName());
          sb.append(".");
        }
        sb.append("checkSelfPermission(");
        if (usingAppCompat) {
          sb.append("this, ");
        }

        // Try to map permission strings back to field references!
        PsiField field = permissionNames.get(permission);
        if (field != null && field.getContainingClass() != null) {
          sb.append(field.getContainingClass().getQualifiedName()).append('.').append(field.getName());
        }
        else {
          sb.append('"').append(permission).append('"');
        }
        sb.append(") != android.content.pm.PackageManager.PERMISSION_GRANTED");
      }
      sb.append(") {\n");
      sb.append(" // TODO: Consider calling\n" +
                " //    Activity").append(usingAppCompat ? "Compat" : "").append(
        "#requestPermissions\n" +
        " // here to request the missing permissions, and then overriding\n" +
        " //   public void onRequestPermissionsResult(int requestCode, String[] permissions,\n" +
        " //                                          int[] grantResults)\n" +
        " // to handle the case where the user grants the permission. See the documentation\n" +
        " // for Activity").append(usingAppCompat ? "Compat" : "").append("#requestPermissions for more details.\n");

      // TODO: Add additional information here, perhaps pointing to
      //    http://android-developers.blogspot.com/2015/09/google-play-services-81-and-android-60.html
      // or adding more of a skeleton from that article.

      PsiMethod method = isKotlin ? null : PsiTreeUtil.getParentOfType(call, PsiMethod.class, true);
      if (method != null && !PsiType.VOID.equals(method.getReturnType())) {
        sb.append("return TODO");
      }
      else {
        sb.append("return");
      }
      if (!isKotlin) {
        sb.append(';');
      }
      sb.append("\n}\n");
      String code = sb.toString();

      if (isKotlin) {
        KtPsiFactory factory = new KtPsiFactory(project);
        KtBlockExpression check = factory.createBlock(code);
        for (PsiElement child : check.getChildren()) {
          if (child instanceof KtIfExpression) {
            PsiElement added = parent.addBefore(child, statement);
            parent.addBefore(factory.createNewLine(), statement);
            if (added instanceof KtElement) {
              ShortenReferencesFacility.Companion.getInstance().shorten((KtElement)added);
            }
            break;
          }
        }
      }
      else {
        PsiElementFactory factory = facade.getElementFactory();
        PsiStatement check = factory.createStatementFromText(code, call);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(check);
        parent.addBefore(check, statement);

        // Reformat from start of newly added element to end of statement following it
        CodeStyleManager.getInstance(project).reformatRange(parent, check.getTextOffset(),
                                                            statement.getTextOffset() + statement.getTextLength());
      }
    }
  }
}
