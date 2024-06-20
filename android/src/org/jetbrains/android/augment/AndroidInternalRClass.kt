package org.jetbrains.android.augment

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
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

class AndroidInternalRClass(psiManager: PsiManager, private val platform: AndroidPlatform, sdk: Sdk) : AndroidLightClassBase(
  psiManager,
  ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL)
) {
  private val file: PsiFile = PsiFileFactory.getInstance(myManager.project).createFileFromText("R.java", JavaFileType.INSTANCE, "")
  private val innerClasses: Array<PsiClass>

  init {
    file.viewProvider.virtualFile.putUserData(ANDROID_INTERNAL_R, sdk)
    setModuleInfo(sdk)
    innerClasses = ResourceType.values().map(::MyInnerClass).toTypedArray()
  }

  override fun getQualifiedName() = AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME
  override fun getName() = "R"
  override fun getContainingClass() = null
  override fun getContainingFile() = file
  override fun getTextRange(): TextRange = TextRange.EMPTY_RANGE
  override fun getInnerClasses() = innerClasses

  private inner class MyInnerClass(resourceType: ResourceType) : InnerRClassBase(this@AndroidInternalRClass, resourceType) {
    @Slow
    override fun doGetFields(): Array<PsiField> {
      val repository = AndroidTargetData.get(platform.sdkData, platform.target).getFrameworkResources(emptySet(), emptyList())
                       ?: return PsiField.EMPTY_ARRAY
      return buildResourceFields(
        repository,
        ResourceNamespace.ANDROID,
        AndroidLightField.FieldModifier.FINAL,
        resourceType,
        context = this@AndroidInternalRClass,
      )
    }

    override val fieldsDependencies: ModificationTracker = ModificationTracker.NEVER_CHANGED
  }

  companion object {
    private val ANDROID_INTERNAL_R = Key.create<Sdk>("ANDROID_INTERNAL_R")
    @JvmStatic
    fun isAndroidInternalR(file: VirtualFile, sdk: Sdk) = sdk == file.getUserData(ANDROID_INTERNAL_R)
  }
}
