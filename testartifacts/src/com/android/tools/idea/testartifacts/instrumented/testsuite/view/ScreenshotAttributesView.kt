/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import com.android.annotations.concurrency.UiThread
import com.google.common.annotations.VisibleForTesting
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.util.ImageMetadata
import com.android.tools.idea.testartifacts.instrumented.testsuite.util.NOT_APPLICABLE
import com.android.tools.idea.testartifacts.instrumented.testsuite.util.ScreenshotTestUtils
import java.awt.Desktop
import java.io.File
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import javax.swing.JComponent
import com.intellij.util.ui.StartupUiUtil

private val LOG = Logger.getInstance(ScreenshotAttributesView::class.java)

private val LocalIsDarkTheme = compositionLocalOf<Boolean> { error("No isDarkTheme provided") }

/**
 * A view that displays the attributes of a screenshot.
 */
class ScreenshotAttributesView {
    /**
     * Represents the state of the screenshot attributes view.
     *
     * @param testResult The result of the test case.
     * @param methodName The name of the test method.
     * @param className The name of the test class.
     * @param refLocation The location of the reference screenshot.
     * @param newLocation The location of the new screenshot.
     * @param matchPercentage The match percentage between the two screenshots.
     */
    @VisibleForTesting
    data class ScreenshotAttributesState(
        val testResult: AndroidTestCaseResult? = null,
        val methodName: String = NOT_APPLICABLE,
        val className: String = NOT_APPLICABLE,
        val refLocation: String = NOT_APPLICABLE,
        val newLocation: String = NOT_APPLICABLE,
        val matchPercentage: String? = null
    )

    @get:VisibleForTesting
    var state by mutableStateOf(ScreenshotAttributesState())
        private set

    /**
     * Returns the Swing component for this view.
     */
    @UiThread
    fun getComponent(): JComponent {
        return ComposePanel().apply {
            setContent {
                var isDarkTheme by remember { mutableStateOf(StartupUiUtil.isDarkTheme) }

                val messageBus = ApplicationManager.getApplication().messageBus
                DisposableEffect(messageBus) {
                    val connection = messageBus.connect()
                    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
                        isDarkTheme = StartupUiUtil.isDarkTheme
                    })
                    onDispose {
                        connection.disconnect()
                    }
                }

                CompositionLocalProvider(LocalIsDarkTheme provides isDarkTheme) {
                    ScreenshotAttributesUi(state)
                }
            }
        }
    }

    /**
     * Updates the data in the view.
     *
     * @param refImagePath The path to the reference screenshot.
     * @param newImagePath The path to the new screenshot.
     * @param testMethodName The name of the test method.
     * @param testClassName The name of the test class.
     * @param result The result of the test case.
     * @param errorStackTrace The error stack trace, if any.
     */
    @UiThread
    fun updateData(
        refImagePath: String?,
        newImagePath: String?,
        testMethodName: String?,
        testClassName: String?,
        result: AndroidTestCaseResult?,
        errorStackTrace: String?,
    ) {
        val matchPercentage = ScreenshotTestUtils.parseMatchPercentage(errorStackTrace)

        state = ScreenshotAttributesState(
            testResult = result,
            methodName = testMethodName ?: NOT_APPLICABLE,
            className = testClassName ?: NOT_APPLICABLE,
            refLocation = refImagePath ?: NOT_APPLICABLE,
            newLocation = newImagePath ?: NOT_APPLICABLE,
            matchPercentage = matchPercentage
        )
    }

    /**
     * The main UI for the screenshot attributes view.
     *
     * @param currentState The current state of the view.
     */
    @Composable
    private fun ScreenshotAttributesUi(currentState: ScreenshotAttributesState) {
        var refMetadata by remember { mutableStateOf(ImageMetadata()) }
        var newMetadata by remember { mutableStateOf(ImageMetadata()) }

        LaunchedEffect(currentState.refLocation) {
            refMetadata = ScreenshotTestUtils.loadImageMetadata(currentState.refLocation.takeIf { it != NOT_APPLICABLE })
        }
        LaunchedEffect(currentState.newLocation) {
            newMetadata = ScreenshotTestUtils.loadImageMetadata(currentState.newLocation.takeIf { it != NOT_APPLICABLE })
        }

        val isDarkTheme = LocalIsDarkTheme.current
        val backgroundColor = if (isDarkTheme) Color(0xFF1e2021) else Color(0xFFf7f8fa)
        Column(
            modifier = Modifier.fillMaxSize().background(backgroundColor).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Summary") {
                KeyValueRow("Match") {
                    val text = currentState.matchPercentage ?: when(currentState.testResult) {
                        AndroidTestCaseResult.FAILED -> "0.00%"
                        else -> currentState.testResult?.name ?: NOT_APPLICABLE
                    }
                    when (currentState.testResult) {
                        AndroidTestCaseResult.PASSED -> GreenText(text)
                        AndroidTestCaseResult.FAILED -> RedText(text)
                        else -> GrayText(text)
                    }
                }
                KeyValueRow("Preview") { BlueText(currentState.methodName) }
                KeyValueRow("Related Composables") { BlueText(currentState.className) }
            }

            Section("Preview configuration") { CodeSnippet("@Preview(${currentState.methodName})") }

            Section("File info") {
                FileInfoTable(
                    refMetadata.dimensions, newMetadata.dimensions,
                    refMetadata.size, newMetadata.size,
                    refMetadata.date, newMetadata.date,
                    currentState.refLocation, currentState.newLocation
                )
            }
        }
    }
}

/**
 * A section of the UI.
 *
 * @param title The title of the section.
 * @param content The content of the section.
 */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoldLightText(title)
        Column(modifier = Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

/**
 * A key-value row in the UI.
 *
 * @param key The key.
 * @param value The value.
 */
@Composable
private fun KeyValueRow(key: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GrayText(key, modifier = Modifier.width(160.dp))
        value()
    }
}

/**
 * A table of file info.
 *
 * @param refDimensions The dimensions of the reference screenshot.
 * @param newDimensions The dimensions of the new screenshot.
 * @param refSize The size of the reference screenshot.
 * @param newSize The size of the new screenshot.
 * @param refDate The date of the reference screenshot.
 * @param newDate The date of the new screenshot.
 * @param refLocation The location of the reference screenshot.
 * @param newLocation The location of the new screenshot.
 */
@Composable
private fun FileInfoTable(
    refDimensions: String, newDimensions: String,
    refSize: String, newSize: String,
    refDate: String, newDate: String,
    refLocation: String, newLocation: String
) {
    BoxWithConstraints {
        val cellWidth = (maxWidth - 160.dp) / 2
        FileInfoTableContent(
            refDimensions, newDimensions,
            refSize, newSize,
            refDate, newDate,
            refLocation, newLocation,
            cellWidth
        )
    }
}

@Composable
private fun FileInfoTableContent(
    refDimensions: String, newDimensions: String,
    refSize: String, newSize: String,
    refDate: String, newDate: String,
    refLocation: String, newLocation: String,
    cellWidth: androidx.compose.ui.unit.Dp
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val headerColor = if (isDarkTheme) Color(0xFF888888) else Color.Gray
    val headerStyle = TextStyle(color = headerColor, fontWeight = FontWeight.Bold)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(160.dp))
            BasicText("Reference", style = headerStyle, modifier = Modifier.width(cellWidth))
            BasicText("New", style = headerStyle, modifier = Modifier.width(cellWidth))
        }
        Divider()

        FileInfoRow("Dimensions", refDimensions, newDimensions, cellWidth)
        FileInfoRow("Size", refSize, newSize, cellWidth)
        FileInfoRow("Screenshot date", refDate, newDate, cellWidth)
        FileInfoRow("Location", refLocation, newLocation, cellWidth)
    }
}

/**
 * A row in the file info table.
 *
 * @param attribute The attribute.
 * @param refValue The value for the reference screenshot.
 * @param newValue The value for the new screenshot.
 * @param cellWidth The width of the cells.
 */
@Composable
private fun FileInfoRow(attribute: String, refValue: String, newValue: String, cellWidth: androidx.compose.ui.unit.Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GrayText(attribute, modifier = Modifier.width(160.dp))
        if (attribute == "Location") {
            ClickableFileLink(refValue, modifier = Modifier.width(cellWidth))
            ClickableFileLink(newValue, modifier = Modifier.width(cellWidth))
        } else {
            LightText(refValue, modifier = Modifier.width(cellWidth))
            LightText(newValue, modifier = Modifier.width(cellWidth))
        }
    }
}

/**
 * A code snippet.
 *
 * @param text The text of the code snippet.
 * @param modifier The modifier.
 */
@Composable
private fun CodeSnippet(text: String, modifier: Modifier = Modifier) {
    val isDark = LocalIsDarkTheme.current
    val backgroundColor = if (isDark) Color(0xFF3C3F41) else Color(0xFFdfe1e3)
    val textColor = if (isDark) Color(0xFFA9B7C6) else Color.Gray
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = backgroundColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = textColor, fontFamily = FontFamily.Monospace)
        )
    }
}

/**
 * A divider.
 *
 * @param modifier The modifier.
 */
@Composable
private fun Divider(modifier: Modifier = Modifier) {
    val isDarkTheme = LocalIsDarkTheme.current
    val backgroundColor = if (isDarkTheme) Color(0xFF3C3F41) else Color.LightGray
    Box(
        modifier
            .height(1.dp)
            .fillMaxWidth()
            .background(color = backgroundColor)
    )
}

/**
 * A clickable file link.
 *
 * @param path The path to the file.
 * @param modifier The modifier.
 */
@Composable
private fun ClickableFileLink(path: String, modifier: Modifier = Modifier) {
    if (path == NOT_APPLICABLE) {
        LightText(text = path, modifier = modifier)
    } else {
        BlueText(
            text = path,
            modifier = modifier.clickable(enabled = File(path).exists()) {
                try {
                    Desktop.getDesktop().open(File(path))
                } catch (e: Exception) {
                    LOG.warn("Failed to open file: $path", e)
                }
            }
        )
    }
}

/**
 * Blue text.
 *
 * @param text The text.
 * @param modifier The modifier.
 */
@Composable
private fun BlueText(text: String, modifier: Modifier = Modifier) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color(0xFF589DF6) else Color(0xFF1854D9)
    BasicText(text = text, style = TextStyle(color = textColor), modifier = modifier)
}

/**
 * Gray text.
 *
 * @param text The text.
 * @param modifier The modifier.
 */
@Composable
private fun GrayText(text: String, modifier: Modifier = Modifier) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color(0xFF888888) else Color.Gray
    BasicText(text = text, style = TextStyle(color = textColor), modifier = modifier)
}

/**
 * Bold, light text.
 *
 * @param text The text.
 * @param modifier The modifier.
 */
@Composable
private fun BoldLightText(text: String, modifier: Modifier = Modifier) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color(0xFFBBBBBB) else Color.Black
    BasicText(text = text, style = TextStyle(color = textColor, fontWeight = FontWeight.Bold), modifier = modifier)
}

/**
 * Light text.
 *
 * @param text The text.
 * @param modifier The modifier.
 */
@Composable
private fun LightText(text: String, modifier: Modifier = Modifier) {
    val isDarkTheme = LocalIsDarkTheme.current
    val textColor = if (isDarkTheme) Color(0xFFBBBBBB) else Color.Black
    BasicText(text = text, style = TextStyle(color = textColor), modifier = modifier)
}

/**
 * Red text.
 *
 * @param text The text.
 * @param modifier The modifier.
 */
@Composable
private fun RedText(text: String, modifier: Modifier = Modifier) {
    BasicText(text = text, style = TextStyle(color = Color(0xFFFF5261)), modifier = modifier)
}

/**
 * Green text.
 *
 * @param text The text.
 * @param modifier The modifier.
 */
@Composable
private fun GreenText(text: String, modifier: Modifier = Modifier) {
    BasicText(text = text, style = TextStyle(color = Color(0xFF499C54)), modifier = modifier)
}
