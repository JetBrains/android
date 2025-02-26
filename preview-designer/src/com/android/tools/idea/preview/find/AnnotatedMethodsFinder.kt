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
package com.android.tools.idea.preview.find

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.getPsiFileSafely
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parentOfType
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.isRejected
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType

/**
 * A mapping to keep track of the cache [Key]s for the annotation combinations. Each [Key] instance
 * is unique by implementation and [Key] declares [hashCode] and [equals] methods as final. Thus,
 * this mapping allows to reuse the same [Key] instance for the same [localKey].
 */
@Service
class CacheKeysManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): CacheKeysManager =
      project.getService(CacheKeysManager::class.java)
  }

  private val annotationCacheKeys = ConcurrentHashMap<Any, Key<out CachedValue<out Any>>>()

  fun <T : Any> getKey(localKey: Any): Key<CachedValue<T>> {
    return annotationCacheKeys.getOrPut(localKey) { Key<CachedValue<T>>(localKey.toString()) }
      as Key<CachedValue<T>>
  }

  @VisibleForTesting fun map() = annotationCacheKeys
}

fun <T> CachedValuesManager.getCachedValue(
  dataHolder: UserDataHolder,
  key: Key<CachedValue<T>>,
  provider: CachedValueProvider<T>,
): T = this.getCachedValue(dataHolder, key, provider, false)

/** Finds all the [UAnnotation]s in [vFile] in [project] with [shortAnnotationName] as name. */
@RequiresReadLock
@VisibleForTesting
internal fun findAnnotations(
  project: Project,
  vFile: VirtualFile,
  shortAnnotationName: String,
): Collection<UAnnotation> {
  if (DumbService.isDumb(project)) {
    Logger.getInstance(AnnotatedMethodsFinder::class.java)
      .debug(
        "findAnnotations for @$shortAnnotationName called while indexing. No annotations will be found"
      )
    return emptyList()
  }

  val psiFile = AndroidPsiUtils.getPsiFileSafely(project, vFile) ?: return emptyList()
  return CachedValuesManager.getManager(project).getCachedValue(
    psiFile,
    CacheKeysManager.getInstance(project).getKey(shortAnnotationName),
  ) {
    val scope = GlobalSearchScope.fileScope(project, vFile)
    val annotations =
      if (psiFile.language == KotlinLanguage.INSTANCE) {
        KotlinAnnotationsIndex[shortAnnotationName, project, scope].asSequence().map {
          it.psiOrParent
        }
      } else {
        JavaAnnotationIndex.getInstance()
          .getAnnotations(shortAnnotationName, project, scope)
          .asSequence()
      }

    CachedValueProvider.Result.create(
      annotations.toList().mapNotNull { it.toUElementOfType<UAnnotation>() }.distinct(),
      psiFile,
    )
  }
}

/**
 * A [ModificationTracker] that tracks a [Promise] and can be used as a dependency for
 * [CachedValuesManager.getCachedValue]. [CachedValuesManager.getCachedValue] can use objects
 * implementing some interfaces (see [com.intellij.util.CachedValueBase.getTimeStamp] for the
 * interfaces list) as dependencies to invalidate the cache when the interface methods indicate so.
 * Here we implement one of the interfaces, namely [ModificationTracker]. If the promise is rejected
 * or fails, the count is updated, the cache is invalidated, and the result is not cached.
 */
private class PromiseModificationTracker(private val promise: Promise<*>) : ModificationTracker {
  private var modificationCount = 0L

  override fun getModificationCount(): Long =
    when {
      promise.isRejected -> ++modificationCount // The promise failed so we ensure it is not cached
      else -> modificationCount
    }
}

@RequiresReadLock
fun UMethod?.isAnnotatedWith(annotationFqn: String) = runReadAction {
  this?.uAnnotations?.any { annotation -> annotationFqn == annotation.qualifiedName } ?: false
}

/**
 * Returns the [UMethod] annotated by this [UAnnotation], or null if it is not annotating a method,
 * or if the method is not also annotated with [annotationFqn].
 */
@RequiresReadLock
fun UAnnotation.getContainingUMethodAnnotatedWith(annotationFqn: String): UMethod? {
  // TODO(b/349535556): Remove this. This is temporarily added to ensure that no paths without the
  // read lock are left to this method.
  // The method is tagged RequiresReadLock so it should never be called without the read lock.
  fun getContainingUMethodWithReadLock(): UMethod? {
    val uMethod =
      getContainingUMethod() ?: javaPsi?.parentOfType<PsiMethod>()?.toUElement(UMethod::class.java)
    return if (uMethod.isAnnotatedWith(annotationFqn)) uMethod else null
  }

  if (!ApplicationManager.getApplication().isReadAccessAllowed) {
    Logger.getInstance(AnnotatedMethodsFinder::class.java)
      .error("getContainingUMethodAnnotatedWith called without read lock")
    return runReadAction { getContainingUMethodWithReadLock() }
  }

  return getContainingUMethodWithReadLock()
}

/**
 * Returns a [CachedValueProvider] that provides values of type [T] from the methods annotated with
 * [annotationFqn] and [shortAnnotationName], from [vFile] of [project]. Technically, this function
 * could just return a collection of methods, but [toValues] might be slow to calculate so caching
 * the values rather than methods is more useful. To benefit from caching make sure the same
 * parameters are passed to the function call as all the parameters constitute the key.
 */
private fun <T> findAnnotatedMethodsCachedValues(
  project: Project,
  vFile: VirtualFile,
  annotationFqn: String,
  shortAnnotationName: String,
  toValues: (methods: List<UMethod>) -> Flow<T>,
): CachedValueProvider<CompletableDeferred<Collection<T>>> = CachedValueProvider {
  // This Deferred should not be needed, the promise could be returned directly. However, it seems
  // there is a compiler issue that
  // causes the findAnnotatedMethodsValues to fail when using the "dist" build (not from source).
  // Using the deferred seems to avoid the problem. b/222843951.
  val deferred = CompletableDeferred<Collection<T>>()

  val promise =
    ReadAction.nonBlocking(
        Callable<Collection<T>> {
          val uMethods =
            findAnnotations(project, vFile, shortAnnotationName)
              .mapNotNull { it.getContainingUMethodAnnotatedWith(annotationFqn) }
              .distinct() // avoid looking more than once per method

          // TODO(b/381827960): avoid using runBlockingCancellable
          // At the moment we use runBlockingCancellable to calculate the list of values within this
          // smart read lock.
          // Callers of findAnnotatedMethodsValues must first ensure that any processing on their
          // flow provides smart read locks where necessary before removing the terminal .toList()
          // call.
          runBlockingCancellable { toValues(uMethods).toList() }
        }
      )
      .inSmartMode(project)
      .submit(AppExecutorUtil.getAppExecutorService())
      .onSuccess { deferred.complete(it) }
      .onError { deferred.completeExceptionally(it) }

  val kotlinJavaModificationTracker =
    PsiModificationTracker.getInstance(project).forLanguages { lang ->
      lang.`is`(KotlinLanguage.INSTANCE) || lang.`is`(JavaLanguage.INSTANCE)
    }
  val dumbModificationTracker = DumbService.getInstance(project).modificationTracker
  CachedValueProvider.Result.create(
    deferred,
    kotlinJavaModificationTracker,
    dumbModificationTracker,
    PromiseModificationTracker(promise),
  )
}

private data class CachedValuesKey<T>(
  val annotationFqn: String,
  val shortAnnotationName: String,
  val toValues: (methods: List<UMethod>) -> Flow<T>,
)

/**
 * Finds all the values calculated by [toValues] associated with the methods annotated with
 * [annotationFqn] and [shortAnnotationName] from [vFile] in [project].
 */
suspend fun <T> findAnnotatedMethodsValues(
  project: Project,
  vFile: VirtualFile,
  annotationFqn: String,
  shortAnnotationName: String,
  toValues: (methods: List<UMethod>) -> Flow<T>,
): Collection<T> {
  val psiFile = getPsiFileSafely(project, vFile) ?: return emptyList()
  return withContext(AndroidDispatchers.workerThread) {
    val promiseResult =
      CachedValuesManager.getManager(project)
        .getCachedValue(
          psiFile,
          CacheKeysManager.getInstance(project)
            .getKey(CachedValuesKey(annotationFqn, shortAnnotationName, toValues)),
          findAnnotatedMethodsCachedValues(
            project,
            vFile,
            annotationFqn,
            shortAnnotationName,
            toValues,
          ),
        )
    try {
      return@withContext promiseResult.await()
    } catch (_: Throwable) {
      return@withContext emptyList()
    }
  }
}

private object AnnotatedMethodsFinder
