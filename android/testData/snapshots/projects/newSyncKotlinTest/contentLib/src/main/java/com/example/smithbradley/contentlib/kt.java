/*
 * This file used to be located under "contentLib/src/main/kotlin/asdasd". Unfortunately, due to the following issue
 * https://youtrack.jetbrains.com/issue/IDEA-297059 introduced recently on the platform, is causing flakiness on test
 * given this commit: https://github.com/JetBrains/intellij-community/commit/dff59a2d1944e020dcb4e4d2c176c9b9a3b79721
 *
 * The flakiness is strictly linked by the fact of picking the first source directory based on HashSet with the addition
 * that by removing the kotlin source this will ignore the kotlin directory given that this one doesn't exist,
 * discarding in this way relay on the ordering issue on HashSet (the root of the issue).
 */

package com.example.smithbradley.contentlib

public class kt {
}
