/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_DEBUGGABLE
import com.android.SdkConstants.ATTR_ENABLED
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_TARGET_ACTIVITY
import com.android.SdkConstants.ATTR_TARGET_SDK_VERSION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_PERMISSION
import com.android.SdkConstants.TAG_PERMISSION_GROUP
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M
import com.android.SdkConstants.TAG_USES_SDK
import com.android.tools.apk.analyzer.BinaryXmlParser
import com.android.tools.idea.flags.StudioFlags
import com.android.utils.reflection.qualifiedName
import com.google.common.primitives.Shorts
import com.google.devrel.gmscore.tools.apk.arsc.Chunk
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.DataInputOutputUtilRt.readSeq
import com.intellij.openapi.util.io.DataInputOutputUtilRt.writeSeq
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension
import com.intellij.util.indexing.SingleEntryIndexer
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readNullable
import com.intellij.util.io.DataInputOutputUtil.writeNullable
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.android.facet.AndroidFacet
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.DataInput
import java.io.DataOutput
import java.io.InputStreamReader
import java.io.Reader
import java.util.Objects
import java.util.stream.Stream

/**
 * A file-based index which maps each AndroidManifest.xml to a structured representation
 * of the manifest's raw text (as an [AndroidManifestRawText]).
 *
 * Callers that need to consume only a subset of a merged manifest's attributes can use
 * this index to avoid blocking on computing the entire manifest by applying this pattern:
 *
 *   1. Use [AndroidManifestIndex.getDataForMergedManifestContributors] to obtain the pre-parsed
 *      [AndroidManifestRawText] for each of the module's merged manifest contributors.
 *   2. Extract the desired attribute(s) from each pre-parsed [AndroidManifestRawText]
 *   3. Apply any relevant overrides and placeholder substitutions to the attributes' raw text (as determined by
 *      [com.android.tools.idea.projectsystem.AndroidModuleSystem.getManifestOverrides])
 *   4. Manually merge the attributes to obtain the final attribute(s) which would be present in the merged manifest
 */
class AndroidManifestIndex : SingleEntryFileBasedIndexExtension<AndroidManifestRawText>() {
  companion object {
    private val LOG = Logger.getInstance(AndroidManifestIndex::class.java)

    @JvmField
    val NAME: ID<Int, AndroidManifestRawText> = ID.create(::NAME.qualifiedName)

    @JvmStatic
    fun indexEnabled() = StudioFlags.ANDROID_MANIFEST_INDEX_ENABLED.get()

    /**
     * Returns the [AndroidManifestRawText] for the given [manifestFile], or null
     * if the file isn't recognized by the index (e.g. because it's malformed).
     * NOTE: This function must be called from a smart read action.
     * @see MergedManifestContributors
     * @see DumbService.runReadActionInSmartMode
     */
    @JvmStatic
    fun getDataForManifestFile(project: Project, manifestFile: VirtualFile): AndroidManifestRawText? {
      return if (checkIndexAccessibleFor(project)) {
        doGetDataForManifestFile(project, manifestFile)
      }
      else {
        null
      }
    }

    /**
     * Returns the [AndroidManifestRawText] for each of the given [facet]'s [MergedManifestContributors]
     * recognized by the index.
     * NOTE: This function must be called from a smart read action.
     * @see MergedManifestContributors
     * @see DumbService.runReadActionInSmartMode
     */
    @JvmStatic
    fun getDataForMergedManifestContributors(facet: AndroidFacet): Stream<AndroidManifestRawText> {
      val project = facet.module.project
      return if (checkIndexAccessibleFor(project)) {
        MergedManifestContributors.determineFor(facet)
          .allFiles
          .stream()
          .map { doGetDataForManifestFile(project, it) }
          .filter(Objects::nonNull)
          .map { it as AndroidManifestRawText }
      }
      else {
        Stream.empty()
      }
    }

    @JvmStatic
    private fun checkIndexAccessibleFor(project: Project): Boolean {
      return when {
        !indexEnabled() -> {
          LOG.error("Manifest index was queried even though it's disabled.")
          false
        }
        !ApplicationManager.getApplication().isReadAccessAllowed or DumbService.isDumb(project) -> {
          LOG.error("Manifest index queried outside of a smart read action.")
          false
        }
        else -> true
      }
    }

    @JvmStatic
    private fun doGetDataForManifestFile(project: Project, manifestFile: VirtualFile): AndroidManifestRawText? {
      return FileBasedIndex.getInstance()
        .getValues(NAME, getFileKey(manifestFile), GlobalSearchScope.fileScope(project, manifestFile))
        .firstOrNull()
    }
  }

  override fun getValueExternalizer() = AndroidManifestRawText.Externalizer

  override fun getName() = NAME

  override fun getVersion() = 1

  override fun getIndexer() = Indexer

  override fun getInputFilter() = InputFilter

  object InputFilter : DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
    override fun acceptInput(file: VirtualFile) = indexEnabled() && file.name == FN_ANDROID_MANIFEST_XML
  }

  object Indexer : SingleEntryIndexer<AndroidManifestRawText>(false) {
    public override fun computeValue(inputData: FileContent): AndroidManifestRawText? {
      // TODO: rather than throw errors when the manifest is malformed,
      //  we should do our best to extract as much as we can from the document.
      val parser = KXmlParser()
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
      parser.setInput(inputData.toManifestReader())
      try {
        while (parser.eventType != END_DOCUMENT) {
          when {
            parser.eventType != START_TAG -> parser.next()
            parser.name == TAG_MANIFEST -> return parser.parseManifestTag()
            else -> parser.skipSubTree()
          }
        }
      }
      catch (e: XmlPullParserException) {
        LOG.warn(e)
      }
      // This is unfortunate. Ideally, we'd just be catching XmlPullParserExceptions from KXmlParser
      // and the IllegalStateExceptions from our utility methods, but KXmlParser throws simple
      // RuntimeExceptions in some cases when the input file is malformed (e.g. if an attribute uses
      // an undefined namespace).
      catch (e: RuntimeException) {
        if (e is ProcessCanceledException) {
          throw e
        }
        LOG.warn(e)
      }
      return null
    }

    private fun FileContent.toManifestReader(): Reader {
      val inBinaryManifestFormat = Shorts.fromBytes(content[1], content[0]) == Chunk.Type.XML.code()
      return if (inBinaryManifestFormat) {
        val decoded = BinaryXmlParser.decodeXml(fileName, content)
        InputStreamReader(ByteArrayInputStream(decoded))
      }
      else {
        CharArrayUtil.readerFromCharSequence(contentAsText)
      }
    }

    private fun KXmlParser.parseManifestTag(): AndroidManifestRawText {
      require(START_TAG, null, TAG_MANIFEST)

      val activities = hashSetOf<ActivityRawText>()
      val activityAliases = hashSetOf<ActivityAliasRawText>()
      val customPermissionGroupNames = hashSetOf<String>()
      val customPermissionNames = hashSetOf<String>()
      val debuggable = getAttributeValue(ANDROID_URI, ATTR_DEBUGGABLE)
      val enabled = getAttributeValue(ANDROID_URI, ATTR_ENABLED)
      var minSdkLevel: String? = null
      val packageName = getAttributeValue(null, ATTR_PACKAGE)
      val usedPermissionNames = hashSetOf<String>()
      var targetSdkLevel: String? = null
      var theme: String? = null

      processChildTags {
        when(name) {
          TAG_APPLICATION -> {
            theme = getAttributeValue(ANDROID_URI, ATTR_THEME)
            processChildTags {
              when(name) {
                TAG_ACTIVITY -> activities.add(parseActivityTag())
                TAG_ACTIVITY_ALIAS -> activityAliases.add(parseActivityAliasTag())
                else -> skipSubTree()
              }
            }
          }
          TAG_PERMISSION -> {
            androidName?.let(customPermissionNames::add)
            skipSubTree()
          }
          TAG_PERMISSION_GROUP -> {
            androidName?.let(customPermissionGroupNames::add)
            skipSubTree()
          }
          TAG_USES_PERMISSION, TAG_USES_PERMISSION_SDK_23, TAG_USES_PERMISSION_SDK_M -> {
            androidName?.let(usedPermissionNames::add)
            skipSubTree()
          }
          TAG_USES_SDK -> {
            minSdkLevel = getAttributeValue(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            targetSdkLevel = getAttributeValue(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
            skipSubTree()
          }
          else -> skipSubTree()
        }
      }

      return AndroidManifestRawText(
        activities = activities.toSet(),
        activityAliases = activityAliases.toSet(),
        customPermissionGroupNames = customPermissionGroupNames.toSet(),
        customPermissionNames = customPermissionNames.toSet(),
        debuggable = debuggable,
        enabled = enabled,
        minSdkLevel = minSdkLevel,
        packageName = packageName,
        usedPermissionNames = usedPermissionNames.toSet(),
        targetSdkLevel = targetSdkLevel,
        theme = theme
      )
    }

    private fun KXmlParser.parseActivityTag() : ActivityRawText {
      require(START_TAG, null, TAG_ACTIVITY)

      val activityName: String? = androidName
      val enabled: String? = getAttributeValue(ANDROID_URI, ATTR_ENABLED)
      val intentFilters = hashSetOf<IntentFilterRawText>()

      processChildTags {
        if (name == TAG_INTENT_FILTER) {
          intentFilters.add(parseIntentFilterTag())
        }
        else {
          skipSubTree()
        }
      }

      return ActivityRawText(
        name = activityName,
        enabled = enabled,
        intentFilters = intentFilters.toSet()
      )
    }

    private fun KXmlParser.parseActivityAliasTag() : ActivityAliasRawText {
      require(START_TAG, null, TAG_ACTIVITY_ALIAS)

      val aliasName  = androidName
      val targetActivity = getAttributeValue(ANDROID_URI, ATTR_TARGET_ACTIVITY)
      val enabled  = getAttributeValue(ANDROID_URI, ATTR_ENABLED)
      val intentFilters = hashSetOf<IntentFilterRawText>()

      processChildTags {
        if (name == TAG_INTENT_FILTER) {
          intentFilters.add(parseIntentFilterTag())
        }
        else {
          skipSubTree()
        }
      }

      return ActivityAliasRawText(
        name = aliasName,
        targetActivity = targetActivity,
        enabled = enabled,
        intentFilters = intentFilters.toSet()
      )
    }

    private fun KXmlParser.parseIntentFilterTag() : IntentFilterRawText {
      require(START_TAG, null, TAG_INTENT_FILTER)

      val actionNames = hashSetOf<String>()
      val categoryNames = hashSetOf<String>()

      processChildTags {
        when(name) {
          TAG_ACTION -> androidName?.let(actionNames::add)
          TAG_CATEGORY -> androidName?.let(categoryNames::add)
        }
        skipSubTree()
      }

      return IntentFilterRawText(
        actionNames = actionNames.toSet(),
        categoryNames = categoryNames.toSet()
      )
    }
  }
}

/**
 * Invokes [processChildTag] for each start tag in the subtree of the current tag.
 * After this function is called, the parser will have moved to the current tag's end tag.
 *
 * [processChildTag] must also consume each child tag for which it is called,
 * moving the parser to the child's end tag. In order to make this actually
 * process just the children of the current tag, [processChildTag] should consume
 * the subtree of each child (e.g. by calling [KXmlParser.skipSubTree] or recursively calling
 * [processChildTags]).
 */
private inline fun KXmlParser.processChildTags(crossinline processChildTag: KXmlParser.() -> Unit) {
  require(START_TAG, null, null)
  val parentName = name
  val parentDepth = depth
  while (next() != END_DOCUMENT) {
    when (eventType) {
      START_TAG -> {
        check(parentDepth + 1 == depth) {
          "Child start tag depth mismatch: expected ${parentDepth + 1}, got $depth for tag \"$name\" (child of \"$parentName\")."
        }
        processChildTag()
      }
      END_TAG -> {
        check(parentDepth == depth) {
          "Parent end tag depth mismatch: expected $parentDepth, got $depth for tag \"$name\"."
        }
        return
      }
    }
  }
}

private val KXmlParser.androidName get() = getAttributeValue(ANDROID_URI, ATTR_NAME)

/**
 * Structured pieces of raw text from an AndroidManifest.xml file corresponding to a subset of a manifest tag's
 * attributes and sub-tags.
 *
 * The raw text may include placeholders and resource references. This is by design, since indices are project-independent
 * (whereas placeholder values are project/variant-specific) and we aren't capable of resolving resource references at
 * this abstraction level. For performance reasons, [AndroidManifestRawText] only contains the subset of text that the IDE needs.
 * When updating the struct to include any additional information, one must also update the schema used by
 * [AndroidManifestRawText.Externalizer] and increment [AndroidManifestIndex.getVersion].
 */
data class AndroidManifestRawText(
  val activities: Set<ActivityRawText>,
  val activityAliases: Set<ActivityAliasRawText>,
  val customPermissionGroupNames: Set<String>,
  val customPermissionNames: Set<String>,
  val debuggable: String?,
  val enabled: String?,
  val minSdkLevel: String?,
  val packageName: String?,
  val usedPermissionNames: Set<String>,
  val targetSdkLevel: String?,
  val theme: String?
) {
  /**
   * Singleton responsible for serializing/de-serializing [AndroidManifestRawText]s to/from disk.
   *
   * [AndroidManifestIndex] uses this externalizer to keep its cache within its memory limit
   * and also to persist indexed data between IDE sessions. Any structural change to [AndroidManifestRawText]
   * requires an update to the schema used here, and any update to the schema requires us to increment
   * [AndroidManifestIndex.getVersion].
   */
  object Externalizer : DataExternalizer<AndroidManifestRawText> {
    override fun save(out: DataOutput, value: AndroidManifestRawText) {
      value.apply {
        writeSeq(out, activities) { ActivityRawText.Externalizer.save(out, it) }
        writeSeq(out, activityAliases) { ActivityAliasRawText.Externalizer.save(out, it) }
        writeSeq(out, customPermissionNames) { writeUTF(out, it) }
        writeSeq(out, customPermissionGroupNames) { writeUTF(out, it) }
        writeNullable(out, debuggable) { writeUTF(out, it) }
        writeNullable(out, enabled) { writeUTF(out, it) }
        writeNullable(out, minSdkLevel) { writeUTF(out, it) }
        writeNullable(out, packageName) { writeUTF(out, it) }
        writeSeq(out, usedPermissionNames) { writeUTF(out, it) }
        writeNullable(out, targetSdkLevel) { writeUTF(out, it) }
        writeNullable(out, theme) { writeUTF(out, it) }
      }
    }

    override fun read(`in`: DataInput) = AndroidManifestRawText(
      activities = readSeq(`in`) { ActivityRawText.Externalizer.read(`in`) }.toSet(),
      activityAliases = readSeq(`in`) { ActivityAliasRawText.Externalizer.read(`in`) }.toSet(),
      customPermissionNames = readSeq(`in`) { readUTF(`in`) }.toSet(),
      customPermissionGroupNames = readSeq(`in`) { readUTF(`in`) }.toSet(),
      debuggable = readNullable(`in`) { readUTF(`in`) },
      enabled = readNullable(`in`) { readUTF(`in`) },
      minSdkLevel = readNullable(`in`) { readUTF(`in`) },
      packageName = readNullable(`in`) { readUTF(`in`) },
      usedPermissionNames = readSeq(`in`) { readUTF(`in`) }.toSet(),
      targetSdkLevel = readNullable(`in`) { readUTF(`in`) },
      theme = readNullable(`in`) { readUTF(`in`) }
    )
  }
}

/**
 * Structured pieces of raw text from an AndroidManifest.xml file corresponding to a subset of an
 * activity tag's attributes and sub-tags.
 *
 * @see AndroidManifestRawText
 */
data class ActivityRawText(val name: String?, val enabled: String?, val intentFilters: Set<IntentFilterRawText>) {
  /**
   * Singleton responsible for serializing/de-serializing [ActivityRawText]s to/from disk.
   *
   * [AndroidManifestIndex] uses this externalizer to keep its cache within its memory limit
   * and also to persist indexed data between IDE sessions. Any structural change to [ActivityRawText]
   * requires an update to the schema used here, and any update to the schema requires us to increment
   * [AndroidManifestIndex.getVersion].
   */
  object Externalizer : DataExternalizer<ActivityRawText> {
    override fun save(out: DataOutput, value: ActivityRawText) {
      value.apply {
        writeNullable(out, name) { writeUTF(out, it) }
        writeNullable(out, enabled) { writeUTF(out, it) }
        writeSeq(out, intentFilters) { IntentFilterRawText.Externalizer.save(out, it) }
      }
    }

    override fun read(`in`: DataInput) = ActivityRawText(
      name = readNullable(`in`) { readUTF(`in`) },
      enabled = readNullable(`in`) { readUTF(`in`) },
      intentFilters = readSeq(`in`) { IntentFilterRawText.Externalizer.read(`in`) }.toSet()
    )
  }
}

/**
 * Structured pieces of raw text from an AndroidManifest.xml file corresponding to a subset of an
 * activity alias tag's attributes and sub-tags.
 *
 * @see AndroidManifestRawText
 */
data class ActivityAliasRawText(
  val name: String?,
  val targetActivity: String?,
  val enabled: String?,
  val intentFilters: Set<IntentFilterRawText>
) {
  /**
   * Singleton responsible for serializing/de-serializing [ActivityAliasRawText]s to/from disk.
   *
   * [AndroidManifestIndex] uses this externalizer to keep its cache within its memory limit
   * and also to persist indexed data between IDE sessions. Any structural change to [ActivityAliasRawText]
   * requires an update to the schema used here, and any update to the schema requires us to increment
   * [AndroidManifestIndex.getVersion].
   */
  object Externalizer : DataExternalizer<ActivityAliasRawText> {
    override fun save(out: DataOutput, value: ActivityAliasRawText) {
      value.apply {
        writeNullable(out, name) { writeUTF(out, it) }
        writeNullable(out, targetActivity) { writeUTF(out, it) }
        writeNullable(out, enabled) { writeUTF(out, it) }
        writeSeq(out, intentFilters) { IntentFilterRawText.Externalizer.save(out, it) }
      }
    }

    override fun read(`in`: DataInput) = ActivityAliasRawText(
      name = readNullable(`in`) { readUTF(`in`) },
      targetActivity = readNullable(`in`) { readUTF(`in`) },
      enabled = readNullable(`in`) { readUTF(`in`) },
      intentFilters = readSeq(`in`) { IntentFilterRawText.Externalizer.read(`in`) }.toSet()
    )
  }
}

/**
 * Structured pieces of raw text from an AndroidManifest.xml file corresponding to a subset of an
 * intent filter tag's attributes and sub-tags.
 *
 * @see AndroidManifestRawText
 */
data class IntentFilterRawText(val actionNames: Set<String>, val categoryNames: Set<String>) {
  /**
   * Singleton responsible for serializing/de-serializing [IntentFilterRawText]s to/from disk.
   *
   * [AndroidManifestIndex] uses this externalizer to keep its cache within its memory limit
   * and also to persist indexed data between IDE sessions. Any structural change to [IntentFilterRawText]
   * requires an update to the schema used here, and any update to the schema requires us to increment
   * [AndroidManifestIndex.getVersion].
   */
  object Externalizer : DataExternalizer<IntentFilterRawText> {
    override fun save(out: DataOutput, value: IntentFilterRawText) {
      value.apply {
        writeSeq(out, actionNames) { writeUTF(out, it) }
        writeSeq(out, categoryNames) { writeUTF(out, it) }
      }
    }

    override fun read(`in`: DataInput) = IntentFilterRawText(
      actionNames = readSeq(`in`) { readUTF(`in`) }.toSet(),
      categoryNames = readSeq(`in`) { readUTF(`in`) }.toSet()
    )
  }
}
