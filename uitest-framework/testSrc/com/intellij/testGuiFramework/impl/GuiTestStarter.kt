/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.impl

import com.intellij.idea.IdeaApplication
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger

/**
 * [GuiTestStarter] is an extension of the appStarter extension point. When the IDE launches, it finds all of the appStarter extensions and
 * checks to see if the result of invoking getCommandName() on any of them matches the first command-line argument. If so, it is used
 * instead of the default [IdeaApplication.IdeStarter] (see [IdeaApplication.getStarter]).
 *
 * This implementation starts a [GuiTestThread], strips the "guitest" and port arguments, and then delegates to the default
 * [IdeaApplication.IdeStarter] implementation.
 *
 * @author Sergey Karashevich
 */
class GuiTestStarter : IdeaApplication.IdeStarter(), ApplicationStarter {

  companion object {
    val COMMAND_NAME = "guitest"

    val GUI_TEST_PORT = "idea.gui.test.port"
    val GUI_TEST_HOST = "idea.gui.test.host"

    fun isGuiTestThread(): Boolean = Thread.currentThread().name == GuiTestThread.GUI_TEST_THREAD_NAME
  }

  private val LOG = Logger.getInstance(this.javaClass)
  private val PORT_UNDEFINED = "undefined"
  private val HOST_LOCALHOST = "localhost"

  private val guiTestThread = GuiTestThread()

  override fun getCommandName() = COMMAND_NAME

  override fun premain(args: Array<String>) {
    processArgs(args)
    LOG.info("Starting GuiTest activity")
    guiTestThread.start()
    super.premain(args)
  }

  override fun main(args: Array<String>) {
    super.main(removeGuiTestArgs(args))
  }

  private fun processArgs(args: Array<String>) {
    val hostArg: String? = args.find { arg -> arg.toLowerCase().startsWith("host") }?.substringAfter("host=") ?: HOST_LOCALHOST
    System.setProperty(GUI_TEST_HOST, hostArg!!.removeSurrounding("\""))
    val portArg: String? = args.find { arg -> arg.toLowerCase().startsWith("port") }?.substringAfter("port=") ?: PORT_UNDEFINED
    if (portArg != null)
      System.setProperty(GUI_TEST_PORT, portArg.removeSurrounding("\""))
    else
      System.setProperty(GUI_TEST_PORT, PORT_UNDEFINED)

    LOG.info("Set GUI tests host: $hostArg")
    LOG.info("Set GUI tests port: $portArg")
  }

  private fun removeGuiTestArgs(args: Array<String>): Array<out String>? {
    return args.sliceArray(1..args.lastIndex)  //remove guitest keyword
      .filterNot { arg -> arg.startsWith("port") || arg.startsWith("host") }//lets remove host and port from args
      .toTypedArray()
  }

}
