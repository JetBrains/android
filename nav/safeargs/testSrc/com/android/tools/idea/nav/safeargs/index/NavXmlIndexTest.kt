/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.index

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.indexing.FileContentImpl
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class NavXmlIndexTest {
  private val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val navArgsFlagRule = RestoreFlagRule(StudioFlags.NAV_SAFE_ARGS_SUPPORT)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture: CodeInsightTestFixture by lazy {
    projectRule.fixture
  }

  @Test
  fun indexingSkippedIfFlagNotEnabled() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(false)

    val file = fixture.addFileToProject(
      "navigation/main.xml",
      //language=XML
      """
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:app="http://schemas.android.com/apk/res-auto"
                  xmlns:tools="http://schemas.android.com/tools"
                  android:id="@+id/top_level_nav"
                  app:startDestination="@id/fragment1" />
      """.trimIndent()).virtualFile


    val navXmlIndex = NavXmlIndex()
    assertThat(navXmlIndex.inputFilter.acceptInput(file)).isEqualTo(false)
  }

  @Test
  fun indexNavigationLayout() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)

    val file = fixture.addFileToProject(
      "navigation/main.xml",
      //language=XML
      """
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:app="http://schemas.android.com/apk/res-auto"
                  xmlns:tools="http://schemas.android.com/tools"
                  android:id="@+id/top_level_nav"
                  app:startDestination="@id/fragment1">

          <action android:id="@+id/action_to_fragment2"
                  app:destination="@id/fragment2" />

          <fragment android:id="@+id/fragment1"
                    android:name="test.safeargs.Fragment1"
                    tools:layout="@layout/fragment1">

              <action android:id="@+id/action_fragment1_to_fragment2"
                      app:destination="@id/fragment2" />

              <action android:id="@+id/action_fragment1_to_fragment3"
                      app:destination="@id/fragment3" />
          </fragment>

          <fragment android:id="@+id/fragment2"
                    android:name="test.safeargs.Fragment2"
                    tools:layout="@layout/fragment2">

              <argument android:name="arg"
                        app:argType="string"
                        android:defaultValue="someString"/>

              <action android:id="@+id/action_fragment2_to_fragment3"
                      app:destination="@id/fragment3" />
          </fragment>

          <navigation android:id="@+id/nested_nav"
                      app:startDestination="@id/fragment3">

              <fragment android:id="@+id/fragment3"
                        android:name="test.safeargs.Fragment3"
                        tools:layout="@layout/fragment3">

                  <argument android:name="arg1"
                            app:argType="float" />

                  <argument android:name="arg2"
                            app:argType="integer" />

                  <action android:id="@+id/action_fragment3_to_fragment1"
                          app:destination="@id/fragment1" />

              </fragment>

          </navigation>

      </navigation>
    """.trimIndent()).virtualFile

    val navXmlIndex = NavXmlIndex()
    assertThat(navXmlIndex.inputFilter.acceptInput(file)).isEqualTo(true)

    val map = navXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    assertThat(map).hasSize(1)

    val data = map.values.first()
    assertThat(data.root.id).isEqualTo("top_level_nav")
    assertThat(data.root.toDestination()!!.name).isEqualTo(".TopLevelNav")
    assertThat(data.root.startDestination).isEqualTo("fragment1")
    assertThat(data.root.actions.size).isEqualTo(1)
    data.root.actions[0].let { action ->
      assertThat(action.id).isEqualTo("action_to_fragment2")
      assertThat(action.destination).isEqualTo("fragment2")
    }
    assertThat(data.root.fragments.size).isEqualTo(2)
    data.root.fragments[0].let { destination ->
      assertThat(destination.id).isEqualTo("fragment1")
      assertThat(destination.name).isEqualTo("test.safeargs.Fragment1")
      assertThat(destination.arguments.size).isEqualTo(0)
      assertThat(destination.actions.size).isEqualTo(2)
      destination.actions[0].let { action ->
        assertThat(action.id).isEqualTo("action_fragment1_to_fragment2")
        assertThat(action.destination).isEqualTo("fragment2")
      }
      destination.actions[1].let { action ->
        assertThat(action.id).isEqualTo("action_fragment1_to_fragment3")
        assertThat(action.destination).isEqualTo("fragment3")
      }
    }

    data.root.fragments[1].let { destination ->
      assertThat(destination.id).isEqualTo("fragment2")
      assertThat(destination.name).isEqualTo("test.safeargs.Fragment2")
      assertThat(destination.arguments.size).isEqualTo(1)
      assertThat(destination.actions.size).isEqualTo(1)
      destination.arguments[0].let { argument ->
        assertThat(argument.type).isEqualTo("string")
        assertThat(argument.name).isEqualTo("arg")
        assertThat(argument.defaultValue).isEqualTo("someString")
      }
      destination.actions[0].let { action ->
        assertThat(action.id).isEqualTo("action_fragment2_to_fragment3")
        assertThat(action.destination).isEqualTo("fragment3")
      }
    }

    assertThat(data.root.navigations.size).isEqualTo(1)
    data.root.navigations[0].let { nested ->
      assertThat(nested.id).isEqualTo("nested_nav")
      assertThat(nested.toDestination()!!.name).isEqualTo(".NestedNav")
      assertThat(nested.actions).isEmpty()
      assertThat(nested.startDestination).isEqualTo("fragment3")
      assertThat(nested.fragments.size).isEqualTo(1)
      nested.fragments[0].let { destination ->
        assertThat(destination.id).isEqualTo("fragment3")
        assertThat(destination.name).isEqualTo("test.safeargs.Fragment3")
        assertThat(destination.arguments.size).isEqualTo(2)
        assertThat(destination.actions.size).isEqualTo(1)
        destination.arguments[0].let { argument ->
          assertThat(argument.type).isEqualTo("float")
          assertThat(argument.name).isEqualTo("arg1")
          assertThat(argument.defaultValue).isNull()
        }
        destination.arguments[1].let { argument ->
          assertThat(argument.type).isEqualTo("integer")
          assertThat(argument.name).isEqualTo("arg2")
          assertThat(argument.defaultValue).isNull()
        }
        destination.actions[0].let { action ->
          assertThat(action.id).isEqualTo("action_fragment3_to_fragment1")
          assertThat(action.destination).isEqualTo("fragment1")
        }
      }
    }

    // Verify all three destinations can be found from the root
    assertThat(data.root.allDestinations.map { it.id })
      .containsExactly("fragment1", "fragment2", "fragment3", "top_level_nav", "nested_nav")
  }

  @Test
  fun indexRecoversFromUnrelatedXml() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)

    val file = fixture.addFileToProject(
      "navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              package="com.example.nav.safeargs">
            <application android:label="Safe Args Test" />
        </manifest>
    """.trimIndent()).virtualFile

    val navXmlIndex = NavXmlIndex()
    assertThat(navXmlIndex.inputFilter.acceptInput(file)).isEqualTo(true)

    val map = navXmlIndex.indexer.map(FileContentImpl.createByFile(file))
    assertThat(map).isEmpty()
  }

}
