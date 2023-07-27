package org.jetbrains.android.augment

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.idea.res.AndroidInternalRClassFinder
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.AndroidTargetData
import com.google.common.collect.ImmutableSet
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier

class AndroidInternalRClass(psiManager: PsiManager, platform: AndroidPlatform, sdk: Sdk) : AndroidLightClassBase(
  psiManager, ImmutableSet.of(
    PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL
  )
) {
  private val myFile: PsiFile
  private val myPlatform: AndroidPlatform
  private val myInnerClasses: Array<PsiClass>

  init {
    myFile = PsiFileFactory.getInstance(myManager.project).createFileFromText("R.java", JavaFileType.INSTANCE, "")
    myFile.viewProvider.virtualFile.putUserData(ANDROID_INTERNAL_R, sdk)
    setModuleInfo(sdk)
    myPlatform = platform
    val types = ResourceType.values()
    myInnerClasses = arrayOfNulls(types.size)
    for (i in types.indices) {
      myInnerClasses[i] = MyInnerClass(types[i])
    }
  }

  override fun getQualifiedName(): String? {
    return AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME
  }

  override fun getName(): String {
    return "R"
  }

  override fun getContainingClass(): PsiClass? {
    return null
  }

  override fun getContainingFile(): PsiFile? {
    return myFile
  }

  override fun getTextRange(): TextRange {
    return TextRange.EMPTY_RANGE
  }

  override fun getInnerClasses(): Array<PsiClass> {
    return myInnerClasses
  }

  private inner class MyInnerClass(resourceType: ResourceType) : InnerRClassBase(this@AndroidInternalRClass, resourceType) {
    @Slow
    override fun doGetFields(): Array<PsiField> {
      val targetData = AndroidTargetData.get(myPlatform.sdkData, myPlatform.target)
      val repository = targetData.getFrameworkResources(ImmutableSet.of())
        ?: return PsiField.EMPTY_ARRAY
      return buildResourceFields(
        repository, ResourceNamespace.ANDROID, null,
        AndroidLightField.FieldModifier.FINAL,
        { resource: ResourceItem? -> true },
        resourceType,
        this@AndroidInternalRClass
      )
    }

    override val fieldsDependencies: ModificationTracker
      protected get() = ModificationTracker.NEVER_CHANGED
  }

  companion object {
    private val ANDROID_INTERNAL_R = Key.create<Sdk>("ANDROID_INTERNAL_R")
    @JvmStatic
    fun isAndroidInternalR(file: VirtualFile, sdk: Sdk): Boolean {
      return sdk == file.getUserData(ANDROID_INTERNAL_R)
    }
  }
}
