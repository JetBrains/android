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
import com.android.SdkConstants.ATTR_EXPORTED
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_REQUIRED
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
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M
import com.android.SdkConstants.TAG_USES_SDK
import com.android.tools.apk.analyzer.BinaryXmlParser
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.androidFacet
import com.android.utils.reflection.qualifiedName
import com.google.common.primitives.Shorts
import com.google.devrel.gmscore.tools.apk.arsc.Chunk
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.DataInputOutputUtilRt.readSeq
import com.intellij.openapi.util.io.DataInputOutputUtilRt.writeSeq
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readNullable
import com.intellij.util.io.DataInputOutputUtil.writeNullable
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import com.intellij.util.io.KeyDescriptor
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
import java.io.Reader
import java.util.Objects
import java.util.stream.Stream

private val LOG = Logger.getInstance(AndroidManifestIndex::class.java)

/**
 * A file-based index which maps each AndroidManifest.xml to a single entry, <key: package name, value: structured
 * representation of the manifest's raw text (as an [AndroidManifestRawText])>.
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
class AndroidManifestIndex : FileBasedIndexExtension<String, AndroidManifestRawText>() {
  companion object {
    @JvmField
    val NAME: ID<String, AndroidManifestRawText> = ID.create(::NAME.qualifiedName<AndroidManifestIndex>())

    /**
     * Returns corresponding [AndroidFacet]s by given key(package name)
     * NOTE: This function must be called from a smart read action.
     *
     * This may not be useful for non-Gradle build systems, as they may allow for package name overrides.
     * Most callers should use the build system-dependent AndroidProjectSystem.findAndroidFacetsWithPackageName.
     *
     * @see DumbService.runReadActionInSmartMode
     */
    @JvmStatic
    fun queryByPackageName(project: Project, packageName: String, scope: GlobalSearchScope): List<AndroidFacet> {
      if (!checkIndexAccessibleFor(project)) {
        return emptyList()
      }

      val facets = mutableSetOf<AndroidFacet>()
      val fileBasedIndex = FileBasedIndex.getInstance()
      fileBasedIndex.processFilesContainingAllKeys(NAME, listOf(packageName), scope, null) { relevantFile ->
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(relevantFile)
        module?.androidFacet?.let { facets.add(it) }
        true
      }

      return facets.toList()
    }

    /**
     * Returns the [AndroidManifestRawText] for the given [manifestFile], or null
     * if the file isn't recognized by the index (e.g. because it's malformed).
     * NOTE: This function must be called from a smart read action.
     * @see com.android.tools.idea.projectsystem.MergedManifestContributors
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
     * Returns the [AndroidManifestRawText] for each of the given [facet]'s `MergedManifestContributors` recognized by the index.
     * NOTE: This function must be called from a smart read action.
     * @see com.android.tools.idea.projectsystem.MergedManifestContributors
     * @see DumbService.runReadActionInSmartMode
     */
    @JvmStatic
    fun getDataForMergedManifestContributors(facet: AndroidFacet): Stream<AndroidManifestRawText> {
      val project = facet.module.project
      return if (checkIndexAccessibleFor(project)) {
        facet
          .getModuleSystem()
          .getMergedManifestContributors()
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
        !ApplicationManager.getApplication().isReadAccessAllowed -> {
          LOG.error("Manifest index queried outside of a read action.")
          false
        }
        DumbService.isDumb(project) -> {
          // When we call runReadActionInSmartMode, if it's already called with read access allowed, we don't wait for
          // smart mode to begin, and fails with IndexNotReadyException thrown. So we need to call with try-catch block
          // and try to recover if IndexNotReadyException.
          LOG.info("Manifest index queried outside of a smart mode.")
          true
        }
        else -> true
      }
    }

    /**
     * Returns the [AndroidManifestRawText] for the given [manifestFile], or null
     * if the file isn't recognized by the index (e.g. because it's malformed).
     */
    @JvmStatic
    private fun doGetDataForManifestFile(project: Project, manifestFile: VirtualFile): AndroidManifestRawText? {
      ProgressManager.checkCanceled()
      val data: MutableMap<String, AndroidManifestRawText> = FileBasedIndex.getInstance().getFileData(NAME, manifestFile, project)
      check(data.values.size <= 1)
      return data.values.firstOrNull()
    }
  }

  override fun getValueExternalizer() = AndroidManifestRawText.Externalizer
  override fun getName() = NAME
  override fun getVersion() = 10
  override fun getIndexer() = Indexer
  override fun getInputFilter() = InputFilter

  object InputFilter : DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
    override fun acceptInput(file: VirtualFile) = file.name == FN_ANDROID_MANIFEST_XML
  }

  object Indexer : DataIndexer<String, AndroidManifestRawText, FileContent> {
    override fun map(inputData: FileContent): Map<String, AndroidManifestRawText?> {
      val manifestRawText = computeValue(inputData) ?: return emptyMap()
      return mapOf(StringUtil.notNullize(manifestRawText.packageName) to manifestRawText)
    }

    private fun computeValue(inputData: FileContent): AndroidManifestRawText? {
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
            else -> parser.skipSubTreeWithExceptionCaught()
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
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: RuntimeException) {
        LOG.warn(e)
      }
      return null
    }

    private fun FileContent.toManifestReader(): Reader {
      val inBinaryManifestFormat = content.size >= 2 && Shorts.fromBytes(content[1], content[0]) == Chunk.Type.XML.code()
      return if (inBinaryManifestFormat) {
        // There's an upstream IntelliJ issue where files in binary manifest format are
        // mistyped as regular XML files instead of binary files. This causes other indices
        // like DomFileIndex to access the file content in an unsupported way (i.e. using
        // FileContentImpl#getContentAsText), which corrupts the bytes returned by
        // FileContentImpl#getContent. As a workaround, we'll get the uncorrupted content
        // through the VirtualFile. This may result in staleness issues, but it's better
        // than nothing and we've only ever seen files in this format coming from compressed
        // archives that don't change frequently.
        // TODO(b/143528395): Go back to using FileContentImpl#getContent once the upstream issue has been resolved.
        val decoded = BinaryXmlParser.decodeXml(file.contentsToByteArray())
        ByteArrayInputStream(decoded).reader()
      }
      else {
        CharArrayUtil.readerFromCharSequence(contentAsText)
      }
    }

    /**
     * This is a copy of the KXmlPullParser implementation, except that it catches pull parser exceptions and returns
     * them as an invalid status code so that we can retain whatever information we've already computed.
     */
    private fun KXmlParser.skipSubTreeWithExceptionCaught() {
      require(START_TAG, null, null)
      var level = 1
      var eventType = START_TAG

      while (level > 0 && eventType != END_DOCUMENT) {
        eventType = nextWithExceptionCaught()
        if (eventType == END_TAG) {
          --level
        }
        else if (eventType == START_TAG) {
          ++level
        }
      }
    }

    private fun KXmlParser.parseManifestTag(): AndroidManifestRawText {
      require(START_TAG, null, TAG_MANIFEST)
      val activities = hashSetOf<ActivityRawText>()
      val activityAliases = hashSetOf<ActivityAliasRawText>()
      val customPermissionGroupNames = hashSetOf<String>()
      val customPermissionNames = hashSetOf<String>()
      val enabled = getAttributeValue(ANDROID_URI, ATTR_ENABLED)
      var minSdkLevel: String? = null
      val packageName = getAttributeValue(null, ATTR_PACKAGE)
      val usedPermissionNames = hashSetOf<String>()
      val usedFeatures = hashSetOf<UsedFeatureRawText>()
      var debuggable: String? = null
      var targetSdkLevel: String? = null
      var theme: String? = null

      processChildTags {
        when (name) {
          TAG_APPLICATION -> {
            theme = getAttributeValue(ANDROID_URI, ATTR_THEME)
            debuggable = getAttributeValue(ANDROID_URI, ATTR_DEBUGGABLE)
            processChildTags {
              when (name) {
                TAG_ACTIVITY -> activities.add(parseActivityTag())
                TAG_ACTIVITY_ALIAS -> activityAliases.add(parseActivityAliasTag())
                else -> skipSubTreeWithExceptionCaught()
              }
            }
          }
          TAG_PERMISSION -> {
            androidName?.let(customPermissionNames::add)
            skipSubTreeWithExceptionCaught()
          }
          TAG_PERMISSION_GROUP -> {
            androidName?.let(customPermissionGroupNames::add)
            skipSubTreeWithExceptionCaught()
          }
          TAG_USES_PERMISSION, TAG_USES_PERMISSION_SDK_23, TAG_USES_PERMISSION_SDK_M -> {
            androidName?.let(usedPermissionNames::add)
            skipSubTreeWithExceptionCaught()
          }
          TAG_USES_FEATURE -> {
            val required = getAttributeValue(ANDROID_URI, ATTR_REQUIRED)
            usedFeatures.add(UsedFeatureRawText(androidName, required))
            skipSubTreeWithExceptionCaught()
          }
          TAG_USES_SDK -> {
            minSdkLevel = getAttributeValue(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            targetSdkLevel = getAttributeValue(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
            skipSubTreeWithExceptionCaught()
          }
          else -> skipSubTreeWithExceptionCaught()
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
        usedFeatures = usedFeatures.toSet(),
        targetSdkLevel = targetSdkLevel,
        theme = theme
      )
    }

    private fun KXmlParser.parseActivityTag(): ActivityRawText {
      require(START_TAG, null, TAG_ACTIVITY)
      val activityName: String? = androidName
      val enabled: String? = getAttributeValue(ANDROID_URI, ATTR_ENABLED)
      val exported: String? = getAttributeValue(ANDROID_URI, ATTR_EXPORTED)
      val theme: String? = getAttributeValue(ANDROID_URI, ATTR_THEME)
      val intentFilters = hashSetOf<IntentFilterRawText>()
      processChildTags {
        if (name == TAG_INTENT_FILTER) {
          intentFilters.add(parseIntentFilterTag())
        }
        else {
          skipSubTreeWithExceptionCaught()
        }
      }
      return ActivityRawText(
        name = activityName,
        enabled = enabled,
        exported = exported,
        theme = theme,
        intentFilters = intentFilters.toSet()
      )
    }

    private fun KXmlParser.parseActivityAliasTag(): ActivityAliasRawText {
      require(START_TAG, null, TAG_ACTIVITY_ALIAS)
      val aliasName = androidName
      val targetActivity = getAttributeValue(ANDROID_URI, ATTR_TARGET_ACTIVITY)
      val enabled = getAttributeValue(ANDROID_URI, ATTR_ENABLED)
      val exported: String? = getAttributeValue(ANDROID_URI, ATTR_EXPORTED)
      val intentFilters = hashSetOf<IntentFilterRawText>()
      processChildTags {
        if (name == TAG_INTENT_FILTER) {
          intentFilters.add(parseIntentFilterTag())
        }
        else {
          skipSubTreeWithExceptionCaught()
        }
      }
      return ActivityAliasRawText(
        name = aliasName,
        targetActivity = targetActivity,
        enabled = enabled,
        exported = exported,
        intentFilters = intentFilters.toSet()
      )
    }

    private fun KXmlParser.parseIntentFilterTag(): IntentFilterRawText {
      require(START_TAG, null, TAG_INTENT_FILTER)
      val actionNames = hashSetOf<String>()
      val categoryNames = hashSetOf<String>()
      processChildTags {
        when (name) {
          TAG_ACTION -> androidName?.let(actionNames::add)
          TAG_CATEGORY -> androidName?.let(categoryNames::add)
        }
        skipSubTreeWithExceptionCaught()
      }
      return IntentFilterRawText(
        actionNames = actionNames.toSet(),
        categoryNames = categoryNames.toSet()
      )
    }
  }

  override fun dependsOnFileContent() = true

  override fun getKeyDescriptor(): KeyDescriptor<String> {
    return EnumeratorStringDescriptor.INSTANCE
  }
}

/**
 * Invokes [processChildTag] for each start tag in the subtree of the current tag.
 * After this function is called, the parser will have moved to the current tag's end tag.
 *
 * [processChildTag] must also consume each child tag for which it is called,
 * moving the parser to the child's end tag. In order to make this actually
 * process just the children of the current tag, [processChildTag] should consume
 * the subtree of each child (e.g. by calling [KXmlParser.skipSubTreeWithExceptionCaught]
 * or recursively calling [processChildTags]).
 */
private inline fun KXmlParser.processChildTags(crossinline processChildTag: KXmlParser.() -> Unit) {
  require(START_TAG, null, null)
  val parentName = name
  val parentDepth = depth
  var eventType = nextWithExceptionCaught()
  while (eventType != END_DOCUMENT) {
    when (eventType) {
      START_TAG -> {
        if (parentDepth + 1 != depth) {
          LOG.warn("Child start tag depth mismatch: expected ${parentDepth + 1}, got $depth for tag \"$name\" (child of \"$parentName\").")
          return
        }
        processChildTag()
      }
      END_TAG -> {
        if (parentDepth != depth) {
          LOG.warn("Parent end tag depth mismatch: expected $parentDepth, got $depth for tag \"$name\".")
        }
        return
      }
    }
    eventType = nextWithExceptionCaught()
  }
}

private val ERROR_TAG get() = -1

private fun KXmlParser.nextWithExceptionCaught(): Int {
  try {
    return next()
  }
  catch (e: XmlPullParserException) {
    LOG.warn(e.message)

    if (this.eventType == END_TAG) {
      return END_TAG
    }

    return ERROR_TAG
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
  val usedFeatures: Set<UsedFeatureRawText>,
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
        writeSeq(out, usedFeatures) { UsedFeatureRawText.Externalizer.save(out, it) }
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
      usedFeatures = readSeq(`in`) { UsedFeatureRawText.Externalizer.read(`in`) }.toSet(),
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
data class ActivityRawText(
  val name: String?,
  val enabled: String?,
  val exported: String?,
  val theme: String?,
  val intentFilters: Set<IntentFilterRawText>
) {
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
        writeNullable(out, exported) { writeUTF(out, it) }
        writeNullable(out, theme) { writeUTF(out, it) }
        writeSeq(out, intentFilters) { IntentFilterRawText.Externalizer.save(out, it) }
      }
    }

    override fun read(`in`: DataInput) = ActivityRawText(
      name = readNullable(`in`) { readUTF(`in`) },
      enabled = readNullable(`in`) { readUTF(`in`) },
      exported = readNullable(`in`) { readUTF(`in`) },
      theme = readNullable(`in`) { readUTF(`in`) },
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
  val exported: String?,
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
        writeNullable(out, exported) { writeUTF(out, it) }
        writeSeq(out, intentFilters) { IntentFilterRawText.Externalizer.save(out, it) }
      }
    }

    override fun read(`in`: DataInput) = ActivityAliasRawText(
      name = readNullable(`in`) { readUTF(`in`) },
      targetActivity = readNullable(`in`) { readUTF(`in`) },
      enabled = readNullable(`in`) { readUTF(`in`) },
      exported = readNullable(`in`) { readUTF(`in`) },
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

/**
 * Structured pieces of raw text from an AndroidManifest.xml file corresponding to a subset of uses-feature tag's
 * attributes.
 *
 * @see AndroidManifestRawText
 */
data class UsedFeatureRawText(val name: String?, val required: String?) {
  /**
   * Singleton responsible for serializing/de-serializing [UsedFeatureRawText]s to/from disk.
   *
   * [AndroidManifestIndex] uses this externalizer to keep its cache within its memory limit
   * and also to persist indexed data between IDE sessions. Any structural change to [UsedFeatureRawText]
   * requires an update to the schema used here, and any update to the schema requires us to increment
   * [AndroidManifestIndex.getVersion].
   */
  object Externalizer : DataExternalizer<UsedFeatureRawText> {
    override fun save(out: DataOutput, value: UsedFeatureRawText) {
      value.apply {
        writeNullable(out, name) { writeUTF(out, it) }
        writeNullable(out, required) { writeUTF(out, it) }
      }
    }

    override fun read(`in`: DataInput) = UsedFeatureRawText(
      name = readNullable(`in`) { readUTF(`in`) },
      required = readNullable(`in`) { readUTF(`in`) }
    )
  }
}