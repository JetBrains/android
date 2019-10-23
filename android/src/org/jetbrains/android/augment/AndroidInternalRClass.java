package org.jetbrains.android.augment;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AndroidInternalRClassFinder;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import org.jetbrains.android.augment.AndroidLightField.FieldModifier;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidInternalRClass extends AndroidLightClassBase {
  private static final Key<Sdk> ANDROID_INTERNAL_R = Key.create("ANDROID_INTERNAL_R");
  private final PsiFile myFile;
  private final AndroidPlatform myPlatform;
  private final PsiClass[] myInnerClasses;

  public AndroidInternalRClass(@NotNull PsiManager psiManager, @NotNull AndroidPlatform platform, Sdk sdk) {
    super(psiManager, ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL));
    myFile = PsiFileFactory.getInstance(myManager.getProject()).createFileFromText("R.java", JavaFileType.INSTANCE, "");
    myFile.getViewProvider().getVirtualFile().putUserData(ANDROID_INTERNAL_R, sdk);
    setModuleInfo(sdk);
    myPlatform = platform;

    final ResourceType[] types = ResourceType.values();
    myInnerClasses = new PsiClass[types.length];

    for (int i = 0; i < types.length; i++) {
      myInnerClasses[i] = new MyInnerClass(types[i]);
    }
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME;
  }

  @Override
  public String getName() {
    return "R";
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myFile;
  }

  @Override
  public TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @NotNull
  @Override
  public PsiClass[] getInnerClasses() {
    return myInnerClasses;
  }

  @Override
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    for (PsiClass aClass : getInnerClasses()) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  private class MyInnerClass extends InnerRClassBase {

    private MyInnerClass(@NotNull ResourceType resourceType) {
      super(AndroidInternalRClass.this, resourceType);
    }

    @NotNull
    @Override
    protected PsiField[] doGetFields() {
      ResourceRepository repository = myPlatform.getSdkData().getTargetData(myPlatform.getTarget()).getFrameworkResources(false);
      if (repository == null) {
        return PsiField.EMPTY_ARRAY;
      }
      return buildResourceFields(repository,
                                 ResourceNamespace.ANDROID,
                                 FieldModifier.FINAL,
                                 (type, s) -> true,
                                 myResourceType, AndroidInternalRClass.this);
    }

    @NotNull
    @Override
    protected Object[] getFieldsDependencies() {
      return new Object[]{ModificationTracker.NEVER_CHANGED};
    }
  }

  public static boolean isAndroidInternalR(@NotNull VirtualFile file, @NotNull Sdk sdk) {
    return sdk.equals(file.getUserData(ANDROID_INTERNAL_R));
  }
}
