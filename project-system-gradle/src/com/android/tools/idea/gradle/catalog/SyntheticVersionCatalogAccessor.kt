// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.catalog

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightClass
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PropertyUtilBase
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames

/**
 * Serves as a client for PSI infrastructure and as a layer over TOML version catalog files at the same time
 *
 * This is mostly a copy of JetBrains SyntheticVersionCatalogAccessor that provides access to Studio Version Catalog model.
 * Class must be deleted once intellij.gradle.analysis is enabled in Studio or platform navigation relies on
 * gradle.dsl.api module
 *
 * Multiple catalog support was introduced comparatively to platform
*/
class SyntheticVersionCatalogAccessor(project: Project, scope: GlobalSearchScope, model: GradleVersionCatalogsModel, className: String) :
  LightClass(JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, scope)!!) {
  init{
    assert(className in model.catalogNames())
  }
  private val libraries: Array<PsiMethod> =
    SyntheticAccessorBuilder(project, scope, className, Kind.LIBRARY)
      .buildLibraryMethods(this,
                    model.libraries(className)!!.properties,
                    "")
      .toTypedArray()

  private val plugins: PsiMethod = SyntheticAccessorBuilder(project, scope, className, Kind.PLUGIN)
    .buildEnclosingMethod(this,
                          model.plugins(className)!!.properties,
                          "plugins")

  private val versions: PsiMethod = SyntheticAccessorBuilder(project, scope, className, Kind.VERSION)
    .buildEnclosingMethod(this,
                          model.versions(className)!!.properties,
                          "versions")

  private val bundles: PsiMethod = SyntheticAccessorBuilder(project, scope, className, Kind.BUNDLE)
    .buildEnclosingMethod(this,
                          model.bundles(className)!!.properties,
                          "bundles")


  private val className = "LibrariesFor${StringUtil.capitalize(className)}"

  override fun getMethods(): Array<PsiMethod> {
    return libraries + arrayOf(plugins, versions, bundles)
  }

  override fun getQualifiedName(): String {
    return "org.gradle.accessors.dm.$className"
  }

  override fun getName(): String = className

  companion object {
    private enum class Kind(val prefix: String) {
      LIBRARY("Library"), PLUGIN("Plugin"), BUNDLE("Bundle"), VERSION("Version")
    }

    const val asProviderMethodName = "asProvider"

    private class SyntheticAccessorBuilder(val project: Project, val scope: GlobalSearchScope, val className: String, val kind: Kind) {

      private fun buildSyntheticInnerClass(mapping: List<GraphNode>,
                                           containingClass: PsiClass,
                                           name: String): LightClass {
        val clazz = object : LightClass(JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, scope)!!) {
          private val methods = buildMethods(this, mapping, name).toTypedArray()

          override fun getMethods(): Array<out PsiMethod> {
            return methods
          }

          override fun getContainingClass(): PsiClass {
            return containingClass
          }

          override fun getName(): String {
            return name + kind.prefix + "Accessors"
          }

          override fun getQualifiedName(): String {
            return "org.gradle.accessors.dm.LibrariesFor${StringUtil.capitalize(className)}.${name}${kind.prefix}Accessors"
          }
        }

        return clazz
      }

      fun buildLibraryMethods(constructedClass: PsiClass, model: List<GradlePropertyModel>, prefix: String): List<LightMethodBuilder> {
        val graph = distributeNames(model)
        return buildMethods(constructedClass, graph, prefix)
      }

      fun buildMethods(constructedClass: PsiClass, model: List<GraphNode>, prefix: String): List<LightMethodBuilder> {
        val container = mutableListOf<LightMethodBuilder>()
        for (propertyModel in model) {
          val name = propertyModel.name
          val getterName =
            if (name == asProviderMethodName) name else PropertyUtilBase.getAccessorName(name, PropertyKind.GETTER)
          val method = LightMethodBuilder(PsiManager.getInstance(project), JavaLanguage.INSTANCE, getterName)
          method.containingClass = constructedClass
          val innerModel = propertyModel.getChildren()
          when (propertyModel) {
            is LeafNode -> {
              val fqn = when (kind) {
                Kind.LIBRARY -> GradleCommonClassNames.GRADLE_API_ARTIFACTS_MINIMAL_EXTERNAL_MODULE_DEPENDENCY
                Kind.PLUGIN -> GradleCommonClassNames.GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCY
                Kind.BUNDLE -> GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY_BUNDLE
                Kind.VERSION -> CommonClassNames.JAVA_LANG_STRING
              }
              val provider = JavaPsiFacade.getInstance(project).findClass(GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER, scope)
                             ?: continue
              val minimalDependency = PsiClassType.getTypeByName(fqn, project, scope)
              method.setMethodReturnType(PsiElementFactory.getInstance(project).createType(provider, minimalDependency))
            }

            is PrefixBasedGraphNode, is PrefixBasedGraphNodeWithProvider -> {
              val syntheticClass = buildSyntheticInnerClass(innerModel, constructedClass,
                                                            prefix + StringUtil.capitalize(name))
              method.setMethodReturnType(PsiElementFactory.getInstance(project).createType(syntheticClass, PsiSubstitutor.EMPTY))
            }
          }

          container.add(method)
        }
        return container
      }

      fun buildEnclosingMethod(constructedClass: PsiClass, model: List<GradlePropertyModel>, enclosingMethodName: String): PsiMethod {
        val accessorName = PropertyUtilBase.getAccessorName(enclosingMethodName, PropertyKind.GETTER)
        val method = LightMethodBuilder(PsiManager.getInstance(project), JavaLanguage.INSTANCE, accessorName)
        method.containingClass = constructedClass
        val graph = distributeNames(model)
        val syntheticClass = buildSyntheticInnerClass(graph, constructedClass, "")
        method.setMethodReturnType(PsiElementFactory.getInstance(project).createType(syntheticClass, PsiSubstitutor.EMPTY))
        return method
      }
    }

    private interface GraphNode {
      val name: String
      fun getChildren(): List<GraphNode>
    }

    private class LeafNode(override val name: String) : GraphNode {
      override fun getChildren(): List<GraphNode> = listOf()
    }

    private class PrefixBasedGraphNode(override val name: String, val nested: List<GraphNode>?) : GraphNode {
      override fun getChildren(): List<GraphNode> = nested ?: listOf()
    }

    /**
     * This class covers case when node has value and children at the same time. I.e:
     * a = "dependency"
     * a_ext = "another dependency"
     */
    private class PrefixBasedGraphNodeWithProvider(override val name: String, val nested: List<GraphNode>) : GraphNode {
      override fun getChildren(): List<GraphNode> = nested + LeafNode(asProviderMethodName)
    }

    /**
     * Building tree of nodes
     */
    private fun distributeNames(model:List<GradlePropertyModel>): List<GraphNode> {
      val dict = model.associateBy { it.name.replace("[_-]".toRegex(), ".") }

      fun createTree(continuation: List<List<String>>, path:List<String>): List<GraphNode>? {
        if (continuation.isEmpty()) {
          return null
        }
        val grouped: Map<String, List<List<String>>> = continuation.groupBy({ it[0] }) { it.drop(1).takeIf(List<String>::isNotEmpty) }.mapValues { it.value.filterNotNull() }
        return grouped.map { (name, cont) ->
          val child = createTree(cont, path + name)
          val fullName = (path + name).joinToString(".")
          val value = dict[fullName]

          if(child != null && value != null) {
            PrefixBasedGraphNodeWithProvider(name, child)
          } else if(value != null) {
            LeafNode(name)
          } else {
            PrefixBasedGraphNode(name, child)
          }
        }
      }

      return createTree(dict.map{it.key.split(".")}, listOf()) ?: listOf()
    }
  }

}