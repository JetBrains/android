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
package com.android.tools.idea.projectsystem

import com.android.tools.idea.concurrency.coroutineScope
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.blockingContext
import io.github.classgraph.ClassGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * This internal action is only used for checking the consistency of the final system.  Since interfaces of [Token]
 * define extension points, implemented by concrete classes and looked up at runtime, it is easy to imagine a
 * flawed distribution that does not provide implementation classes.  We would expect some other end-to-end tests
 * to fail in those circumstances, as that would presumably mean that some feature didn't operate as expected, but
 * having the explicit consistency checks here should help diagnose the issue if that is what has happened.
 */
class VerifyGradleTokensAction : AnAction("Verify Gradle Tokens") {
  override fun actionPerformed(e: AnActionEvent) {
    // TODO Update action to use service, to prevent memory leak
    e.project!!.coroutineScope.launch(Dispatchers.IO) {
      blockingContext {
        val tokenClass = Token::class.java
        val gradleTokenClass = GradleToken::class.java
        val projectSystemTokenClass = ProjectSystemToken::class.java
        val interfaces = mutableSetOf<Class<*>>()
        val classes = mutableSetOf<Class<*>>()
        val problems = mutableSetOf<Class<*>>()
        val classLoaders = PluginManager.getLoadedPlugins().map { it.classLoader }.plus(tokenClass.classLoader).toSet()

        ClassGraph()
          .apply { classLoaders.forEach { addClassLoader(it) } }
          .enableClassInfo().scan()
          .allClasses
          .mapNotNull { try { it.loadClass() } catch (e: Throwable) { null } }
          .filter { tokenClass.isAssignableFrom(it) }
          .forEach {
            when {
              it == tokenClass -> Unit
              // Skip interfaces like `interface GradleToken: ProjectSystemToken`
              it.isInterface && projectSystemTokenClass.isAssignableFrom(it) -> Unit

              it.isInterface -> interfaces.add(it)
              else -> classes.add(it)
            }
          }

        interfaces.forEach { i ->
          classes.filter { c -> i.isAssignableFrom(c) && gradleTokenClass.isAssignableFrom(c) }.let { l ->
            when {
              l.isEmpty() -> problems.add(i).also { LOG.info("no GradleToken class implementing ${i.name}") }
              l.size > 1 -> problems.add(i).also { LOG.info("more than one GradleToken class ($l) implementing ${i.name}") }
              else -> LOG.debug("${l[0].name} implements ${i.name}")
            }
          }
        }

        classes.filter { gradleTokenClass.isAssignableFrom(it) }
          .forEach { c ->
            interfaces.filter { i -> i.isAssignableFrom(c) }.let { l ->
              when {
                l.isEmpty() -> problems.add(c).also { LOG.info("GradleToken subclass ${c.name} does not implement any interface") }
                l.size > 1 -> problems.add(c).also { LOG.info("GradleToken subclass ${c.name} implements more than one Token interface: $l") }
                else -> LOG.debug("${c.name} implements ${l[0].name}")
              }
            }
          }

        LOG.info("${problems.size}/${interfaces.size} problem${if (problems.size == 1) "" else "s"} found")
      }
    }
  }

  companion object {
    val LOG = Logger.getInstance("VerifyGradleTokens")
  }
}
