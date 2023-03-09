/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger.concepts

import com.android.tools.idea.dagger.addDaggerAndHiltClasses
import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexClassWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexPsiWrapper
import com.android.tools.idea.kotlin.toPsiType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class ComponentAndModuleDaggerConceptTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  private fun runIndexer(wrapper: DaggerIndexClassWrapper): Map<String, Set<IndexValue>> =
    mutableMapOf<String, MutableSet<IndexValue>>().also { indexEntries ->
      ComponentAndModuleDaggerConcept.indexers.classIndexers.forEach {
        it.addIndexEntries(wrapper, indexEntries)
      }
    }

  @Test
  fun indexer_component() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        import dagger.Component;

        @Component(
          modules = { DripCoffeeModule.class, FrenchPressCoffeeModule.class },
          dependencies = { FilterComponent.class, SteamerComponent.class }
        )
        interface CoffeeShop {}
        """
          .trimIndent()
      ) as PsiJavaFile

    val element = myFixture.moveCaret("Coffee|Shop").parentOfType<PsiClass>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    val expectedModuleIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, "com.example.CoffeeShop"))
    val expectedDependencyIndexValue =
      setOf(
        ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, "com.example.CoffeeShop")
      )

    assertThat(entries)
      .containsExactly(
        "DripCoffeeModule",
        expectedModuleIndexValue,
        "FrenchPressCoffeeModule",
        expectedModuleIndexValue,
        "FilterComponent",
        expectedDependencyIndexValue,
        "SteamerComponent",
        expectedDependencyIndexValue
      )
  }

  @Test
  fun indexer_componentWithNoAnnotationArguments() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        import dagger.Component;

        @Component
        interface CoffeeShop {}
        """
          .trimIndent()
      ) as PsiJavaFile

    val element = myFixture.moveCaret("Coffee|Shop").parentOfType<PsiClass>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_wrongComponentAnnotation() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        import com.other.Component;

        @Component(
          modules = { DripCoffeeModule.class, FrenchPressCoffeeModule.class },
          dependencies = { FilterComponent.class, SteamerComponent.class }
        )
        interface CoffeeShop {}
        """
          .trimIndent()
      ) as PsiJavaFile

    val element = myFixture.moveCaret("Coffee|Shop").parentOfType<PsiClass>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_subcomponent() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        import dagger.Subcomponent;

        @Subcomponent(
          modules = { DripCoffeeModule.class, FrenchPressCoffeeModule.class }
        )
        interface CoffeeShop {}
        """
          .trimIndent()
      ) as PsiJavaFile

    val element = myFixture.moveCaret("Coffee|Shop").parentOfType<PsiClass>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    val expectedIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.SUBCOMPONENT_WITH_MODULE, "com.example.CoffeeShop"))

    assertThat(entries)
      .containsExactly(
        "DripCoffeeModule",
        expectedIndexValue,
        "FrenchPressCoffeeModule",
        expectedIndexValue
      )
  }

  @Test
  fun indexer_module() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        import dagger.Module;

        @Module(
          includes = { DripCoffeeModule.class, FrenchPressCoffeeModule.class },
          subcomponents = { FilterComponent.class, SteamerComponent.class }
        )
        interface CoffeeShop {}
        """
          .trimIndent()
      ) as PsiJavaFile

    val element = myFixture.moveCaret("Coffee|Shop").parentOfType<PsiClass>()!!
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    val expectedIncludesIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, "com.example.CoffeeShop"))
    val expectedSubcomponentsIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, "com.example.CoffeeShop"))

    assertThat(entries)
      .containsExactly(
        "DripCoffeeModule",
        expectedIncludesIndexValue,
        "FrenchPressCoffeeModule",
        expectedIncludesIndexValue,
        "FilterComponent",
        expectedSubcomponentsIndexValue,
        "SteamerComponent",
        expectedSubcomponentsIndexValue
      )
  }

  @Test
  fun classIndexValue_serialization() {
    val indexValues =
      setOf(
        ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, "abc"),
        ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, "def"),
        ClassIndexValue(IndexValue.DataType.SUBCOMPONENT_WITH_MODULE, "ghi"),
        ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, "jkl"),
        ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, "mno"),
      )
    assertThat(serializeAndDeserializeIndexValues(indexValues))
      .containsExactlyElementsIn(indexValues)
  }

  @Test
  fun componentIndexValue_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.Component
      import dagger.Module

      @Component(
        modules = [CoffeeShopModule::class],
        dependencies = [DependencyComponent::class]
        )
      interface CoffeeShopComponent

      @Module
      interface CoffeeShopModule

      @Component
      interface DependencyComponent
      """
        .trimIndent()
    )

    val modulePsiType =
      myFixture.moveCaret("interface CoffeeShop|Module").parentOfType<KtClass>()!!.toPsiType()!!
    val dependencyPsiType =
      myFixture.moveCaret("interface Dependency|Component").parentOfType<KtClass>()!!.toPsiType()!!

    val componentClass =
      myFixture.moveCaret("interface CoffeeShop|Component").parentOfType<KtClass>()!!

    val moduleIndexValue =
      ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, "com.example.CoffeeShopComponent")
    val dependencyIndexValue =
      ClassIndexValue(
        IndexValue.DataType.COMPONENT_WITH_DEPENDENCY,
        "com.example.CoffeeShopComponent"
      )

    val resolvedModuleIndexValue =
      moduleIndexValue
        .resolveToDaggerElements(modulePsiType, myFixture.project, myFixture.project.projectScope())
        .single()
    assertThat(resolvedModuleIndexValue.psiElement).isEqualTo(componentClass)
    assertThat(resolvedModuleIndexValue.daggerType).isEqualTo(DaggerElement.Type.COMPONENT)

    val resolvedDependencyIndexValue =
      moduleIndexValue
        .resolveToDaggerElements(modulePsiType, myFixture.project, myFixture.project.projectScope())
        .single()
    assertThat(resolvedDependencyIndexValue.psiElement).isEqualTo(componentClass)
    assertThat(resolvedDependencyIndexValue.daggerType).isEqualTo(DaggerElement.Type.COMPONENT)

    // When the psi types are swapped, no matches should be returned.
    assertThat(
        moduleIndexValue.resolveToDaggerElements(
          dependencyPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
    assertThat(
        dependencyIndexValue.resolveToDaggerElements(
          modulePsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
  }

  @Test
  fun subcomponentIndexValue_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.Module
      import dagger.Subcomponent

      @Subcomponent(modules = [CoffeeShopModule::class])
      interface CoffeeShopSubcomponent

      @Module
      interface CoffeeShopModule
      """
        .trimIndent()
    )

    val modulePsiType =
      myFixture.moveCaret("interface CoffeeShop|Module").parentOfType<KtClass>()!!.toPsiType()!!

    val subcomponentClass =
      myFixture.moveCaret("interface CoffeeShop|Subcomponent").parentOfType<KtClass>()!!

    val indexValue =
      ClassIndexValue(
        IndexValue.DataType.SUBCOMPONENT_WITH_MODULE,
        "com.example.CoffeeShopSubcomponent"
      )

    val resolvedIndexValue =
      indexValue
        .resolveToDaggerElements(modulePsiType, myFixture.project, myFixture.project.projectScope())
        .single()
    assertThat(resolvedIndexValue.psiElement).isEqualTo(subcomponentClass)
    assertThat(resolvedIndexValue.daggerType).isEqualTo(DaggerElement.Type.SUBCOMPONENT)
  }

  @Test
  fun moduleIndexValue_resolveToPsiElements_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.Module
      import dagger.Subcomponent

      @Module(
        includes = [CoffeeShopIncludedModule::class],
        subcomponents = [CoffeeShopSubcomponent::class]
      )
      interface CoffeeShopModule

      @Subcomponent
      interface CoffeeShopSubcomponent

      @Module
      interface CoffeeShopIncludedModule
      """
        .trimIndent()
    )

    val includedModulePsiType =
      myFixture
        .moveCaret("interface CoffeeShopIncluded|Module")
        .parentOfType<KtClass>()!!
        .toPsiType()!!
    val subcomponentPsiType =
      myFixture
        .moveCaret("interface CoffeeShop|Subcomponent")
        .parentOfType<KtClass>()!!
        .toPsiType()!!

    val moduleClass = myFixture.moveCaret("interface CoffeeShop|Module").parentOfType<KtClass>()!!

    val includeIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, "com.example.CoffeeShopModule")
    val subcomponentIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, "com.example.CoffeeShopModule")

    val resolvedIncludeIndexValue =
      includeIndexValue
        .resolveToDaggerElements(
          includedModulePsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
        .single()
    assertThat(resolvedIncludeIndexValue.psiElement).isEqualTo(moduleClass)
    assertThat(resolvedIncludeIndexValue.daggerType).isEqualTo(DaggerElement.Type.MODULE)

    val resolvedSubcomponentIndexValue =
      subcomponentIndexValue
        .resolveToDaggerElements(
          subcomponentPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
        .single()
    assertThat(resolvedSubcomponentIndexValue.psiElement).isEqualTo(moduleClass)
    assertThat(resolvedSubcomponentIndexValue.daggerType).isEqualTo(DaggerElement.Type.MODULE)

    // When the psi types are swapped, no matches should be returned.
    assertThat(
        includeIndexValue.resolveToDaggerElements(
          subcomponentPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
    assertThat(
        subcomponentIndexValue.resolveToDaggerElements(
          includedModulePsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
  }

  @Test
  fun componentIndexValue_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Component;
      import dagger.Module;

      @Component(
        modules = { CoffeeShopModule.class }, // Array initializer
        dependencies = DependencyComponent.class // Single value without array syntax
        )
      public interface CoffeeShopComponent {}

      @Module
      public interface CoffeeShopModule {}

      @Component
      public interface DependencyComponent {}
      """
        .trimIndent()
    )

    val modulePsiType =
      myFixture.moveCaret("interface CoffeeShop|Module").parentOfType<PsiClass>()!!.getPsiType()!!
    val dependencyPsiType =
      myFixture
        .moveCaret("interface Dependency|Component")
        .parentOfType<PsiClass>()!!
        .getPsiType()!!

    val componentClass =
      myFixture.moveCaret("interface CoffeeShop|Component").parentOfType<PsiClass>()!!

    val moduleIndexValue =
      ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, "com.example.CoffeeShopComponent")
    val dependencyIndexValue =
      ClassIndexValue(
        IndexValue.DataType.COMPONENT_WITH_DEPENDENCY,
        "com.example.CoffeeShopComponent"
      )

    val resolvedModuleIndexValue =
      moduleIndexValue
        .resolveToDaggerElements(modulePsiType, myFixture.project, myFixture.project.projectScope())
        .single()
    assertThat(resolvedModuleIndexValue.psiElement).isEqualTo(componentClass)
    assertThat(resolvedModuleIndexValue.daggerType).isEqualTo(DaggerElement.Type.COMPONENT)

    val resolvedDependencyIndexValue =
      moduleIndexValue
        .resolveToDaggerElements(modulePsiType, myFixture.project, myFixture.project.projectScope())
        .single()
    assertThat(resolvedDependencyIndexValue.psiElement).isEqualTo(componentClass)
    assertThat(resolvedDependencyIndexValue.daggerType).isEqualTo(DaggerElement.Type.COMPONENT)

    // When the psi types are swapped, no matches should be returned.
    assertThat(
        moduleIndexValue.resolveToDaggerElements(
          dependencyPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
    assertThat(
        dependencyIndexValue.resolveToDaggerElements(
          modulePsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
  }

  @Test
  fun subcomponentIndexValue_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Module;
      import dagger.Subcomponent;

      @Subcomponent(modules = CoffeeShopModule.class)
      public interface CoffeeShopSubcomponent {}

      @Module
      public interface CoffeeShopModule {}
      """
        .trimIndent()
    )

    val modulePsiType =
      myFixture.moveCaret("interface CoffeeShop|Module").parentOfType<PsiClass>()!!.getPsiType()!!

    val subcomponentClass =
      myFixture.moveCaret("interface CoffeeShop|Subcomponent").parentOfType<PsiClass>()!!

    val indexValue =
      ClassIndexValue(
        IndexValue.DataType.SUBCOMPONENT_WITH_MODULE,
        "com.example.CoffeeShopSubcomponent"
      )

    val resolvedIndexValue =
      indexValue
        .resolveToDaggerElements(modulePsiType, myFixture.project, myFixture.project.projectScope())
        .single()
    assertThat(resolvedIndexValue.psiElement).isEqualTo(subcomponentClass)
    assertThat(resolvedIndexValue.daggerType).isEqualTo(DaggerElement.Type.SUBCOMPONENT)
  }

  @Test
  fun moduleIndexValue_resolveToPsiElements_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Module;
      import dagger.Subcomponent;

      @Module(
        includes = CoffeeShopIncludedModule.class, // Single value without array syntax
        subcomponents = { CoffeeShopSubcomponent.class } // Array initializer
      )
      public interface CoffeeShopModule {}

      @Subcomponent
      public interface CoffeeShopSubcomponent {}

      @Module
      public interface CoffeeShopIncludedModule {}
      """
        .trimIndent()
    )

    val includedModulePsiType =
      myFixture
        .moveCaret("interface CoffeeShopIncluded|Module")
        .parentOfType<PsiClass>()!!
        .getPsiType()!!
    val subcomponentPsiType =
      myFixture
        .moveCaret("interface CoffeeShop|Subcomponent")
        .parentOfType<PsiClass>()!!
        .getPsiType()!!

    val moduleClass = myFixture.moveCaret("interface CoffeeShop|Module").parentOfType<PsiClass>()!!

    val includeIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, "com.example.CoffeeShopModule")
    val subcomponentIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, "com.example.CoffeeShopModule")

    val resolvedIncludeIndexValue =
      includeIndexValue
        .resolveToDaggerElements(
          includedModulePsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
        .single()
    assertThat(resolvedIncludeIndexValue.psiElement).isEqualTo(moduleClass)
    assertThat(resolvedIncludeIndexValue.daggerType).isEqualTo(DaggerElement.Type.MODULE)

    val resolvedSubcomponentIndexValue =
      subcomponentIndexValue
        .resolveToDaggerElements(
          subcomponentPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
        .single()
    assertThat(resolvedSubcomponentIndexValue.psiElement).isEqualTo(moduleClass)
    assertThat(resolvedSubcomponentIndexValue.daggerType).isEqualTo(DaggerElement.Type.MODULE)

    // When the psi types are swapped, no matches should be returned.
    assertThat(
        includeIndexValue.resolveToDaggerElements(
          subcomponentPsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
    assertThat(
        subcomponentIndexValue.resolveToDaggerElements(
          includedModulePsiType,
          myFixture.project,
          myFixture.project.projectScope()
        )
      )
      .isEmpty()
  }

  @Test
  fun daggerElementIdentifiers_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.Component
      import dagger.Module
      import dagger.Subcomponent

      @Component
      interface CoffeeShopComponent

      @Subcomponent
      interface CoffeeShopSubcomponent

      @Module
      interface CoffeeShopModule
      """
        .trimIndent()
    )

    val componentDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Component").parentOfType<KtClass>()!!
      )
    assertThat(componentDaggerElement).isNotNull()
    assertThat(componentDaggerElement!!.daggerType).isEqualTo(DaggerElement.Type.COMPONENT)

    val subcomponentDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Subcomponent").parentOfType<KtClass>()!!
      )
    assertThat(subcomponentDaggerElement).isNotNull()
    assertThat(subcomponentDaggerElement!!.daggerType).isEqualTo(DaggerElement.Type.SUBCOMPONENT)

    val moduleDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Module").parentOfType<KtClass>()!!
      )
    assertThat(moduleDaggerElement).isNotNull()
    assertThat(moduleDaggerElement!!.daggerType).isEqualTo(DaggerElement.Type.MODULE)
  }

  @Test
  fun daggerElementIdentifiers_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Component;
      import dagger.Module;
      import dagger.Subcomponent;

      @Component
      public interface CoffeeShopComponent {}

      @Subcomponent
      public interface CoffeeShopSubcomponent {}

      @Module
      public interface CoffeeShopModule {}
      """
        .trimIndent()
    )

    val componentDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Component").parentOfType<PsiClass>()!!
      )
    assertThat(componentDaggerElement).isNotNull()
    assertThat(componentDaggerElement!!.daggerType).isEqualTo(DaggerElement.Type.COMPONENT)

    val subcomponentDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Subcomponent").parentOfType<PsiClass>()!!
      )
    assertThat(subcomponentDaggerElement).isNotNull()
    assertThat(subcomponentDaggerElement!!.daggerType).isEqualTo(DaggerElement.Type.SUBCOMPONENT)

    val moduleDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Module").parentOfType<PsiClass>()!!
      )
    assertThat(moduleDaggerElement).isNotNull()
    assertThat(moduleDaggerElement!!.daggerType).isEqualTo(DaggerElement.Type.MODULE)
  }

  @Test
  fun componentDaggerElementBase_getIncludedModulesAndSubcomponents_kotlin() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.Component
      import dagger.Module
      import dagger.Subcomponent

      @Component(modules = [ CoffeeShopModule::class, NotAModule::class ])
      interface CoffeeShopComponent

      @Module(subcomponents = [ CoffeeShopSubcomponent::class, NotASubcomponent::class ])
      interface CoffeeShopModule
      interface NotAModule

      @Subcomponent
      interface CoffeeShopSubcomponent
      interface NotASubcomponent
      """
        .trimIndent()
    )

    val componentPsiElement = myFixture.moveCaret("CoffeeShop|Component").parentOfType<KtClass>()!!

    val componentDaggerElementBase =
      FakeComponentDaggerElementBase(
        componentPsiElement,
        DaggerElement.Type.COMPONENT,
        "dagger.Component"
      )
    val modulesAndSubcomponents =
      componentDaggerElementBase.callGetIncludedModulesAndSubcomponents()

    assertThat(modulesAndSubcomponents).hasSize(2)

    assertThat(modulesAndSubcomponents[0].first.daggerType).isEqualTo(DaggerElement.Type.MODULE)
    assertThat(modulesAndSubcomponents[1].first.daggerType)
      .isEqualTo(DaggerElement.Type.SUBCOMPONENT)

    assertThat(modulesAndSubcomponents[0].first.psiElement.text).contains("CoffeeShopModule")
    assertThat(modulesAndSubcomponents[1].first.psiElement.text).contains("CoffeeShopSubcomponent")

    assertThat(modulesAndSubcomponents[0].second).isEqualTo("Modules included")
    assertThat(modulesAndSubcomponents[1].second).isEqualTo("Subcomponents")
  }

  @Test
  fun componentDaggerElementBase_getIncludedModulesAndSubcomponents_java() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Component;
      import dagger.Module;
      import dagger.Subcomponent;

      @Component(modules = { CoffeeShopModule.class, NotAModule.class })
      public interface CoffeeShopComponent {}

      @Module(subcomponents = { CoffeeShopSubcomponent.class, NotASubcomponent.class })
      public interface CoffeeShopModule {}
      public interface NotAModule {}

      @Subcomponent
      public interface CoffeeShopSubcomponent {}
      public interface NotASubcomponent {}
      """
        .trimIndent()
    )

    val componentPsiElement = myFixture.moveCaret("CoffeeShop|Component").parentOfType<PsiClass>()!!

    val componentDaggerElementBase =
      FakeComponentDaggerElementBase(
        componentPsiElement,
        DaggerElement.Type.COMPONENT,
        "dagger.Component"
      )
    val modulesAndSubcomponents =
      componentDaggerElementBase.callGetIncludedModulesAndSubcomponents()

    assertThat(modulesAndSubcomponents).hasSize(2)

    assertThat(modulesAndSubcomponents[0].first.daggerType).isEqualTo(DaggerElement.Type.MODULE)
    assertThat(modulesAndSubcomponents[1].first.daggerType)
      .isEqualTo(DaggerElement.Type.SUBCOMPONENT)

    assertThat(modulesAndSubcomponents[0].first.psiElement.text).contains("CoffeeShopModule")
    assertThat(modulesAndSubcomponents[1].first.psiElement.text).contains("CoffeeShopSubcomponent")

    assertThat(modulesAndSubcomponents[0].second).isEqualTo("Modules included")
    assertThat(modulesAndSubcomponents[1].second).isEqualTo("Subcomponents")
  }

  @Test
  fun componentDaggerElementBase_getIncludedModulesAndSubcomponents_javaSingleInitializer() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Component;
      import dagger.Module;
      import dagger.Subcomponent;

      @Component(modules = CoffeeShopModule.class)
      public interface CoffeeShopComponent {}

      @Module(subcomponents = CoffeeShopSubcomponent.class)
      public interface CoffeeShopModule {}

      @Subcomponent
      public interface CoffeeShopSubcomponent {}
      """
        .trimIndent()
    )

    val componentPsiElement = myFixture.moveCaret("CoffeeShop|Component").parentOfType<PsiClass>()!!

    val componentDaggerElementBase =
      FakeComponentDaggerElementBase(
        componentPsiElement,
        DaggerElement.Type.COMPONENT,
        "dagger.Component"
      )
    val modulesAndSubcomponents =
      componentDaggerElementBase.callGetIncludedModulesAndSubcomponents()

    assertThat(modulesAndSubcomponents).hasSize(2)

    assertThat(modulesAndSubcomponents[0].first.daggerType).isEqualTo(DaggerElement.Type.MODULE)
    assertThat(modulesAndSubcomponents[1].first.daggerType)
      .isEqualTo(DaggerElement.Type.SUBCOMPONENT)

    assertThat(modulesAndSubcomponents[0].first.psiElement.text).contains("CoffeeShopModule")
    assertThat(modulesAndSubcomponents[1].first.psiElement.text).contains("CoffeeShopSubcomponent")

    assertThat(modulesAndSubcomponents[0].second).isEqualTo("Modules included")
    assertThat(modulesAndSubcomponents[1].second).isEqualTo("Subcomponents")
  }

  private class FakeComponentDaggerElementBase(
    psiElement: PsiElement,
    daggerType: Type,
    override val definingAnnotationName: String
  ) : ComponentDaggerElementBase(psiElement, daggerType) {

    override fun getRelatedDaggerElements() = throw NotImplementedError()

    fun callGetIncludedModulesAndSubcomponents() = getIncludedModulesAndSubcomponents()
  }
}
