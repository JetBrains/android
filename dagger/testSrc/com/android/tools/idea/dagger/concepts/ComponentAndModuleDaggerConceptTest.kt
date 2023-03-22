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
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtClass
import org.junit.Before
import org.junit.Ignore
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

    val element: PsiClass = myFixture.findParentElement("Coffee|Shop")
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

    val componentClass: KtClass = myFixture.findParentElement("interface CoffeeShop|Component")

    val moduleIndexValue =
      ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, "com.example.CoffeeShopComponent")
    val dependencyIndexValue =
      ClassIndexValue(
        IndexValue.DataType.COMPONENT_WITH_DEPENDENCY,
        "com.example.CoffeeShopComponent"
      )

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
      ClassIndexValue(
        IndexValue.DataType.SUBCOMPONENT_WITH_MODULE,
        "com.example.CoffeeShopSubcomponent"
      )

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
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, "com.example.CoffeeShopModule")
    val subcomponentIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, "com.example.CoffeeShopModule")

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
      ClassIndexValue(IndexValue.DataType.COMPONENT_WITH_MODULE, "com.example.CoffeeShopComponent")
    val dependencyIndexValue =
      ClassIndexValue(
        IndexValue.DataType.COMPONENT_WITH_DEPENDENCY,
        "com.example.CoffeeShopComponent"
      )

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
      ClassIndexValue(
        IndexValue.DataType.SUBCOMPONENT_WITH_MODULE,
        "com.example.CoffeeShopSubcomponent"
      )

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
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_INCLUDE, "com.example.CoffeeShopModule")
    val subcomponentIndexValue =
      ClassIndexValue(IndexValue.DataType.MODULE_WITH_SUBCOMPONENT, "com.example.CoffeeShopModule")

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
    assertThat(componentDaggerElement).isInstanceOf(ComponentDaggerElement::class.java)

    val subcomponentDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Subcomponent").parentOfType<KtClass>()!!
      )
    assertThat(subcomponentDaggerElement).isInstanceOf(SubcomponentDaggerElement::class.java)

    val moduleDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Module").parentOfType<KtClass>()!!
      )
    assertThat(moduleDaggerElement).isInstanceOf(ModuleDaggerElement::class.java)
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
    assertThat(componentDaggerElement).isInstanceOf(ComponentDaggerElement::class.java)

    val subcomponentDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Subcomponent").parentOfType<PsiClass>()!!
      )
    assertThat(subcomponentDaggerElement).isInstanceOf(SubcomponentDaggerElement::class.java)

    val moduleDaggerElement =
      ComponentAndModuleDaggerConcept.daggerElementIdentifiers.getDaggerElement(
        myFixture.moveCaret("CoffeeShop|Module").parentOfType<PsiClass>()!!
      )
    assertThat(moduleDaggerElement).isInstanceOf(ModuleDaggerElement::class.java)
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
        DaggerRelatedElement(ModuleDaggerElement(coffeeShopModuleElement), "Modules included"),
        DaggerRelatedElement(
          SubcomponentDaggerElement(coffeeShopSubcomponentElement),
          "Subcomponents"
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
        DaggerRelatedElement(ModuleDaggerElement(coffeeShopModuleElement), "Modules included"),
        DaggerRelatedElement(
          SubcomponentDaggerElement(coffeeShopSubcomponentElement),
          "Subcomponents"
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

    val componentPsiElement: PsiClass = myFixture.findParentElement("CoffeeShop|Component")

    val componentDaggerElement = ComponentDaggerElement(componentPsiElement)
    val modulesAndSubcomponents = componentDaggerElement.getIncludedModulesAndSubcomponents()

    assertThat(modulesAndSubcomponents).hasSize(2)

    assertThat(modulesAndSubcomponents[0].first).isInstanceOf(ModuleDaggerElement::class.java)
    assertThat(modulesAndSubcomponents[1].first).isInstanceOf(SubcomponentDaggerElement::class.java)

    assertThat(modulesAndSubcomponents[0].first.psiElement.text).contains("CoffeeShopModule")
    assertThat(modulesAndSubcomponents[1].first.psiElement.text).contains("CoffeeShopSubcomponent")

    assertThat(modulesAndSubcomponents[0].second).isEqualTo("Modules included")
    assertThat(modulesAndSubcomponents[1].second).isEqualTo("Subcomponents")
  }

  @Ignore // TODO(b/265846405): Start running test when index is enabled
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
      ComponentDaggerElement(
        myFixture.moveCaret("interface TopLevel|Component").parentOfType<KtClass>(withSelf = true)!!
      )

    val myModuleDaggerElement =
      ModuleDaggerElement(
        myFixture.moveCaret("interface My|Module").parentOfType<KtClass>(withSelf = true)!!
      )

    val mySubcomponentDaggerElement =
      SubcomponentDaggerElement(
        myFixture.moveCaret("interface My|Subcomponent").parentOfType<KtClass>(withSelf = true)!!
      )

    val includedComponentDaggerElement =
      ComponentDaggerElement(
        myFixture.moveCaret("interface Included|Component").parentOfType<KtClass>(withSelf = true)!!
      )

    assertThat(topLevelComponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(myModuleDaggerElement, "Modules included"),
        DaggerRelatedElement(mySubcomponentDaggerElement, "Subcomponents"),
      )

    assertThat(includedComponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(topLevelComponentDaggerElement, "Parent components"),
      )
  }

  @Ignore // TODO(b/265846405): Start running test when index is enabled
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
      ComponentDaggerElement(
        myFixture.moveCaret("interface My|Component").parentOfType<KtClass>(withSelf = true)!!
      )

    val mySubcomponentDaggerElement =
      SubcomponentDaggerElement(
        myFixture.moveCaret("interface My|Subcomponent").parentOfType<KtClass>(withSelf = true)!!
      )

    val myContainingModuleDaggerElement =
      ModuleDaggerElement(
        myFixture
          .moveCaret("interface My|ContainingModule")
          .parentOfType<KtClass>(withSelf = true)!!
      )

    val myModuleDaggerElement =
      ModuleDaggerElement(
        myFixture.moveCaret("interface My|Module").parentOfType<KtClass>(withSelf = true)!!
      )

    assertThat(myModuleDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(myComponentDaggerElement, "Included in components"),
        DaggerRelatedElement(mySubcomponentDaggerElement, "Included in subcomponents"),
        DaggerRelatedElement(myContainingModuleDaggerElement, "Included in modules"),
      )
  }

  @Ignore // TODO(b/265846405): Start running test when index is enabled
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
      ComponentDaggerElement(
        myFixture.moveCaret("interface My|Component").parentOfType<KtClass>(withSelf = true)!!
      )

    val mySubcomponentDaggerElement =
      SubcomponentDaggerElement(
        myFixture.moveCaret("interface My|Subcomponent").parentOfType<KtClass>(withSelf = true)!!
      )

    val myIncludedModuleDaggerElement =
      ModuleDaggerElement(
        myFixture.moveCaret("interface My|IncludedModule").parentOfType<KtClass>(withSelf = true)!!
      )

    val myIncludedSubcomponentDaggerElement =
      SubcomponentDaggerElement(
        myFixture
          .moveCaret("interface My|IncludedSubcomponent")
          .parentOfType<KtClass>(withSelf = true)!!
      )

    assertThat(mySubcomponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(myComponentDaggerElement, "Parent components"),
        DaggerRelatedElement(myIncludedModuleDaggerElement, "Modules included"),
        DaggerRelatedElement(myIncludedSubcomponentDaggerElement, "Subcomponents"),
      )

    assertThat(myIncludedSubcomponentDaggerElement.getRelatedDaggerElements())
      .containsExactly(
        DaggerRelatedElement(mySubcomponentDaggerElement, "Parent components"),
      )
  }
}
