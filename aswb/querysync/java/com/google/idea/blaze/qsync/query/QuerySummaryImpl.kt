/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.query

import com.google.common.annotations.VisibleForTesting
import com.google.devtools.build.lib.query2.proto.proto2api.Build
import com.google.idea.blaze.common.Interners
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.Label.Companion.fromWorkspacePackageAndName
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path

/**
 * Summaries the output from a `query` invocation into just the data needed by the rest of
 * querysync.
 *
 *
 * The main purpose of the summarized output is to allow the outputs from multiple `query`
 * invocations to be combined. This enables delta updates to the project.
 *
 *
 * If extra data from the `query` invocation is needed by later stages of sync, that data
 * should be added to the [Query.Summary] proto and this code should be updated accordingly.
 * The proto should remain a simple mapping of data from the build proto, i.e. no complex
 * functionality should be added to this class. Non-trivial calculations based on the output of the
 * query belong in [com.google.idea.blaze.qsync.BlazeQueryParser] instead.
 *
 *
 * Instances of the the [Query.Summary] proto are maintained in memory so data should not
 * be added to it unnecessarily.
 */
data class QuerySummaryImpl(
  private val proto: Query.Summary,
) : QuerySummary {

  override val isCompatibleWithCurrentPluginVersion get(): Boolean = proto.version == PROTO_VERSION

  /** Do not generate toString, this object is too large  */
  override fun toString(): String {
    return super.toString()
  }

  /**
   * An opaque proto buffer to be serialized with the project state and re-create the [ ] using [QuerySummaryImpl.create].
   */
  override fun protoForSerializationOnly(): Query.Summary = proto

  private class StringIndexer {
    private val strings: MutableMap<String, Int> = hashMapOf()
    private val list: MutableList<String> = mutableListOf()

    init {
      strings.put("", 0)
      list.add("")
    }

    fun ruleToStoredRule(r: QueryData.Rule): Query.StoredRule {
      val builder = Query.StoredRule.newBuilder()
        .setLabel(indexLabel(r.label))
        .setRuleClass(index(r.ruleClass))
        .addAllSources(indexLabels(r.sources))
        .addAllDeps(indexLabels(r.deps))
        .addAllIdlSources(indexLabels(r.idlSources))
        .addAllRuntimeDeps(indexLabels(r.runtimeDeps))
        .addAllResourceFiles(indexLabels(r.resourceFiles))
        .setTestApp(index(r.testApp))
        .setInstruments(index(r.instruments))
        .setCustomPackage(index(r.customPackage))
        .addAllHdrs(indexLabels(r.hdrs))
        .addAllCopts(index(r.copts))
        .addAllTags(index(r.tags))
        .setMainClass(index(r.mainClass))
      if (r.manifest != null) {
        builder.setManifest(indexLabel(r.manifest))
      }
      return builder.build()
    }

    fun addStringAndGetIndex(s: String): Int {
      list.add(intern(s))
      return list.size - 1
    }

    fun index(s: String): Int {
      return strings.getOrPut(s) { this.addStringAndGetIndex(s) }
    }

    fun indexLabel(l: Label): Query.StoredLabel {
      return Query.StoredLabel.newBuilder()
        .setWorkspace(index(l.workspace))
        .setBuildPackage(index(l.buildPackage))
        .setName(index(l.name))
        .build()
    }

    fun indexLabels(labels: Collection<Label>): List<Query.StoredLabel> {
      return labels.map { this.indexLabel(it) }
    }

    fun index(ss: Collection<String>): List<Int> {
      return ss.map { strings.getOrPut(it) { this.addStringAndGetIndex(it) } }
    }

    fun list(): List<String> {
      return list.toList()
    }

    fun sourceFileToStoredSourceFile(it: QueryData.SourceFile): Query.StoredSourceFile {
      return Query.StoredSourceFile.newBuilder()
        .setLabel(indexLabel(it.label))
        .addAllSubinclude(indexLabels(it.subincliudes))
        .build()
    }
  }

  private class StringLookup(private val list: MutableList<String>) {
    fun storedRuleToRule(r: Query.StoredRule): QueryData.Rule {
      return QueryData.Rule(
        label = lookupLabel(r.label),
        ruleClass = lookupString(r.ruleClass),
        sources = lookupLabels(r.sourcesList),
        deps = lookupLabels(r.depsList),
        idlSources = lookupLabels(r.idlSourcesList),
        runtimeDeps = lookupLabels(r.runtimeDepsList),
        resourceFiles = lookupLabels(r.resourceFilesList),
        testApp = lookupString(r.testApp),
        instruments = lookupString(r.instruments),
        customPackage = lookupString(r.customPackage),
        hdrs = lookupLabels(r.hdrsList),
        copts = lookupStrings(r.coptsList),
        tags = lookupStrings(r.tagsList),
        mainClass = lookupString(r.mainClass),
        manifest = if (r.hasManifest()) lookupLabel(r.manifest) else null,
        testRule = if (r.hasTestRule()) lookupLabel(r.testRule) else null,
      )
    }

    fun storedSourceFileToSourceFile(s: Query.StoredSourceFile): QueryData.SourceFile {
      return QueryData.SourceFile(lookupLabel(s.label), lookupLabels(s.subincludeList))
    }

    fun lookupString(s: Int): String {
      return list[s]
    }

    fun lookupStrings(ss: Collection<Int>): List<String> {
      return ss.map { list[it] }
    }

    fun lookupLabel(l: Query.StoredLabel): Label {
      return Label(
        lookupString(l.workspace),
        lookupString(l.buildPackage),
        lookupString(l.name)
      )
    }

    fun lookupLabels(ll: Collection<Query.StoredLabel>): List<Label> {
      return ll.map { this.lookupLabel(it) }
    }
  }

  override val queryStrategy: QuerySpec.QueryStrategy
    get() {
      return when (proto.getQueryStrategy()) {
        Query.Summary.QueryStrategy.QUERY_STRATEGY_FILTERING_TO_KNOWN_AND_USED_TARGETS ->
          QuerySpec.QueryStrategy.FILTERING_TO_KNOWN_AND_USED_TARGETS

        Query.Summary.QueryStrategy.QUERY_STRATEGY_PLAIN_WITH_SAFE_FILTERS -> QuerySpec.QueryStrategy.PLAIN_WITH_SAFE_FILTERS

        Query.Summary.QueryStrategy.QUERY_STRATEGY_PLAIN, Query.Summary.QueryStrategy.QUERY_STRATEGY_UNKNOWN -> QuerySpec.QueryStrategy.PLAIN

        Query.Summary.QueryStrategy.UNRECOGNIZED -> throw IllegalStateException(proto.getQueryStrategy().toString())
      }
    }

  /**
   * Returns the map of source files included in the query output.
   *
   *
   * This is a map of source target label to the [QueryData.SourceFile] proto representing it.
   */

  override val sourceFilesMap: Map<Label, QueryData.SourceFile> by lazy {
    val lookup = StringLookup(proto.stringStorage.indexedStringsList)
    proto.sourceFilesList
      .map { lookup.storedSourceFileToSourceFile(it) }
      .associateBy { it.label }
  }

  /**
   * Returns the map of rules included in the query output.
   *
   *
   * This is a map of rule label to the [QueryData.Rule] proto representing it.
   */
  override val rulesMap: Map<Label, QueryData.Rule> by lazy {
    val lookup = StringLookup(proto.stringStorage.indexedStringsList)
    proto.storedRulesList
      .map { lookup.storedRuleToRule(it) }
      .associateBy{it.label}
  }

  override val packagesWithErrors: Set<Path> by lazy {
    proto.packagesWithErrorsList
      .map { Label.of(it) }
      .map { it.getBuildPackagePath() }  // The packages are BUILD file labels.
      .toSet()
  }

  /**
   * Returns the set of build packages in the query output.
   *
   *
   * The packages are workspace relative paths that contain a BUILD file.
   */
  override val packages: PackageSet by lazy {
    PackageSet(sourceFilesMap.keys.map { it.getBuildPackagePath() }.toSet() + packagesWithErrors)
  }

  /**
   * Returns a map of .bzl file labels to BUILD file labels that include them.
   *
   *
   * This is used to determine, for example, which build files include a given .bzl file.
   */
  override val reverseSubincludeMap by lazy {
    sourceFilesMap.entries.asSequence()
      .flatMap { entry ->
        entry.value.subincliudes.asSequence().map { subinclude -> subinclude to entry.key }
      }
      .groupBy({ it.first.toFilePath() }, { it.second.toFilePath() })
      .mapValues { it.value.toSet() }
  }

  /**
   * Returns the set of labels of all files includes from BUILD files.
   */
  override val allBuildIncludedFiles: Set<Label> by lazy {
    sourceFilesMap.values
      .flatMap { it.subincliudes }
      .toSet()
  }

  override val packagesWithErrorsCount: Int get() = proto.packagesWithErrorsCount
  override val rulesCount: Int get() = proto.storedRulesCount

  /**
   * Builder for [QuerySummaryImpl]. This should be used when constructing a summary from a map of
   * source files and rules. To construct one from a serialized proto, you should use [ ][QuerySummaryImpl.create] instead.
   */
  class Builder internal constructor() {
    private var indexer: StringIndexer = StringIndexer()
    private val builder: Query.Summary.Builder = Query.Summary.newBuilder().setVersion(PROTO_VERSION)

    fun putAllSourceFiles(sourceFileMap: Map<Label, QueryData.SourceFile>): Builder {
      builder.addAllSourceFiles(sourceFileMap.values.map { indexer.sourceFileToStoredSourceFile(it) })
      return this
    }

    fun putSourceFiles(sourceFile: QueryData.SourceFile): Builder {
      builder.addSourceFiles(indexer.sourceFileToStoredSourceFile(sourceFile))
      return this
    }

    fun putAllRules(rules: Collection<QueryData.Rule>): Builder {
      builder.addAllStoredRules(rules.map { indexer.ruleToStoredRule(it) })
      return this
    }

    fun putRules(rule: QueryData.Rule): Builder {
      builder.addStoredRules(indexer.ruleToStoredRule(rule))
      return this
    }

    fun putAllPackagesWithErrors(packagesWithErrors: Set<Path>): Builder {
      packagesWithErrors // TODO: b/334110669 - Consider multi workspace-builds.
        .asSequence()
        .map {
          fromWorkspacePackageAndName(
            Label.ROOT_WORKSPACE,
            it,
            "BUILD"
          )
        }
        .map { it.toString() }
        .map { intern(it) }
        .forEach { builder.addPackagesWithErrors(it) }
      return this
    }

    fun putPackagesWithErrors(packageWithErrors: Path): Builder {
      builder.addPackagesWithErrors(
        intern(
          fromWorkspacePackageAndName(Label.ROOT_WORKSPACE, packageWithErrors, "BUILD")
            .toString()
        )
      )
      return this
    }

    fun build(): QuerySummary {
      builder.setStringStorage(
        Query.StringStorage.newBuilder().addAllIndexedStrings(indexer.list())
      )
      return create(builder.build())
    }
  }

  companion object {
    /**
     * The current version of the Query.Summary proto that this is compatible with. Any persisted
     * protos with a different version embedded in them will be discarded.
     *
     *
     * Whenever changing the logic in this class such that the Query.Summary proto contents will be
     * different for the same input, this version should be incremented.
     */
    @VisibleForTesting
    const val PROTO_VERSION: Int = 12

    // Compile-time dependency attributes, as they appear in streamed_proto output
    private val DEPENDENCY_ATTRIBUTES: Set<String> =
      setOf<String>( // android_local_test depends on junit implicitly using the _junit attribute.
        "\$junit",
        "deps",
        "test_deps",  // java_proto_library and java_lite_proto_library rules depend on the proto runtime
        // library via these proto_toolchain attributes. In Starlark, the attribute names
        // begin with an underscore instead of a colon (e.g., _aspect_java_proto_toolchain).
        ":aspect_java_proto_toolchain",
        ":aspect_proto_toolchain_for_javalite",  // This is not strictly correct, as source files of rule with 'export' do not
        // depend on exported targets.
        "exports"
      )

    // Compile time dependency attributes scoped to specific rule kind, for cases where sync does not
    // need to always need to traverse the attribute.
    private val RULE_SCOPED_ATTRIBUTES: Map<String, Set<String>> =
      mapOf<String, Set<String>>(
        "\$toolchain" to
        setOf(
          "_java_grpc_library",
          "_java_lite_grpc_library",
          "kt_jvm_library_helper",
          "android_library",
          "kt_android_library"
        )
      )

    // Runtime dependency attributes
    private val RUNTIME_DEP_ATTRIBUTES: Set<String> =
      setOf<String>( // From android_binary rules used in android_instrumentation_tests
        "instruments",  // From android_instrumentation_test rules
        "test_app"
      )

    // Source attributes.
    private val SRCS_ATTRIBUTES: Set<String> = setOf<String>(
      "srcs", "java_srcs", "kotlin_srcs", "java_test_srcs", "kotlin_test_srcs", "common_srcs"
    )

    @JvmStatic
    fun create(proto: Query.Summary): QuerySummary {
      return QuerySummaryImpl(proto)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun create(
      queryStrategy: QuerySpec.QueryStrategy,
      protoInputStream: InputStream
    ): QuerySummary {
      // IMPORTANT: when changing the logic herein, you should also update PROTO_VERSION above.
      // Failure to do so is likely to result in problems during a partial sync.
      val sourceFileMap: MutableMap<Label, Query.StoredSourceFile> = hashMapOf()
      val ruleMap: MutableMap<Label, Query.StoredRule> = hashMapOf()
      val packagesWithErrors: MutableSet<String> = hashSetOf()
      val indexer = StringIndexer()
      var target: Build.Target?
      while ((Build.Target.parseDelimitedFrom(protoInputStream).also { target = it }) != null) {
        when (target!!.getType()) {
          Build.Target.Discriminator.SOURCE_FILE -> {
            val sourceFileLabel = Label.of(target.sourceFile.getName())
            val sourceFile =
              Query.StoredSourceFile.newBuilder()
                .setLabel(indexer.indexLabel(sourceFileLabel))
                .addAllSubinclude(indexer.indexLabels(target.sourceFile.subincludeList.map { Label.of(it) }))
                .build()
            sourceFileMap.put(sourceFileLabel, sourceFile)
            if (target.sourceFile.packageContainsErrors) {
              packagesWithErrors.add(intern(target.sourceFile.getName()))
            }
          }

          Build.Target.Discriminator.RULE -> {
            // TODO We don't need all rules types in the proto since many are not used later on.
            //   We could filter the rules here, or even create rule-specific proto messages to
            //   reduce the size of the output proto.
            val rule =
              Query.StoredRule.newBuilder()
                .setRuleClass(indexer.index(target.rule.getRuleClass()))
            val label = Label.of(target.rule.getName())
            rule.setLabel(indexer.indexLabel(label))
            for (a in target.rule.attributeList) {
              val attributeName = intern(a.getName())
              when {
                SRCS_ATTRIBUTES.contains(attributeName) -> {
                  rule.addAllSources(indexer.indexLabels(a.asLabelListSafe()))
                }
                attributeName == "hdrs" -> {
                  rule.addAllHdrs(indexer.indexLabels(a.asLabelListSafe()))
                }
                attributeIsTrackedDependency(attributeName, target) -> {
                  rule.addAllDeps(indexer.indexLabels(a.asLabelListSafe()))
                }
                RUNTIME_DEP_ATTRIBUTES.contains(attributeName) -> {
                  rule.addAllRuntimeDeps(indexer.indexLabels(a.asLabelListSafe()))
                }
                attributeName == "idl_srcs" -> {
                  rule.addAllIdlSources(indexer.indexLabels(a.asLabelListSafe()))
                }
                attributeName == "resource_files" -> {
                  rule.addAllResourceFiles(indexer.indexLabels(a.asLabelListSafe()))
                }
                attributeName == "manifest" -> {
                  a.asLabelSafe()
                    ?.let { rule.setManifest(indexer.indexLabel(it)) }
                }
                attributeName == "custom_package" -> {
                  rule.setCustomPackage(indexer.index((a.getStringValue())))
                }
                attributeName == "copts" -> {
                  rule.addAllCopts(indexer.index(a.stringListValueList))
                }
                attributeName == "tags" -> {
                  rule.addAllTags(indexer.index(a.stringListValueList))
                }
                attributeName == "main_class" -> {
                  rule.setMainClass(indexer.index(a.getStringValue()))
                }
                attributeName == "test_app" -> {
                  rule.setTestApp(indexer.index(a.getStringValue()))
                }
                attributeName == "instruments" -> {
                  rule.setInstruments(indexer.index(a.getStringValue()))
                }
                attributeName == "test_rule" -> {
                  a.asLabelSafe()
                    ?.let { rule.setTestRule(indexer.indexLabel(it)) }
                }
              }
            }
            ruleMap.put(label, rule.build())
          }

          else -> {}
        }
      }
      return create(
        Query.Summary.newBuilder()
          .setQueryStrategy(convertQueryStrategy(queryStrategy))
          .setVersion(PROTO_VERSION)
          .addAllSourceFiles(sourceFileMap.values)
          .addAllStoredRules(ruleMap.values)
          .setStringStorage(Query.StringStorage.newBuilder().addAllIndexedStrings(indexer.list()))
          .addAllPackagesWithErrors(packagesWithErrors)
          .build()
      )
    }

    private fun convertQueryStrategy(queryStrategy: QuerySpec.QueryStrategy): Query.Summary.QueryStrategy {
      return when (queryStrategy) {
        QuerySpec.QueryStrategy.PLAIN -> Query.Summary.QueryStrategy.QUERY_STRATEGY_PLAIN
        QuerySpec.QueryStrategy.FILTERING_TO_KNOWN_AND_USED_TARGETS -> Query.Summary.QueryStrategy.QUERY_STRATEGY_FILTERING_TO_KNOWN_AND_USED_TARGETS
        QuerySpec.QueryStrategy.PLAIN_WITH_SAFE_FILTERS -> Query.Summary.QueryStrategy.QUERY_STRATEGY_PLAIN_WITH_SAFE_FILTERS
      }
    }

    private fun attributeIsTrackedDependency(
      attributeName: String,
      target: Build.Target
    ): Boolean {
      if (DEPENDENCY_ATTRIBUTES.contains(attributeName)) {
        return true
      }
      return (RULE_SCOPED_ATTRIBUTES[attributeName] ?: return false).contains(target.rule.getRuleClass())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun create(querySpecStrategy: QuerySpec.QueryStrategy, protoFile: File): QuerySummary {
      return create(querySpecStrategy, BufferedInputStream(FileInputStream(protoFile)))
    }

    @JvmStatic
    fun newBuilder(): Builder = Builder()

    private fun intern(s: String): String {
      return Interners.STRING.intern(s)
    }

    private fun intern(list: List<String>): List<String> = list.map { Interners.STRING.intern(it) }
  }
}

private fun Build.Attribute.asLabelListSafe(): List<Label> {
  return when (this.type) {
    Build.Attribute.Discriminator.LABEL ->
      listOfNotNull(this.stringValue.takeUnless { it.isNullOrEmpty() }?.let { Label.of(it) })
    Build.Attribute.Discriminator.LABEL_LIST ->
      this.stringListValueList.map { Label.of(it) }
    else ->
      emptyList()
  }
}

private fun Build.Attribute.asLabelSafe(): Label? {
  return when (this.type) {
    Build.Attribute.Discriminator.LABEL ->
      this.stringValue.takeUnless { it.isNullOrEmpty() }?.let { Label.of(it) }
    else ->
      null
  }
}
