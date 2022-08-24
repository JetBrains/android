package org.jetbrains.android;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenamePsiPackageProcessor;
import com.intellij.refactoring.rename.RenameXmlAttributeProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidApplicationPackageRenameProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    if (element instanceof PsiPackage) {
      // possibly renaming application package
      return ProjectFacetManager.getInstance(element.getProject()).hasFacets(AndroidFacet.ID);
    }
    return isPackageAttributeInManifest(element.getProject(), element);
  }

  private static boolean isPackageAttributeInManifest(@NotNull Project project, @Nullable PsiElement element) {
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


  @Override
  public void renameElement(@NotNull PsiElement element, @NotNull String newName, @NotNull UsageInfo[] usages, @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException {
    if (element instanceof PsiPackage) {
      final Map<GenericAttributeValue, String> newAttrValues = new HashMap<GenericAttributeValue, String>();

      final Project project = element.getProject();
      final String oldPackageQName = ((PsiPackage)element).getQualifiedName();
      final String newPackageQName = PsiUtilCore.getQualifiedNameAfterRename(oldPackageQName, newName);

      for (AndroidFacet facet : ProjectSystemUtil.getAndroidFacets(project)) {
        final Manifest manifest = facet != null ? Manifest.getMainManifest(facet) : null;

        if (manifest != null) {
          final XmlElement manifestElement = manifest.getXmlElement();
          final PsiFile manifestPsiFile = manifestElement != null ? manifestElement.getContainingFile() : null;

          if (manifestPsiFile instanceof XmlFile) {
            final String basePackage = manifest.getPackage().getValue();

            if (basePackage == null) {
              continue;
            }
            processAllAttributesToUpdate(
              (XmlFile)manifestPsiFile, basePackage, oldPackageQName, newPackageQName,
              new Processor<Pair<GenericAttributeValue, String>>() {
                @Override
                public boolean process(Pair<GenericAttributeValue, String> pair) {
                  newAttrValues.put(pair.getFirst(), pair.getSecond());
                  return true;
                }
              });
          }
        }
      }
      new RenamePsiPackageProcessor().renameElement(element, newName, usages, listener);

      for (Map.Entry<GenericAttributeValue, String> e : newAttrValues.entrySet()) {
        //noinspection unchecked
        e.getKey().setStringValue(e.getValue());
      }
      return;
    }
    final PsiFile file = element.getContainingFile();

    if (!(file instanceof XmlFile)) {
      return;
    }
    final Map<GenericAttributeValue, PsiClass> attr2class = buildAttr2ClassMap((XmlFile)file);

    new RenameXmlAttributeProcessor().renameElement(element, newName, usages, listener);

    for (Map.Entry<GenericAttributeValue, PsiClass> e : attr2class.entrySet()) {
      //noinspection unchecked
      e.getKey().setValue(e.getValue());
    }
  }

  @Nullable
  private static String computeNewQName(@NotNull String name, @NotNull String oldPackageName, @NotNull String newPackageName) {
    if (name.startsWith(oldPackageName)) {
      final String suffix = name.substring(oldPackageName.length());

      if (suffix.isEmpty() || suffix.charAt(0) == '.') {
        return newPackageName + suffix;
      }
    }
    return null;
  }

  @NotNull
  private static Map<GenericAttributeValue, PsiClass> buildAttr2ClassMap(@NotNull XmlFile file) {
    final Map<GenericAttributeValue, PsiClass> map = new HashMap<GenericAttributeValue, PsiClass>();

    processAllClassAttrValues(file, new Processor<Pair<GenericAttributeValue, PsiClass>>() {
      @Override
      public boolean process(Pair<GenericAttributeValue, PsiClass> pair) {
        map.put(pair.getFirst(), pair.getSecond());
        return true;
      }
    });
    return map;
  }

  public static void processAllAttributesToUpdate(@NotNull XmlFile file,
                                                  @NotNull final String basePackage,
                                                  @NotNull final String oldPackageQName,
                                                  @NotNull final String newPackageQName,
                                                  @NotNull final Processor<Pair<GenericAttributeValue, String>> processor) {
    if (!AndroidUtils.isPackagePrefix(oldPackageQName, basePackage) &&
        !AndroidUtils.isPackagePrefix(newPackageQName, basePackage)) {
      return;
    }
    processAllClassAttrValues(file, new Processor<Pair<GenericAttributeValue, PsiClass>>() {
      @Override
      public boolean process(Pair<GenericAttributeValue, PsiClass> pair) {
        final GenericAttributeValue domValue = pair.getFirst();
        final PsiClass psiClass = pair.getSecond();
        final String classPackageName = PackageClassConverter.getPackageName(psiClass);

        if (classPackageName != null) {
          final String classQName = PackageClassConverter.getQualifiedName(psiClass);

          if (classQName != null) {
            final String newClassQName = computeNewQName(classQName, oldPackageQName, newPackageQName);

            if (newClassQName != null) {
              final String newRefValue = AndroidUtils.isPackagePrefix(basePackage, newClassQName)
                                         ? newClassQName.substring(basePackage.length())
                                         : newClassQName;
              processor.process(Pair.create(domValue, newRefValue));
            }
          }
        }
        return true;
      }
    });
  }

  private static void processAllClassAttrValues(@NotNull XmlFile file,
                                                @NotNull final Processor<Pair<GenericAttributeValue, PsiClass>> processor) {
    final DomManager domManager = DomManager.getDomManager(file.getProject());

    file.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
        final GenericAttributeValue domAttrValue = domManager.getDomElement(attribute);

        if (domAttrValue != null) {
          final Object value = domAttrValue.getValue();

          if (value instanceof PsiClass) {
            processor.process(Pair.create(domAttrValue, (PsiClass)value));
          }
        }
      }
    });
  }
}
