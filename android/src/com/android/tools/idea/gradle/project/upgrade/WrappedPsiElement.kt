/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.usages.impl.rules.UsageType
import javax.swing.Icon

/**
 * This class is a [PsiElement] wrapper with additional fields to store [AgpUpgradeComponentRefactoringProcessor] metadata.
 *
 * We can't use the existing user data slot on the PsiElement, because an upgrade refactoring will in general have multiple
 * Usages with the same PsiElement, and some of the protocol functions offer us nothing but the element to distinguish -- so
 * we can't tell which of the usages a particular call corresponds to without something equivalent to this breaking of object
 * identity.
 */
class WrappedPsiElement(
  val realElement: PsiElement,
  val processor: AgpUpgradeComponentRefactoringProcessor,
  val usageType: UsageType?,
  val presentableText: String = ""
) : PsiElement by realElement, PsiElementNavigationItem {
  // We override this PsiElement method in order to have it stored in the PsiElementUsage (UsageInfo stores the navigation element, not
  // necessarily the element we pass to the UsageInfo constructor).
  override fun getNavigationElement(): PsiElement = this
  // We need to make sure that we wrap copies of us.
  override fun copy(): PsiElement = WrappedPsiElement(realElement.copy(), processor, usageType)
  // This is not the PsiElement we would get from parsing the text range in the element's file.
  override fun isPhysical(): Boolean = false

  // These Navigatable and NavigationItem methods can't just operate by delegation.
  override fun navigate(requestFocus: Boolean) = (realElement as? Navigatable)?.navigate(requestFocus) ?: Unit
  override fun canNavigate(): Boolean = (realElement as? Navigatable)?.canNavigate() ?: false
  override fun canNavigateToSource(): Boolean = (realElement as? Navigatable)?.canNavigateToSource() ?: false

  override fun getName(): String? = (realElement as? NavigationItem)?.getName()
  override fun getPresentation(): ItemPresentation? = object : ItemPresentation {
    override fun getPresentableText(): String? = this@WrappedPsiElement.presentableText
    override fun getLocationString(): String? = (realElement as? NavigationItem)?.presentation?.locationString
    override fun getIcon(unused: Boolean): Icon? = (realElement as? NavigationItem)?.presentation?.getIcon(unused)
  }

  // The target of our navigation will be the realElement.
  override fun getTargetElement(): PsiElement = realElement
}