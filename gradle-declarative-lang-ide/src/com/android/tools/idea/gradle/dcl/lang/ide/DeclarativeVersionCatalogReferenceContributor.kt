/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBare
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeProperty
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePsiFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeQualified
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeRecursiveVisitor
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType


class DeclarativeVersionCatalogReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(DeclarativeProperty::class.java),
                                        DeclarativeVersionCatalogReferenceProvider()
    )
  }
}

class DeclarativeVersionCatalogReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is DeclarativeProperty) return emptyArray()
    if (element.parent is DeclarativeProperty || element.parent is DeclarativeAssignment) return emptyArray()
    if (!DeclarativeIdeSupport.isEnabled()) return emptyArray()
    lateinit var fileIdentifier: DeclarativeIdentifier
    element.accept(object : DeclarativeRecursiveVisitor() {
      override fun visitBare(o: DeclarativeBare) {
        fileIdentifier = o.field
        super.visitBare(o)
      }
    })

    val aliases = element.module?.let{ getVersionCatalogFiles(it).keys } ?: listOf()
    if(aliases.isEmpty() || !aliases.contains(fileIdentifier.name)) return arrayOf()

    val identifiers: MutableList<DeclarativeIdentifier> = mutableListOf()
    element.accept(object : DeclarativeRecursiveVisitor() {
      override fun visitQualified(o: DeclarativeQualified) {
        identifiers.add(0, o.field)
        super.visitQualified(o)
      }
    })
    val fileReference = VersionCatalogFileReference(element, fileIdentifier)
    val tableIdentifier = identifiers.getOrNull(0)?.takeIf { setOf("plugins", "versions", "bundles").contains(it.name) }
    val tableReference = tableIdentifier?.let { VersionCatalogTableReference(element, fileIdentifier, it) }
    val entryIdentifiers = when (tableIdentifier) {
      null -> identifiers
      else -> identifiers.drop(1)
    }
    val entryReference = entryIdentifiers.takeIf { it.isNotEmpty() }
      ?.let { VersionCatalogEntryReference(element, fileIdentifier, tableIdentifier, entryIdentifiers) }
    return listOfNotNull(fileReference, tableReference, entryReference).toTypedArray()
  }


  class VersionCatalogFileReference(private val element: DeclarativeProperty,
                                    private val identifier: DeclarativeIdentifier) : PsiReference {
    override fun getElement() = element
    override fun getRangeInElement(): TextRange = identifier.textRange.shiftLeft(element.textRange.startOffset)
    override fun resolve(): PsiElement? = identifier.getVersionCatalogFile()
    override fun getCanonicalText(): String = "gradle/${identifier.name!!}.versions.toml"
    override fun handleElementRename(newElementName: String): PsiElement {
      identifier.setName(newElementName)
      return element
    }

    override fun bindToElement(newElement: PsiElement): PsiElement {
      if (newElement !is PsiFile) return element
      identifier.setName(newElement.name.removeSuffix(".versions.toml"))
      return element
    }

    override fun isReferenceTo(element: PsiElement): Boolean = resolve() == element
    override fun isSoft(): Boolean = true
  }

  class VersionCatalogTableReference(
    private val element: DeclarativeProperty,
    private val catalogIdentifier: DeclarativeIdentifier,
    private val identifier: DeclarativeIdentifier
  ) : PsiReference {
    override fun getElement() = element
    override fun getRangeInElement(): TextRange = identifier.textRange.shiftLeft(element.textRange.startOffset)
    override fun resolve(): PsiElement? =
      catalogIdentifier.getVersionCatalogFile()?.findDescendantOfType<PsiNamedElement> { it.name == identifier.name }

    override fun getCanonicalText(): String = identifier.name!!
    override fun handleElementRename(newElementName: String): PsiElement {
      identifier.setName(newElementName)
      return element
    }

    override fun bindToElement(newElement: PsiElement): PsiElement {
      if (newElement !is PsiNamedElement) return element
      newElement.name?.let { identifier.setName(it) }
      return element
    }

    override fun isReferenceTo(element: PsiElement): Boolean = resolve() == element
    override fun isSoft(): Boolean = false
  }

  class VersionCatalogEntryReference(
    private val element: DeclarativeProperty,
    private val catalogIdentifier: DeclarativeIdentifier,
    private val tableIdentifier: DeclarativeIdentifier?,
    private val identifiers: List<DeclarativeIdentifier>
  ) : PsiReference {
    override fun getElement() = element
    override fun getRangeInElement(): TextRange = catalogIdentifier.textRange.startOffset.let { offset ->
      TextRange(identifiers.first().textRange.startOffset, identifiers.last().textRange.endOffset).shiftLeft(offset)
    }

    override fun resolve(): PsiElement? =
      catalogIdentifier.getVersionCatalogFile()
        ?.findDescendantOfType<PsiNamedElement> { it.name == (tableIdentifier?.name ?: "libraries") }
        ?.parent?.parent?.parent
        ?.findDescendantOfType<PsiNamedElement> { it.name?.split(Regex("[-_]")) == identifiers.map { i -> i.name } }

    override fun getCanonicalText(): String = StringBuilder().run {
      append(catalogIdentifier.name)
      tableIdentifier?.name?.let { append(".$it") }
      identifiers.joinTo(this, separator = "-", prefix = ".") { it.name!! }
      toString()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
      val string = StringBuilder().run {
        append(catalogIdentifier.name)
        tableIdentifier?.name?.let { append(".$it") }
        newElementName.split(Regex("[-_]")).joinTo(this, separator = "-", prefix = ".")
        toString()
      }
      return element.replace(DeclarativePsiFactory(element.project).createProperty(string))
    }

    override fun bindToElement(newElement: PsiElement): PsiElement {
      if (newElement !is PsiNamedElement) return element
      return newElement.name?.let { handleElementRename(it) } ?: element
    }

    override fun isReferenceTo(element: PsiElement): Boolean = resolve() == element
    override fun isSoft(): Boolean = false
  }

  companion object {
    fun DeclarativeIdentifier.getVersionCatalogFile(): PsiFile? =
      module?.let {
        getVersionCatalogFiles(it)[name]?.let { virtualFile ->
          PsiManager.getInstance(project).findFile(virtualFile)
        }
      }
  }

}