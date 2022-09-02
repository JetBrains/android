package com.android.tools.idea.compose.preview

import com.android.annotations.concurrency.Slow
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import com.android.tools.idea.compose.preview.util.ComposePreviewElement
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.DisplayPositioning
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.util.ListenerCollection
import com.google.common.collect.Sets
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElementOfType

internal const val PIN_EMOJI = "\uD83D\uDCCC"

private data class PinnedElementReference(
  val containingFilePath: String,
  val pinnedComposeFqn: String
)

/**
 * Returns the full path of the file containing the given [PsiElement]. The path can be null if the
 * file is not valid anymore.
 */
private fun PsiElement.containingPath(): String? =
  ReadAction.compute<String?, Throwable> {
    if (containingFile.isValid) containingFile.virtualFile.path else null
  }

/**
 * Returns the [PinnedElementReference] for the [ComposePreviewElement]. This is just a key that can
 * be serialized pointing to the [ComposePreviewElement].
 */
private fun ComposePreviewElement.asPinnedElement(): PinnedElementReference? {
  val containingFilePath = containingFile?.virtualFile?.path ?: return null
  return PinnedElementReference(containingFilePath, composableMethodFqn)
}

/** A [ComposePreviewElementInstance] for pinned elements. */
private class PinnedComposePreviewElementInstance(
  private val basePreviewElement: ComposePreviewElementInstance
) : ComposePreviewElementInstance(), ComposePreviewElement by basePreviewElement {
  override val instanceId: String = "PINNED#${basePreviewElement.instanceId}"
  override val displaySettings: PreviewDisplaySettings =
    PreviewDisplaySettings(
      basePreviewElement.displaySettings.name,
      message("pinned.group.name"),
      basePreviewElement.displaySettings.showDecoration,
      basePreviewElement.displaySettings.showBackground,
      basePreviewElement.displaySettings.backgroundColor,
      DisplayPositioning.TOP
    )
}

@Service
class PinnedPreviewElementManagerImpl internal constructor(val project: Project) :
  PinnedPreviewElementManager {
  private val pinnedElements = Sets.newConcurrentHashSet<PinnedElementReference>()
  private val pinsModificationTracker = SimpleModificationTracker()
  private val listenerCollection =
    ListenerCollection.createWithDirectExecutor<PinnedPreviewElementManager.Listener>()

  internal val previewElements: Sequence<ComposePreviewElementInstance>
    get() {
      val filesWithPinnedElements = pinnedElements.map { it.containingFilePath }.toSet()
      val kotlinAnnotations: Sequence<PsiElement> =
        CachedValuesManager.getManager(project)
          .getCachedValue(project) {
            CachedValueProvider.Result.createSingleDependency(
              DumbService.getInstance(project)
                .runReadActionInSmartMode(
                  Computable<Collection<PsiElement>> {
                    KotlinAnnotationsIndex.getInstance()
                      .get(
                        COMPOSE_PREVIEW_ANNOTATION_NAME,
                        project,
                        GlobalSearchScope.projectScope(project)
                      )
                  }
                ),
              PsiModificationTracker.SERVICE
                .getInstance(project)
                .forLanguage(KotlinLanguage.INSTANCE)
            )
          }
          .asSequence()

      val foundPreviewElementPaths: Set<String> by lazy {
        kotlinAnnotations.mapNotNull { it.containingPath() }.toSet()
      }

      val foundPreviewElements =
        kotlinAnnotations
          .filterIsInstance<KtAnnotationEntry>()
          // Only look at the files that have a pinned preview
          .filter {
            filesWithPinnedElements.contains(it.containingPath()) && it.isPreviewAnnotation()
          }
          .mapNotNull {
            ReadAction.compute<UAnnotation?, Throwable> { it.psiOrParent.toUElementOfType() }
              ?.toPreviewElement()
          }
          .filterIsInstance<ComposePreviewElementInstance>()
          .map { PinnedComposePreviewElementInstance(it) }
          .filter { pinnedElements.contains(it.asPinnedElement()) }
          .distinct()

      // Clean-up any elements from the file path cache that do not exist anymore
      pinnedElements.removeIf { !foundPreviewElementPaths.contains(it.containingFilePath) }

      return foundPreviewElements
    }

  override fun pin(elements: Collection<ComposePreviewElementInstance>): Boolean =
    elements.filter { pinnedElements.add(it.asPinnedElement() ?: return@filter false) }.ifNotEmpty {
      pinsModificationTracker.incModificationCount()
      listenerCollection.forEach { it.pinsChanged() }
      true
    }
      ?: false

  override fun unpin(elements: Collection<ComposePreviewElementInstance>): Boolean =
    elements
      .filter { pinnedElements.remove(it.asPinnedElement() ?: return@filter false) }
      .ifNotEmpty {
        pinsModificationTracker.incModificationCount()
        listenerCollection.forEach { it.pinsChanged() }
        true
      }
      ?: false

  override fun unpinAll(): Boolean =
    pinnedElements.removeAll(pinnedElements).also { removed ->
      if (removed) {
        pinsModificationTracker.incModificationCount()
        listenerCollection.forEach { it.pinsChanged() }
      }
    }

  override fun isPinned(element: ComposePreviewElement) =
    pinnedElements.contains(element.asPinnedElement())

  override fun isPinned(file: PsiFile) =
    pinnedElements.any { it.containingFilePath == file.virtualFile.path }

  override fun addListener(listener: PinnedPreviewElementManager.Listener) {
    listenerCollection.add(listener)
  }

  override fun removeListener(listener: PinnedPreviewElementManager.Listener) {
    listenerCollection.remove(listener)
  }

  override fun getModificationCount(): Long = pinsModificationTracker.modificationCount
}

private object NopPinnedPreviewElementManager :
  PinnedPreviewElementManager, ModificationTracker by ModificationTracker.NEVER_CHANGED {
  override fun pin(elements: Collection<ComposePreviewElementInstance>): Boolean = false
  override fun unpin(elements: Collection<ComposePreviewElementInstance>): Boolean = false
  override fun unpinAll(): Boolean = false
  override fun isPinned(element: ComposePreviewElement): Boolean = false
  override fun isPinned(file: PsiFile): Boolean = false
  override fun addListener(listener: PinnedPreviewElementManager.Listener) {}

  override fun removeListener(listener: PinnedPreviewElementManager.Listener) {}
}

interface PinnedPreviewElementManager : ModificationTracker {
  fun interface Listener {
    /** Called when the pinned previews have changed */
    fun pinsChanged()
  }

  /**
   * Pins the given [ComposePreviewElementInstance]s. Returns true if any element was not pinned and
   * was successfully pinned.
   */
  fun pin(elements: Collection<ComposePreviewElementInstance>): Boolean

  /**
   * Unpins the given [ComposePreviewElementInstance]s. Returns true if any element was pinned and
   * was successfully unpinned.
   */
  fun unpin(elements: Collection<ComposePreviewElementInstance>): Boolean

  /**
   * Pins the given [ComposePreviewElementInstance]. Returns true if the element was not pinned and
   * was successfully pinned.
   */
  fun pin(element: ComposePreviewElementInstance): Boolean = pin(listOf(element))

  /**
   * Unpins the given [ComposePreviewElementInstance]. Returns true if the element was pinned and
   * was successfully unpinned.
   */
  fun unpin(element: ComposePreviewElementInstance): Boolean = unpin(listOf(element))

  /**
   * Unpins all the current pinned [ComposePreviewElementInstance]. Returns true if there was at
   * least one pinned instance.
   */
  fun unpinAll(): Boolean

  /**
   * Returns true if the given [ComposePreviewElement] is pinned. Only
   * [ComposePreviewElementInstance]s can be pinned.
   */
  fun isPinned(element: ComposePreviewElement): Boolean

  /** Returns true if the given [PsiFile] has any elements pinned. */
  fun isPinned(file: PsiFile): Boolean

  fun addListener(listener: Listener)

  fun removeListener(listener: Listener)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PinnedPreviewElementManager =
      if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
        project.getService(PinnedPreviewElementManagerImpl::class.java)
      } else {
        NopPinnedPreviewElementManager
      }

    /**
     * Returns a [PreviewElementProvider] for pinned previews. Pinned previews are
     * [ComposePreviewElement] that can be anywhere in the project and that are not meant to be only
     * from the current opened editor.
     */
    @JvmStatic
    fun getPreviewElementProvider(
      project: Project
    ): PreviewElementProvider<ComposePreviewElementInstance> =
      if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
        object : PreviewElementProvider<ComposePreviewElementInstance> {
          @Slow
          override suspend fun previewElements(): Sequence<ComposePreviewElementInstance> =
            project.getService(PinnedPreviewElementManagerImpl::class.java).previewElements
        }
      } else {
        EmptyPreviewElementInstanceProvider
      }
  }
}
