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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
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
  private lateinit var myProject: Project

  @Before
  fun setup() {
    myFixture = projectRule.fixture
    myProject = myFixture.project
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

    val element: PsiClass = myFixture.findParentElement("Coffee|Shop")
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    val expectedModuleIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, COFFEE_SHOP_ID))
    val expectedDependencyIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, COFFEE_SHOP_ID))

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

    val element: PsiClass = myFixture.findParentElement("Coffee|Shop")
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

    val element: PsiClass = myFixture.findParentElement("Coffee|Shop")
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    assertThat(entries).isEmpty()
  }

  @Test
  fun indexer_componentAsClass() {
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
        class CoffeeShop {}
        """
          .trimIndent()
      ) as PsiJavaFile

    val element: PsiClass = myFixture.findParentElement("Coffee|Shop")
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    val expectedModuleIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, COFFEE_SHOP_ID))
    val expectedDependencyIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, COFFEE_SHOP_ID))

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
  fun indexer_componentAsObject() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import dagger.Component

        @Component(
          modules = [ DripCoffeeModule::class, FrenchPressCoffeeModule::class ],
          dependencies = [ FilterComponent::class, SteamerComponent::class ]
        )
        object CoffeeShop {}
        """
          .trimIndent()
      ) as KtFile

    val element: KtClassOrObject = myFixture.findParentElement("Coffee|Shop")
    val entries = runIndexer(DaggerIndexPsiWrapper.KotlinFactory(psiFile).of(element))

    val expectedModuleIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, COFFEE_SHOP_ID))
    val expectedDependencyIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, COFFEE_SHOP_ID))

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
  fun indexer_componentOnEnum_kotlin() {
    val psiFile =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        // language=kotlin
        """
        package com.example

        import dagger.Component

        @Component
        enum class NotAComponent {
          @Component
          ONE
        }
        """
          .trimIndent()
      ) as KtFile

    assertThat(ComponentAndModuleDaggerConcept.indexers.runIndexerOn(psiFile)).isEmpty()
  }

  @Test
  fun indexer_componentOnEnum_java() {
    val psiFile =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        // language=java
        """
        package com.example;

        import dagger.Component;

        @Component
        enum NotAComponent {
          @Component
          ONE
        }
        """
          .trimIndent()
      ) as PsiJavaFile

    assertThat(ComponentAndModuleDaggerConcept.indexers.runIndexerOn(psiFile)).isEmpty()
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

    val element: PsiClass = myFixture.findParentElement("Coffee|Shop")
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    val expectedIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.SUBCOMPONENT_WITH_MODULE, COFFEE_SHOP_ID))

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

    val element: PsiClass = myFixture.findParentElement("Coffee|Shop")
    val entries = runIndexer(DaggerIndexPsiWrapper.JavaFactory(psiFile).of(element))

    val expectedIncludesIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, COFFEE_SHOP_ID))
    val expectedSubcomponentsIndexValue =
      setOf(ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, COFFEE_SHOP_ID))

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
        ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, ClassId.fromString("a/b.c")),
        ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, ClassId.fromString("d/e.f")),
        ClassIndexValue(IndexValue.DataType.SUBCOMPONENT_WITH_MODULE, ClassId.fromString("g/h.i")),
        ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, ClassId.fromString("j/k.l")),
        ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, ClassId.fromString("m/n.o")),
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
      interface CoffeeShopComponentInterface

      @Component(
        modules = [CoffeeShopModule::class],
        dependencies = [DependencyComponent::class]
        )
      class CoffeeShopComponentClass

      @Component(
        modules = [CoffeeShopModule::class],
        dependencies = [DependencyComponent::class]
        )
      object CoffeeShopComponentObject

      @Module
      interface CoffeeShopModule

      @Component
      interface DependencyComponent
      """
        .trimIndent()
    )

    val components =
      listOf(
        "CoffeeShopComponentInterface",
        "CoffeeShopComponentClass",
        "CoffeeShopComponentObject"
      )
    for (component in components) {
      val componentElement = myFixture.findParentElement<KtClassOrObject>("|$component")
      val classId = ClassId(FqName("com.example"), Name.identifier(component))

      val moduleIndexValue = ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, classId)
      val dependencyIndexValue =
        ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, classId)

      assertWithMessage("${classId.asString()} - module")
        .that(
          moduleIndexValue
            .resolveToDaggerElements(myProject, myProject.projectScope())
            .firstOrNull()
        )
        .isEqualTo(ComponentDaggerElement(componentElement))

      assertWithMessage("${classId.asString()} - dependency")
        .that(
          dependencyIndexValue
            .resolveToDaggerElements(myProject, myProject.projectScope())
            .firstOrNull()
        )
        .isEqualTo(ComponentDaggerElement(componentElement))
    }
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

    val subcomponentClass: KtClass =
      myFixture.findParentElement("interface CoffeeShop|Subcomponent")

    val indexValue =
      ClassIndexValue(IndexValue.DataType.SUBCOMPONENT_WITH_MODULE, COFFEE_SHOP_SUBCOMPONENT_ID)

    assertThat(indexValue.resolveToDaggerElements(myProject, myProject.projectScope()).single())
      .isEqualTo(SubcomponentDaggerElement(subcomponentClass))
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

    val moduleClass: KtClass = myFixture.findParentElement("interface CoffeeShop|Module")

    val includeIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, COFFEE_SHOP_MODULE_ID)
    val subcomponentIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, COFFEE_SHOP_MODULE_ID)

    assertThat(
        includeIndexValue.resolveToDaggerElements(myProject, myProject.projectScope()).single()
      )
      .isEqualTo(ModuleDaggerElement(moduleClass))

    assertThat(
        subcomponentIndexValue.resolveToDaggerElements(myProject, myProject.projectScope()).single()
      )
      .isEqualTo(ModuleDaggerElement(moduleClass))
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

    val componentClass: PsiClass = myFixture.findParentElement("interface CoffeeShop|Component")

    val moduleIndexValue =
      ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, COFFEE_SHOP_COMPONENT_ID)
    val dependencyIndexValue =
      ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_DEPENDENCY, COFFEE_SHOP_COMPONENT_ID)

    assertThat(
        moduleIndexValue.resolveToDaggerElements(myProject, myProject.projectScope()).single()
      )
      .isEqualTo(ComponentDaggerElement(componentClass))

    assertThat(
        dependencyIndexValue.resolveToDaggerElements(myProject, myProject.projectScope()).single()
      )
      .isEqualTo(ComponentDaggerElement(componentClass))
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

    val subcomponentClass: PsiClass =
      myFixture.findParentElement("interface CoffeeShop|Subcomponent")

    val indexValue =
      ClassIndexValue(IndexValue.DataType.SUBCOMPONENT_WITH_MODULE, COFFEE_SHOP_SUBCOMPONENT_ID)

    assertThat(indexValue.resolveToDaggerElements(myProject, myProject.projectScope()).single())
      .isEqualTo(SubcomponentDaggerElement(subcomponentClass))
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

    val moduleClass: PsiClass = myFixture.findParentElement("interface CoffeeShop|Module")

    val includeIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, COFFEE_SHOP_MODULE_ID)
    val subcomponentIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, COFFEE_SHOP_MODULE_ID)

    assertThat(
        includeIndexValue.resolveToDaggerElements(myProject, myProject.projectScope()).single()
      )
      .isEqualTo(ModuleDaggerElement(moduleClass))

    assertThat(
        subcomponentIndexValue.resolveToDaggerElements(myProject, myProject.projectScope()).single()
      )
      .isEqualTo(ModuleDaggerElement(moduleClass))
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
      interface CoffeeShopComponentInterface

      @Component
      class CoffeeShopComponentClass

      @Component
      object CoffeeShopComponentObject

      @Subcomponent
      interface CoffeeShopSubcomponent

      @Module
      interface CoffeeShopModule
      """
        .trimIndent()
    )

    val componentInterfaceDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("CoffeeShop|ComponentInterface")
      )
    assertThat(componentInterfaceDaggerElement).isInstanceOf(ComponentDaggerElement::class.java)

    val componentClassDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("CoffeeShop|ComponentClass")
      )
    assertThat(componentClassDaggerElement).isInstanceOf(ComponentDaggerElement::class.java)

    val componentObjectDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("CoffeeShop|ComponentObject")
      )
    assertThat(componentObjectDaggerElement).isInstanceOf(ComponentDaggerElement::class.java)

    val subcomponentDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.findParentElement<KtClass>("CoffeeShop|Subcomponent")
      )
    assertThat(subcomponentDaggerElement).isInstanceOf(SubcomponentDaggerElement::class.java)

    val moduleDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.findParentElement<KtClass>("CoffeeShop|Module")
      )
    assertThat(moduleDaggerElement).isInstanceOf(ModuleDaggerElement::class.java)
  }

  @Test
  fun daggerElementIdentifiers_kotlin_enums() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
      package com.example

      import dagger.Component

      @Component
      enum class NotAComponent {
        @Component
        ONE
      }
      """
        .trimIndent()
    )

    assertThat(
        ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
          myFixture.findParentElement<KtClassOrObject>("NotA|Component")
        )
      )
      .isNull()

    assertThat(
        ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
          myFixture.findParentElement<KtClassOrObject>("ON|E")
        )
      )
      .isNull()
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
        myFixture.findParentElement<PsiClass>("CoffeeShop|Component")
      )
    assertThat(componentDaggerElement).isInstanceOf(ComponentDaggerElement::class.java)

    val subcomponentDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.findParentElement<PsiClass>("CoffeeShop|Subcomponent")
      )
    assertThat(subcomponentDaggerElement).isInstanceOf(SubcomponentDaggerElement::class.java)

    val moduleDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.findParentElement<PsiClass>("CoffeeShop|Module")
      )
    assertThat(moduleDaggerElement).isInstanceOf(ModuleDaggerElement::class.java)
  }

  @Test
  fun daggerElementIdentifiers_java_enums() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
      package com.example;

      import dagger.Component;

      @Component
      enum NotAComponent {
        @Component
        ONE
      }
      """
        .trimIndent()
    )

    assertThat(
        ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
          myFixture.findParentElement<PsiClass>("NotA|Component")
        )
      )
      .isNull()

    assertThat(
        ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
          myFixture.findParentElement<PsiClass>("ON|E")
        )
      )
      .isNull()
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

    val componentPsiElement: KtClass = myFixture.findParentElement("interface CoffeeShop|Component")

    val coffeeShopModuleElement: KtClass =
      myFixture.findParentElement("interface CoffeeShop|Module")
    val coffeeShopSubcomponentElement: KtClass =
      myFixture.findParentElement("interface CoffeeShop|Subcomponent")

    val componentDaggerElement = ComponentDaggerElement(componentPsiElement)

    assertThat(componentDaggerElement.getIncludedModulesAndSubcomponents())
      .containsExactly(
        DaggerRelatedElement(
          ModuleDaggerElement(coffeeShopModuleElement),
          "Modules included",
          "navigate.to.included.module"
        ),
        DaggerRelatedElement(
          SubcomponentDaggerElement(coffeeShopSubcomponentElement),
          "Subcomponents",
          "navigate.to.subcomponent"
        ),
      )
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

    val componentPsiElement: PsiClass =
      myFixture.findParentElement("interface CoffeeShop|Component")

    val coffeeShopModuleElement: PsiClass =
      myFixture.findParentElement("interface CoffeeShop|Module")
    val coffeeShopSubcomponentElement: PsiClass =
      myFixture.findParentElement("interface CoffeeShop|Subcomponent")

    val componentDaggerElement = ComponentDaggerElement(componentPsiElement)

    assertThat(componentDaggerElement.getIncludedModulesAndSubcomponents())
      .containsExactly(
        DaggerRelatedElement(
          ModuleDaggerElement(coffeeShopModuleElement),
          "Modules included",
          "navigate.to.included.module"
        ),
        DaggerRelatedElement(
          SubcomponentDaggerElement(coffeeShopSubcomponentElement),
          "Subcomponents",
          "navigate.to.subcomponent"
        ),
      )
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

    val componentDaggerElement =
      ComponentDaggerElement(
        myFixture.findParentElement<PsiClass>("interface CoffeeShop|Component")
      )
    val moduleDaggerElement =
      ModuleDaggerElement(myFixture.findParentElement<PsiClass>("interface CoffeeShop|Module"))
    val subcomponentDaggerElement =
      SubcomponentDaggerElement(
        myFixture.findParentElement<PsiClass>("interface CoffeeShop|Subcomponent")
      )

    assertThat(componentDaggerElement.getIncludedModulesAndSubcomponents())
      .containsExactly(
        DaggerRelatedElement(
          moduleDaggerElement,
          "Modules included",
          "navigate.to.included.module"
        ),
        DaggerRelatedElement(subcomponentDaggerElement, "Subcomponents", "navigate.to.subcomponent")
      )
  }

  @Test
  fun component_getRelatedDaggerElements() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import dagger.Component
          import dagger.Module
          import dagger.Subcomponent

          @Component(
            modules = [MyModule::class],
            dependencies = [IncludedComponent::class],
          )
          interface TopLevelComponent

          @Module(
            subcomponents = [MySubcomponent::class],
          )
          interface MyModule

          @Subcomponent
          interface MySubcomponent

          @Component
          interface IncludedComponent
          """
            .trimIndent()
        )
        .virtualFile
    )

    val topLevelComponentDaggerElement =
      ComponentDaggerElement(myFixture.findParentElement<KtClass>("interface TopLevel|Component"))

    val myModuleDaggerElement =
      ModuleDaggerElement(myFixture.findParentElement<KtClass>("interface My|Module"))

    val mySubcomponentDaggerElement =
      SubcomponentDaggerElement(myFixture.findParentElement<KtClass>("interface My|Subcomponent"))

    val includedComponentDaggerElement =
      ComponentDaggerElement(myFixture.findParentElement<KtClass>("interface Included|Component"))

    assertThat(topLevelComponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          myModuleDaggerElement,
          "Modules included",
          "navigate.to.included.module"
        ),
        DaggerRelatedElement(
          mySubcomponentDaggerElement,
          "Subcomponents",
          "navigate.to.subcomponent"
        ),
      )

    assertThat(includedComponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          topLevelComponentDaggerElement,
          "Parent components",
          "navigate.to.parent.component"
        ),
      )
  }

  @Test
  fun component_getRelatedDaggerElementsWithClasses() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import dagger.Component
          import dagger.Module
          import dagger.Subcomponent

          @Component(
            modules = [MyModule::class],
            dependencies = [IncludedComponent::class],
          )
          class TopLevelComponent

          @Module(
            subcomponents = [MySubcomponent::class],
          )
          class MyModule

          @Subcomponent
          class MySubcomponent

          @Component
          class IncludedComponent
          """
            .trimIndent()
        )
        .virtualFile
    )

    val topLevelComponentDaggerElement =
      ComponentDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("class TopLevel|Component")
      )

    val myModuleDaggerElement =
      ModuleDaggerElement(myFixture.findParentElement<KtClassOrObject>("class My|Module"))

    val mySubcomponentDaggerElement =
      SubcomponentDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("class My|Subcomponent")
      )

    val includedComponentDaggerElement =
      ComponentDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("class Included|Component")
      )

    assertThat(topLevelComponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          myModuleDaggerElement,
          "Modules included",
          "navigate.to.included.module"
        ),
        DaggerRelatedElement(
          mySubcomponentDaggerElement,
          "Subcomponents",
          "navigate.to.subcomponent"
        ),
      )

    assertThat(includedComponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          topLevelComponentDaggerElement,
          "Parent components",
          "navigate.to.parent.component"
        ),
      )
  }

  @Test
  fun component_getRelatedDaggerElementsWithObjects() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import dagger.Component
          import dagger.Module
          import dagger.Subcomponent

          @Component(
            modules = [MyModule::class],
            dependencies = [IncludedComponent::class],
          )
          object TopLevelComponent

          @Module(
            subcomponents = [MySubcomponent::class],
          )
          object MyModule

          @Subcomponent
          object MySubcomponent

          @Component
          object IncludedComponent
          """
            .trimIndent()
        )
        .virtualFile
    )

    val topLevelComponentDaggerElement =
      ComponentDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("object TopLevel|Component")
      )

    val myModuleDaggerElement =
      ModuleDaggerElement(myFixture.findParentElement<KtClassOrObject>("object My|Module"))

    val mySubcomponentDaggerElement =
      SubcomponentDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("object My|Subcomponent")
      )

    val includedComponentDaggerElement =
      ComponentDaggerElement(
        myFixture.findParentElement<KtClassOrObject>("object Included|Component")
      )

    assertThat(topLevelComponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          myModuleDaggerElement,
          "Modules included",
          "navigate.to.included.module"
        ),
        DaggerRelatedElement(
          mySubcomponentDaggerElement,
          "Subcomponents",
          "navigate.to.subcomponent"
        ),
      )

    assertThat(includedComponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          topLevelComponentDaggerElement,
          "Parent components",
          "navigate.to.parent.component"
        ),
      )
  }

  @Test
  fun module_getRelatedDaggerElements() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import dagger.Component
          import dagger.Module
          import dagger.Subcomponent

          @Component(
            modules = [MyModule::class],
          )
          interface MyComponent

          @Subcomponent(
            modules = [MyModule::class],
          )
          interface MySubcomponent

          @Module(
            includes = [MyModule::class],
          )
          interface MyContainingModule

          @Module
          interface MyModule
          """
            .trimIndent()
        )
        .virtualFile
    )

    val myComponentDaggerElement =
      ComponentDaggerElement(myFixture.findParentElement<KtClass>("interface My|Component"))

    val mySubcomponentDaggerElement =
      SubcomponentDaggerElement(myFixture.findParentElement<KtClass>("interface My|Subcomponent"))

    val myContainingModuleDaggerElement =
      ModuleDaggerElement(myFixture.findParentElement<KtClass>("interface My|ContainingModule"))

    val myModuleDaggerElement =
      ModuleDaggerElement(myFixture.findParentElement<KtClass>("interface My|Module"))

    assertThat(myModuleDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          myComponentDaggerElement,
          "Included in components",
          "navigate.to.component.that.include"
        ),
        DaggerRelatedElement(
          mySubcomponentDaggerElement,
          "Included in subcomponents",
          "navigate.to.subcomponent.that.include"
        ),
        DaggerRelatedElement(
          myContainingModuleDaggerElement,
          "Included in modules",
          "navigate.to.module.that.include"
        ),
      )
  }

  @Test
  fun subcomponent_getRelatedDaggerElements() {
    addDaggerAndHiltClasses(myFixture)

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          import dagger.Component
          import dagger.Module
          import dagger.Subcomponent

          @Component(
            modules = [MyModule::class],
          )
          interface MyComponent

          @Module(
            subcomponents = [MySubcomponent::class],
          )
          interface MyModule

          @Subcomponent(
            modules = [MyIncludedModule::class],
          )
          interface MySubcomponent

          @Module(
            subcomponents = [MyIncludedSubcomponent::class],
          )
          interface MyIncludedModule

          @Subcomponent
          interface MyIncludedSubcomponent
          """
            .trimIndent()
        )
        .virtualFile
    )

    val myComponentDaggerElement =
      ComponentDaggerElement(myFixture.findParentElement<KtClass>("interface My|Component"))

    val mySubcomponentDaggerElement =
      SubcomponentDaggerElement(myFixture.findParentElement<KtClass>("interface My|Subcomponent"))

    val myIncludedModuleDaggerElement =
      ModuleDaggerElement(myFixture.findParentElement<KtClass>("interface My|IncludedModule"))

    val myIncludedSubcomponentDaggerElement =
      SubcomponentDaggerElement(
        myFixture.findParentElement<KtClass>("interface My|IncludedSubcomponent")
      )

    assertThat(mySubcomponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          myComponentDaggerElement,
          "Parent components",
          "navigate.to.parent.component"
        ),
        DaggerRelatedElement(
          myIncludedModuleDaggerElement,
          "Modules included",
          "navigate.to.included.module"
        ),
        DaggerRelatedElement(
          myIncludedSubcomponentDaggerElement,
          "Subcomponents",
          "navigate.to.subcomponent"
        ),
      )

    assertThat(myIncludedSubcomponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(
          mySubcomponentDaggerElement,
          "Parent components",
          "navigate.to.parent.component"
        ),
      )
  }

  companion object {
    private val COFFEE_SHOP_ID = ClassId.fromString("com/example/CoffeeShop")
    private val COFFEE_SHOP_MODULE_ID = ClassId.fromString("com/example/CoffeeShopModule")
    private val COFFEE_SHOP_COMPONENT_ID = ClassId.fromString("com/example/CoffeeShopComponent")
    private val COFFEE_SHOP_SUBCOMPONENT_ID =
      ClassId.fromString("com/example/CoffeeShopSubcomponent")
  }
}
