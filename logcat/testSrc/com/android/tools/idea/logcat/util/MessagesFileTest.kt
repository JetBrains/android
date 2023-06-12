package com.android.tools.idea.logcat.util

import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [MessagesFile]
 */
internal class MessagesFileTest {
  private val tempFileFactory = TestTempFileFactory()

  @get:Rule
  val rule = RuleChain(ApplicationRule(), ApplicationServiceRule(TempFileFactory::class.java, tempFileFactory))

  @Test
  fun initialize_createsFile() {
    val messagesFile = messagesFile()

    messagesFile.initialize()

    assertThat(tempFileFactory.getExistingFileNames()).containsExactly("studio-test-1.bin")
  }

  @Test
  fun delete_deletesFile() {
    val messagesFile = messagesFile()
    messagesFile.initialize()

    messagesFile.delete()

    assertThat(tempFileFactory.getExistingFileNames()).isEmpty()
  }

  @Test
  fun loadMessagesAndDelete_emptyFile() {
    val messagesFile = messagesFile()
    messagesFile.initialize()

    assertThat(messagesFile.loadMessagesAndDelete()).isEmpty()
    assertThat(tempFileFactory.getExistingFileNames()).isEmpty()
  }

  @Test
  fun appendMessages_canRead() {
    val messagesFile = messagesFile()
    messagesFile.initialize()

    messagesFile.appendMessages(listOf(
      logcatMessage(message = "Foo"),
      logcatMessage(message = "Bar"),
    ))

    assertThat(messagesFile.loadMessagesAndDelete()).containsExactly(
      logcatMessage(message = "Foo"),
      logcatMessage(message = "Bar"),
    )
    assertThat(tempFileFactory.getExistingFileNames()).isEmpty()
  }

  @Test
  fun appendMessages_exceedsMaxSize() {
    val messagesFile = messagesFile(10)
    messagesFile.initialize()

    messagesFile.appendMessages(listOf(
      logcatMessage(message = "Foo-12345"), // len 9
      logcatMessage(message = "Bar-12345"), // len 18 - file1 will contain 2 messages
    ))

    messagesFile.appendMessages(listOf(
      logcatMessage(message = "More-12345"), // This message will go into file2
    ))

    assertThat(tempFileFactory.getExistingFileNames()).containsExactly(
      "studio-test-1.bin",
      "studio-test-2.bin",
    )
    assertThat(messagesFile.loadMessagesAndDelete()).containsExactly(
      logcatMessage(message = "Foo-12345"),
      logcatMessage(message = "Bar-12345"),
      logcatMessage(message = "More-12345"),
    )
    assertThat(tempFileFactory.getExistingFileNames()).isEmpty()
  }

  @Test
  fun appendMessages_exceedsMaxSizeByMore() {
    val messagesFile = messagesFile(10)
    messagesFile.initialize()

    messagesFile.appendMessages(listOf(
      logcatMessage(message = "Foo-12345"), // len 9
      logcatMessage(message = "Bar-12345"), // len 18 - file1 will contain 2 messages
    ))
    messagesFile.appendMessages(listOf(
      logcatMessage(message = "More-1234"), // len 9
      logcatMessage(message = "More-5678"), // len 18 - file2 will contain 2 messages
    ))
    messagesFile.appendMessages(listOf(
      logcatMessage(message = "Even more"), // This message will go into file3
    ))

    assertThat(tempFileFactory.getExistingFileNames()).containsExactly(
      "studio-test-2.bin",
      "studio-test-3.bin",
    )
    assertThat(messagesFile.loadMessagesAndDelete()).containsExactly(
      logcatMessage(message = "More-1234"),
      logcatMessage(message = "More-5678"),
      logcatMessage(message = "Even more"),
    )
    assertThat(tempFileFactory.getExistingFileNames()).isEmpty()
  }

  private fun messagesFile(maxSize: Int = Int.MAX_VALUE) =
    MessagesFile("test", maxSize)
}
