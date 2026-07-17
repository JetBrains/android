#!/usr/bin/env kotlin

/**
 * Gradle Problems Report Aggregator
 *
 * Abandon all hope, ye who enter here (as in, code below is fully generated via AI)
 * I recommend not engaging with the code below at all both in terms of reading it and
 * reviewing it. Instead, edit it via AI in case requirements or the expected format change.
 * That said, I have been using this and at least can attest to the correctness of the script's header which you are now
 * reading. If some assumption of the script changes substantially, I recommend simply recreating this script from
 * scratch using AI.
 *
 * DESCRIPTION:
 * Throwaway script to scan test execution output logs (e.g., from remote test executions in Bazel)
 * for Gradle problem reports (`problems-report.html`) and aggregates all discovered problems into
 * a single unified HTML report with problematic code snippets included for easier discovery.
 *
 * When a Gradle problem report lacks specific source file locations (e.g., global script
 * deprecations like `val name by extra`), the script analyzes `daemon-*.log` files in the same test
 * output archive to correlate warnings and extract exact line-highlighted source code snippets. This mostly highlights
 * everything correctly apart from some issues in some corner cases.
 *
 * The generated report groups issues by problem type (Issue ID), and then groups occurrences by
 * their code snippet, full problem message, and test target, using a 3-column layout:
 * - "Code snippet": Shows line-highlighted source code or a clear explanation when no file snippet exists.
 * - "Full problem message": Displays detailed label, description, solutions, and documentation links.
 * - "Targets/Tests": Lists affected test targets and exact location paths.
 *
 * USAGE:
 * 1. Run a special bazel test which includes all test projects and Gradle daemon logs in the outputs
 * Recommended invocation to be run remotely to detect all failures:
 *
 * 	bazel --output_base='<bazel_output_dir>' test \
 * 	--jvmopt=-DKEEP_TEST_PROJECTS \
 * 	--jvmopt=-DCOLLECT_GRADLE_DIAGNOSTICS \
 * 	--test_tag_filters=-noci:studio-linux,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate-release,-no_k2 -- //tools/...
 *
 *
 * 2. Then run this script itself, replacing the bazel_output_dir with the one initially specified:
 *
 * prebuilts/studio/intellij-sdk/AI/linux/android-studio/plugins/Kotlin/kotlinc/bin/kotlin \
 *   tools/adt/idea/project-system-gradle/scripts/report_gradle_problems.main.kts `bazel --output_base='<bazel_output_dir>' info bazel-testlogs` <output_html>
 *
 * This should output the report at <output_html> path.
 *
 * RECOMMENDED WORKFLOW:
 * When fixing individual issues, one should run smaller suites with the work-in-progress fixes instead of using the catch all //tools/...
 * target and generate reports based on that.
 *
 * NOTE:
 * Currently the execution above contains AGP upgrade assistant tests as well, causing reports to be inflated with a bunch of older
 * deprecations that are not relevant. Ideally we should tag those tests and filter them out from executions (or from script processing)
 * ```
 *
 * ## Arguments
 * - `bazel_output_dir`: The directory to scan for test logs and output manifests.
 * - `output_html`: The destination file path for the generated aggregation HTML report.
 *   Defaults to `tools/adt/idea/report_gradle_problems.html`
 */

import java.io.File
import java.util.zip.ZipFile
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

// --- Data Models ---

data class SnippetOccurrence(
    val targetName: String,
    val locationPath: String
)

data class ProblemRowKey(
    val snippetHtml: String?, // null if no file snippet
    val noSnippetReason: String, // shown if snippetHtml is null
    val fullMessageHtml: String
)

class IssueBucket(val issueId: String) {
    val rows = mutableMapOf<ProblemRowKey, MutableList<SnippetOccurrence>>()
    var totalOccurrences = 0

    fun addOccurrence(key: ProblemRowKey, occurrence: SnippetOccurrence) {
        totalOccurrences++
        val list = rows.getOrPut(key) { mutableListOf() }
        list.add(occurrence)
    }
}

data class DaemonLogLoc(
    val path: String,
    val line: Int,
    val message: String
)

// --- Lightweight JSON Parser (zero external dependencies) ---

sealed class JsonValue
data class JsonObj(val map: Map<String, JsonValue>) : JsonValue()
data class JsonArr(val list: List<JsonValue>) : JsonValue()
data class JsonStr(val value: String) : JsonValue()
data class JsonNum(val value: Double) : JsonValue()
data class JsonBool(val value: Boolean) : JsonValue()
object JsonNull : JsonValue()

fun JsonValue?.asObj(): JsonObj? = this as? JsonObj
fun JsonValue?.asArr(): JsonArr? = this as? JsonArr
fun JsonValue?.asStr(): String? = (this as? JsonStr)?.value
fun JsonValue?.asNum(): Double? = (this as? JsonNum)?.value
fun JsonObj.getStr(key: String): String? = map[key].asStr()
fun JsonObj.getArr(key: String): List<JsonValue> = map[key].asArr()?.list ?: emptyList()

class SimpleJsonParser(private val text: String) {
    private var pos = 0

    fun parse(): JsonValue {
        skipWhitespace()
        return parseValue()
    }

    private fun skipWhitespace() {
        while (pos < text.length && text[pos] <= ' ') {
            pos++
        }
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (pos >= text.length) return JsonNull
        return when (val ch = text[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            else -> if (ch == '-' || ch.isDigit()) parseNumber() else { pos++; JsonNull }
        }
    }

    private fun parseObject(): JsonObj {
        pos++ // skip '{'
        val map = mutableMapOf<String, JsonValue>()
        skipWhitespace()
        if (pos < text.length && text[pos] == '}') {
            pos++
            return JsonObj(map)
        }
        while (pos < text.length) {
            skipWhitespace()
            if (pos >= text.length || text[pos] != '"') break
            val key = (parseString() as JsonStr).value
            skipWhitespace()
            if (pos < text.length && text[pos] == ':') pos++
            val valObj = parseValue()
            map[key] = valObj
            skipWhitespace()
            if (pos < text.length && text[pos] == ',') {
                pos++
            } else if (pos < text.length && text[pos] == '}') {
                pos++
                break
            } else {
                break
            }
        }
        return JsonObj(map)
    }

    private fun parseArray(): JsonArr {
        pos++ // skip '['
        val list = mutableListOf<JsonValue>()
        skipWhitespace()
        if (pos < text.length && text[pos] == ']') {
            pos++
            return JsonArr(list)
        }
        while (pos < text.length) {
            val valObj = parseValue()
            list.add(valObj)
            skipWhitespace()
            if (pos < text.length && text[pos] == ',') {
                pos++
            } else if (pos < text.length && text[pos] == ']') {
                pos++
                break
            } else {
                break
            }
        }
        return JsonArr(list)
    }

    private fun parseString(): JsonStr {
        pos++ // skip starting quote
        val sb = StringBuilder()
        while (pos < text.length) {
            val ch = text[pos++]
            if (ch == '"') break
            if (ch == '\\' && pos < text.length) {
                when (val esc = text[pos++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (pos + 4 <= text.length) {
                            val hex = text.substring(pos, pos + 4)
                            try { sb.append(hex.toInt(16).toChar()) } catch (_: Exception) {}
                            pos += 4
                        }
                    }
                    else -> sb.append(esc)
                }
            } else {
                sb.append(ch)
            }
        }
        return JsonStr(sb.toString())
    }

    private fun parseBoolean(): JsonBool {
        return if (text.startsWith("true", pos)) {
            pos += 4
            JsonBool(true)
        } else if (text.startsWith("false", pos)) {
            pos += 5
            JsonBool(false)
        } else {
            pos++
            JsonBool(false)
        }
    }

    private fun parseNull(): JsonNull {
        if (text.startsWith("null", pos)) {
            pos += 4
        } else {
            pos++
        }
        return JsonNull
    }

    private fun parseNumber(): JsonNum {
        val start = pos
        if (pos < text.length && text[pos] == '-') pos++
        while (pos < text.length && text[pos].isDigit()) pos++
        if (pos < text.length && text[pos] == '.') {
            pos++
            while (pos < text.length && text[pos].isDigit()) pos++
        }
        if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
            pos++
            if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
            while (pos < text.length && text[pos].isDigit()) pos++
        }
        val numStr = text.substring(start, pos)
        return JsonNum(numStr.toDoubleOrNull() ?: 0.0)
    }
}

// --- Helper Functions ---

fun htmlEscape(text: String): String {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
}

fun extractSnippetFromZip(zip: ZipFile, relPath: String, lineNum: Int): String? {
    val entry = zip.getEntry(relPath) ?: return null
    return try {
        val lines = zip.getInputStream(entry).reader().use { it.readLines() }
        val l = lineNum - 1 // 0-indexed
        val start = maxOf(0, l - 3)
        val end = minOf(lines.size, l + 4)
        val sb = StringBuilder()
        for (i in start until end) {
            val isTarget = (i == l)
            val lineContent = htmlEscape(lines[i])
            val lineClass = if (isTarget) "line target-line" else "line"
            sb.append("<div class=\"$lineClass\"><span class=\"line-num\">${i + 1}</span>$lineContent</div>\n")
        }
        sb.toString().trimEnd()
    } catch (e: Exception) {
        null
    }
}

fun extractDaemonLogLocations(zip: ZipFile): List<DaemonLogLoc> {
    val locs = mutableListOf<DaemonLogLoc>()
    val regex = "^[we]: (?:file://)?(.*?):(\\d+):(?:\\d+:)?\\s*(.*)".toRegex()
    for (entry in zip.entries()) {
        if (!entry.isDirectory && entry.name.contains("daemon-") && entry.name.endsWith(".log")) {
            try {
                zip.getInputStream(entry).reader().useLines { lines ->
                    for (l in lines) {
                        val m = regex.find(l)
                        if (m != null) {
                            val path = m.groupValues[1]
                            val lineNum = m.groupValues[2].toIntOrNull() ?: continue
                            val msg = m.groupValues[3]
                            locs.add(DaemonLogLoc(path, lineNum, msg))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }
    return locs
}

fun DaemonLogLoc.matchesDiag(solutionsArr: List<String>, label: String?, details: String?): Boolean {
    for (sol in solutionsArr) {
        if (sol.isNotBlank() && message.contains(sol)) return true
    }
    if (!label.isNullOrBlank()) {
        if (message.contains(label)) return true
        if (label.contains("by extra") && (message.contains("by extra") || message.contains("ExtraPropertiesExtension.invoke"))) return true
        if (label.contains("withJava()") && message.contains("withJava()")) return true
    }
    if (!details.isNullOrBlank() && message.contains(details)) return true
    return false
}

fun processReportContent(
    content: String,
    targetName: String,
    zip: ZipFile,
    daemonLogLocs: List<DaemonLogLoc>,
    issues: MutableMap<String, IssueBucket>
) {
    val beginMarker = "// begin-report-data"
    val endMarker = "// end-report-data"
    val beginIdx = content.indexOf(beginMarker)
    val endIdx = content.indexOf(endMarker, beginIdx)
    if (beginIdx < 0 || endIdx < 0) return

    val jsonStr = content.substring(beginIdx + beginMarker.length, endIdx).trim()
    val rootObj = SimpleJsonParser(jsonStr).parse().asObj() ?: return
    val diagnostics = rootObj.getArr("diagnostics")

    for (diagVal in diagnostics) {
        val diag = diagVal.asObj() ?: continue
        val problemIdArr = diag.getArr("problemId")
        val idParts = problemIdArr.mapNotNull {
            val obj = it.asObj()
            obj?.getStr("displayName") ?: obj?.getStr("name")
        }
        val issueId = if (idParts.isNotEmpty()) idParts.joinToString(" > ") else (diag.getStr("contextualLabel") ?: "Unknown Gradle Problem")
        val problemDetails = diag.getStr("problemDetails")
        val contextualLabel = diag.getStr("contextualLabel")
        val solutionsArr = diag.getArr("solutions").mapNotNull { it.asStr() }
        val docLink = diag.getStr("documentationLink")

        val msgBuilder = StringBuilder()
        if (!contextualLabel.isNullOrBlank()) {
            msgBuilder.append("<div style=\"font-weight: 600; margin-bottom: 8px; color: #202124;\">${htmlEscape(contextualLabel)}</div>")
        }
        if (!problemDetails.isNullOrBlank()) {
            msgBuilder.append("<div style=\"margin-bottom: 8px; color: #3c4043;\">${htmlEscape(problemDetails)}</div>")
        }
        if (solutionsArr.isNotEmpty()) {
            msgBuilder.append("<div style=\"margin-bottom: 8px;\"><span style=\"font-weight: 600; color: #1e8e3e;\">Solution: </span>")
            msgBuilder.append(solutionsArr.joinToString("<br>") { htmlEscape(it) })
            msgBuilder.append("</div>")
        }
        if (!docLink.isNullOrBlank()) {
            msgBuilder.append("<div><a href=\"${htmlEscape(docLink)}\" target=\"_blank\" style=\"color: #1a73e8; text-decoration: none;\">Documentation Link &nearr;</a></div>")
        }
        val fullMessageHtml = msgBuilder.toString().ifEmpty { "No details provided" }

        val bucket = issues.getOrPut(issueId) { IssueBucket(issueId) }

        val locations = diag.getArr("locations")
        val fileLocations = locations.mapNotNull { it.asObj() }.filter {
            it.getStr("path") != null && (it.getStr("line") ?: (it.map["line"] as? JsonNum)?.value?.toInt()?.toString()) != null
        }

        if (fileLocations.isNotEmpty()) {
            for (loc in fileLocations) {
                val path = loc.getStr("path")!!
                val line = loc.getStr("line")?.toIntOrNull() ?: (loc.map["line"] as? JsonNum)?.value?.toInt()!!
                val relPathInZip = if (path.contains("/test.outputs/")) {
                    path.substringAfter("/test.outputs/")
                } else {
                    path
                }

                val snippetHtml = extractSnippetFromZip(zip, relPathInZip, line)
                val key = if (snippetHtml != null) {
                    ProblemRowKey(snippetHtml, "", fullMessageHtml)
                } else {
                    ProblemRowKey(null, "No source code snippet available (File not found in archive: $relPathInZip, line $line)", fullMessageHtml)
                }
                bucket.addOccurrence(key, SnippetOccurrence(targetName, "$path:$line"))
            }
        } else {
            // Fallback: try matching against daemon log locations
            val matchedDaemonLocs = daemonLogLocs.filter { it.matchesDiag(solutionsArr, contextualLabel, problemDetails) }
            if (matchedDaemonLocs.isNotEmpty()) {
                for (logLoc in matchedDaemonLocs) {
                    val path = logLoc.path
                    val line = logLoc.line
                    val relPathInZip = if (path.contains("/test.outputs/")) {
                        path.substringAfter("/test.outputs/")
                    } else {
                        path
                    }

                    val snippetHtml = extractSnippetFromZip(zip, relPathInZip, line)
                    val key = if (snippetHtml != null) {
                        ProblemRowKey(snippetHtml, "", fullMessageHtml)
                    } else {
                        ProblemRowKey(null, "No source code snippet available (File not found in archive: $relPathInZip, line $line)", fullMessageHtml)
                    }
                    bucket.addOccurrence(key, SnippetOccurrence(targetName, "$path:$line (from daemon log)"))
                }
            } else {
                // Check if pluginId was reported
                val pluginLoc = locations.mapNotNull { it.asObj() }.firstOrNull { it.getStr("pluginId") != null }
                if (pluginLoc != null) {
                    val pluginId = pluginLoc.getStr("pluginId")!!
                    val key = ProblemRowKey(
                        snippetHtml = null,
                        noSnippetReason = "No source code snippet available (Problem reported at plugin level: $pluginId)",
                        fullMessageHtml = fullMessageHtml
                    )
                    bucket.addOccurrence(key, SnippetOccurrence(targetName, "Plugin: $pluginId"))
                } else {
                    val key = ProblemRowKey(
                        snippetHtml = null,
                        noSnippetReason = "No source code snippet available (Gradle problem report provided no file location for this deprecation)",
                        fullMessageHtml = fullMessageHtml
                    )
                    bucket.addOccurrence(key, SnippetOccurrence(targetName, "No location"))
                }
            }
        }
    }
}

fun generateHtmlReport(
    targetDir: File,
    outputFile: File,
    issues: Map<String, IssueBucket>,
    scannedManifests: Int,
    scannedReports: Int,
    elapsedMillis: Long
) {
    val totalOccurrences = issues.values.sumOf { it.totalOccurrences }
    val sortedIssues = issues.values.sortedWith(
        compareByDescending<IssueBucket> {
            it.issueId.startsWith("Deprecation >", ignoreCase = true)
        }.thenByDescending { it.totalOccurrences }
    )

    val html = StringBuilder()
    html.append("""
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Gradle Problems Aggregation Report</title>
<style>
    body {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        margin: 0;
        padding: 20px 40px;
        background-color: #f8f9fa;
        color: #212529;
        line-height: 1.5;
    }
    .header {
        background: #ffffff;
        padding: 24px;
        border-radius: 8px;
        box-shadow: 0 2px 4px rgba(0,0,0,0.05);
        margin-bottom: 24px;
    }
    .header h1 {
        margin: 0 0 16px 0;
        font-size: 24px;
        color: #1a73e8;
    }
    .stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 16px;
    }
    .stat-card {
        background: #f1f3f4;
        padding: 12px 16px;
        border-radius: 6px;
    }
    .stat-label {
        font-size: 12px;
        color: #5f6368;
        text-transform: uppercase;
        font-weight: 600;
    }
    .stat-value {
        font-size: 18px;
        font-weight: bold;
        color: #202124;
        margin-top: 4px;
        word-break: break-all;
    }
    .category-section {
        margin-top: 32px;
    }
    .category-summary {
        cursor: pointer;
        list-style: none;
    }
    .category-summary::-webkit-details-marker {
        display: none;
    }
    .category-title {
        font-size: 20px;
        border-bottom: 2px solid #e0e0e0;
        padding-bottom: 8px;
        margin-bottom: 16px;
        color: #202124;
        display: block;
        cursor: pointer;
    }
    .issue-card {
        background: #ffffff;
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        padding: 20px;
        margin-bottom: 20px;
        box-shadow: 0 1px 3px rgba(0,0,0,0.05);
    }
    .issue-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        cursor: pointer;
        list-style: none;
    }
    .issue-header::-webkit-details-marker {
        display: none;
    }
    .issue-card[open] .issue-header {
        margin-bottom: 12px;
    }
    .issue-title {
        font-size: 18px;
        font-weight: 600;
        margin: 0;
        color: #d93025;
        word-break: break-all;
    }
    .badge {
        background: #e8f0fe;
        color: #1a73e8;
        padding: 4px 10px;
        border-radius: 16px;
        font-size: 13px;
        font-weight: 600;
        white-space: nowrap;
    }
    .issue-meta {
        font-size: 14px;
        color: #5f6368;
        margin-bottom: 16px;
    }
    .targets-details {
        background: #f8f9fa;
        border: 1px solid #e0e0e0;
        border-radius: 4px;
        padding: 8px 12px;
        margin-bottom: 16px;
    }
    .targets-details summary {
        cursor: pointer;
        font-weight: 500;
        color: #1a73e8;
    }
    .targets-list {
        margin: 8px 0 0 0;
        padding-left: 20px;
        font-family: monospace;
        font-size: 13px;
        max-height: 200px;
        overflow-y: auto;
    }
    .snippets-section-details {
        margin-top: 16px;
    }
    .snippets-section-details > summary {
        cursor: pointer;
        font-weight: 600;
        margin-bottom: 12px;
        font-size: 14px;
        color: #3c4043;
    }
    .row-header {
        display: flex;
        background: #e8eaed;
        border: 1px solid #dcdcdc;
        border-radius: 6px;
        font-weight: 700;
        font-size: 13px;
        color: #3c4043;
        margin-bottom: 8px;
    }
    .col-snippet-hdr { flex: 1 1 38%; padding: 10px 14px; border-right: 1px solid #dcdcdc; }
    .col-message-hdr { flex: 1 1 32%; padding: 10px 14px; border-right: 1px solid #dcdcdc; }
    .col-targets-hdr { flex: 1 1 30%; padding: 10px 14px; }
    .snippet-details {
        display: flex;
        align-items: stretch;
        border: 1px solid #dcdcdc;
        border-radius: 6px;
        margin-bottom: 12px;
        background: #ffffff;
        overflow: hidden;
    }
    .col-snippet {
        flex: 1 1 38%;
        min-width: 0;
        margin: 0;
        background: #282c34;
        color: #abb2bf;
        overflow-x: auto;
        border-right: 1px solid #dcdcdc;
    }
    .col-snippet.no-code {
        background: #fdf8f0;
        color: #8f5000;
        font-style: italic;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        font-size: 13px;
        display: flex;
        align-items: center;
        justify-content: center;
        text-align: center;
        padding: 20px;
        border-right: 1px solid #f9ab00;
    }
    .col-snippet pre.snippet-code {
        margin: 0;
        padding: 12px 0;
        font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, Courier, monospace;
        font-size: 12px;
    }
    .col-message {
        flex: 1 1 32%;
        min-width: 0;
        background: #ffffff;
        color: #202124;
        padding: 14px;
        overflow-y: auto;
        font-size: 13px;
        border-right: 1px solid #dcdcdc;
        line-height: 1.6;
        word-break: break-word;
    }
    .col-targets {
        flex: 1 1 30%;
        min-width: 0;
        max-height: 250px;
        background: #f1f3f4;
        color: #202124;
        padding: 14px;
        overflow-y: auto;
        font-family: monospace;
        font-size: 13px;
        line-height: 1.6;
        word-break: break-all;
    }
    .line {
        padding: 0 16px;
        white-space: pre;
    }
    .target-line {
        background-color: #4b5263;
        color: #ffffff;
        font-weight: bold;
    }
    .line-num {
        color: #5c6370;
        display: inline-block;
        width: 40px;
        user-select: none;
        margin-right: 12px;
        border-right: 1px solid #3e4451;
        padding-right: 8px;
    }
    .snippet-target-group {
        margin-bottom: 12px;
    }
    .snippet-target-group:last-child {
        margin-bottom: 0;
    }
    .snippet-target-name {
        color: #1a73e8;
        font-weight: 700;
        margin-bottom: 4px;
        border-bottom: 1px solid #dcdcdc;
        padding-bottom: 2px;
    }
    .snippet-file-item {
        padding-left: 12px;
        font-weight: 500;
        color: #3c4043;
    }
</style>
</head>
<body>
<div class="header">
    <h1>Gradle Problems Aggregation Report</h1>
    <div class="stats-grid">
        <div class="stat-card"><div class="stat-label">Target Directory</div><div class="stat-value">${htmlEscape(targetDir.absolutePath)}</div></div>
        <div class="stat-card"><div class="stat-label">Scanned Manifests</div><div class="stat-value">$scannedManifests</div></div>
        <div class="stat-card"><div class="stat-label">Scanned Reports</div><div class="stat-value">$scannedReports</div></div>
        <div class="stat-card"><div class="stat-label">Unique Problem Types</div><div class="stat-value">${sortedIssues.size}</div></div>
        <div class="stat-card"><div class="stat-label">Total Occurrences</div><div class="stat-value">$totalOccurrences</div></div>
        <div class="stat-card"><div class="stat-label">Time Elapsed</div><div class="stat-value">${String.format("%.2f", elapsedMillis / 1000.0)}s</div></div>
    </div>
</div>
<details class="category-section" open>
    <summary class="category-summary"><h2 class="category-title" style="display: inline;">Aggregated Problem Types (${sortedIssues.size})</h2></summary>
    <div class="issue-meta" style="margin-top: 8px;">Total Occurrences: $totalOccurrences | Unique Problem Types: ${sortedIssues.size}</div>
    """.trimIndent())

    sortedIssues.forEachIndexed { idx, bucket ->
        val issueNum = idx + 1
        val uniqueTargets = bucket.rows.values.flatMap { it }.map { it.targetName }.distinct().sorted()
        val totalOccs = bucket.totalOccurrences
        val totalRows = bucket.rows.size

        html.append("""
    <details class="issue-card" ${if (issueNum <= 5) "open" else ""}>
        <summary class="issue-header">
            <h3 class="issue-title">#$issueNum Issue: <code>${htmlEscape(bucket.issueId)}</code></h3>
            <span class="badge">$totalOccs Occurrences</span>
        </summary>
        <div class="issue-meta">Unique Problem Rows: $totalRows | Affected Targets: ${uniqueTargets.size}</div>
        <details class="targets-details">
            <summary>Targets (${uniqueTargets.size})</summary>
            <ul class="targets-list">
        """)
        uniqueTargets.forEach { tName ->
            html.append("                <li>${htmlEscape(tName)}</li>\n")
        }
        html.append("""
            </ul>
        </details>
        <details class="snippets-section-details" open>
            <summary>Details &amp; Occurrences ($totalRows)</summary>
            <div class="row-header">
                <div class="col-snippet-hdr">Code snippet</div>
                <div class="col-message-hdr">Full problem message</div>
                <div class="col-targets-hdr">Targets / Tests</div>
            </div>
        """)

        val sortedRows = bucket.rows.entries.sortedByDescending { it.value.size }
        for ((key, occurrences) in sortedRows) {
            val snippetColHtml = if (key.snippetHtml != null) {
                """<div class="col-snippet"><pre class="snippet-code"><code>${key.snippetHtml}</code></pre></div>"""
            } else {
                """<div class="col-snippet no-code"><div><span style="font-size: 20px; display: block; margin-bottom: 6px;">ℹ️</span>${htmlEscape(key.noSnippetReason)}</div></div>"""
            }

            html.append("""
            <div class="snippet-details">
                $snippetColHtml
                <div class="col-message">${key.fullMessageHtml}</div>
                <div class="col-targets">
            """)

            val byTarget = occurrences.groupBy { it.targetName }
            for ((tName, occs) in byTarget.toSortedMap()) {
                html.append("""
                    <div class="snippet-target-group">
                        <div class="snippet-target-name">${htmlEscape(tName)} (${occs.size})</div>
                """)
                occs.map { it.locationPath }.distinct().sorted().forEach { locPath ->
                    html.append("                        <div class=\"snippet-file-item\">${htmlEscape(locPath)}</div>\n")
                }
                html.append("                    </div>\n")
            }

            html.append("""
                </div>
            </div>
            """)
        }

        html.append("""
        </details>
    </details>
        """)
    }

    html.append("""
</details>
</body>
</html>
    """.trimIndent())

    outputFile.parentFile?.mkdirs()
    outputFile.writeText(html.toString())
    println("Report successfully generated at: ${outputFile.absolutePath}")
}

// --- Main Execution ---

val defaultTargetDir = "/usr/local/google/home/akerim/bazel_out/snapshots_remote_output/2/execroot/_main/bazel-out/k8-opt/testlogs"
val defaultOutputHtml = "tools/adt/idea/report_gradle_problems.html"

val targetDirPath = args.getOrNull(0) ?: defaultTargetDir
val outputHtmlPath = args.getOrNull(1) ?: defaultOutputHtml

val targetDir = File(targetDirPath)
if (!targetDir.exists() || !targetDir.isDirectory) {
    println("Error: Target directory does not exist or is not a directory: $targetDirPath")
    exitProcess(1)
}

println("Scanning target directory: ${targetDir.absolutePath}...")

val issues = mutableMapOf<String, IssueBucket>()
var scannedManifests = 0
var scannedReports = 0

val elapsedMillis = measureTimeMillis {
    targetDir.walkTopDown().filter { it.isFile && it.name == "MANIFEST" }.forEach { manifestFile ->
        scannedManifests++
        val lines = try {
            manifestFile.readLines()
        } catch (_: Exception) {
            emptyList()
        }

        val reportEntries = lines.filter { it.contains("problems-report.html") }.map { it.split("\\s+".toRegex())[0] }
        if (reportEntries.isEmpty()) return@forEach

        val testDir = manifestFile.parentFile?.parentFile ?: return@forEach
        val zipFile = File(testDir, "test.outputs/outputs.zip")

        val targetName = testDir.absolutePath
            .removePrefix(targetDir.absolutePath)
            .removePrefix("/")
            .removeSuffix("/")

        if (zipFile.exists() && zipFile.isFile) {
            try {
                ZipFile(zipFile).use { zip ->
                    val daemonLogLocs = extractDaemonLogLocations(zip)
                    for (entryName in reportEntries) {
                        val entry = zip.getEntry(entryName) ?: continue
                        scannedReports++
                        val content = zip.getInputStream(entry).reader().use { it.readText() }
                        processReportContent(content, targetName, zip, daemonLogLocs, issues)
                    }
                }
            } catch (e: Exception) {
                // Ignore corrupt zip or read errors
            }
        }
    }
}

val outputFile = File(outputHtmlPath)
generateHtmlReport(targetDir, outputFile, issues, scannedManifests, scannedReports, elapsedMillis)
