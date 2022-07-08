// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.idea.IdeStarter
import com.intellij.openapi.diagnostic.Logger

/**
 * [GuiTestStarter] is an extension of the appStarter extension point. When the IDE launches, it finds all of the appStarter extensions and
 * checks to see if the result of invoking getCommandName() on any of them matches the first command-line argument. If so, it is used
 * instead of the default [IdeaApplication.IdeStarter] (see [IdeaApplication.getStarter]).
 *
 * This implementation starts a [GuiTestThread], strips the "guitest" and port arguments, and then delegates to the default
 * [IdeaApplication.IdeStarter] implementation.
 */
class GuiTestStarter : IdeStarter() {
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

  override val commandName = COMMAND_NAME

  override fun premain(args: List<String>) {
    processArgs(args)
    LOG.info("Starting GuiTest activity")
    guiTestThread.start()
  }

  override fun main(args: List<String>) {
    super.main(removeGuiTestArgs(args))
  }

  private fun processArgs(args: List<String>) {
    val hostArg: String? = args.find { arg -> arg.toLowerCase().startsWith("host") }?.substringAfter("host=") ?: HOST_LOCALHOST
    System.setProperty(GUI_TEST_HOST, hostArg!!.removeSurrounding("\""))
    val portArg: String? = args.find { arg -> arg.toLowerCase().startsWith("port") }?.substringAfter("port=")
    if (portArg != null)
      System.setProperty(GUI_TEST_PORT, portArg.removeSurrounding("\""))

    LOG.info("Set GUI tests host: $hostArg")
    LOG.info("Set GUI tests port: $portArg")
  }


  private fun removeGuiTestArgs(args: List<String>): List<String> {
    return args.drop(1)  //remove guitest keyword
      .filterNot { arg -> arg.startsWith("port") || arg.startsWith("host") }//lets remove host and port from args
  }
}
