package org.jetbrains.android.augment;

import com.android.annotations.concurrency.Slow;
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
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Override
  @Nullable
  public String getQualifiedName() {
    return AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME;
  }

  @Override
  @NotNull
  public String getName() {
    return "R";
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  @Nullable
  public PsiFile getContainingFile() {
    return myFile;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @Override
  @NotNull
  public PsiClass[] getInnerClasses() {
    return myInnerClasses;
  }

  private class MyInnerClass extends InnerRClassBase {

    private MyInnerClass(@NotNull ResourceType resourceType) {
      super(AndroidInternalRClass.this, resourceType);
    }

    @Slow
    @Override
    @NotNull
    protected PsiField[] doGetFields() {
      AndroidTargetData targetData = AndroidTargetData.get(myPlatform.getSdkData(), myPlatform.getTarget());
      ResourceRepository repository = targetData.getFrameworkResources(ImmutableSet.of());
      if (repository == null) {
        return PsiField.EMPTY_ARRAY;
      }
      return buildResourceFields(repository, ResourceNamespace.ANDROID, null,
                                 FieldModifier.FINAL,
                                 (resource) -> true,
                                 myResourceType,
                                 AndroidInternalRClass.this);
    }

    @Override
    @NotNull
    protected ModificationTracker getFieldsDependencies() {
      return ModificationTracker.NEVER_CHANGED;
    }
  }

  public static boolean isAndroidInternalR(@NotNull VirtualFile file, @NotNull Sdk sdk) {
    return sdk.equals(file.getUserData(ANDROID_INTERNAL_R));
  }
}
