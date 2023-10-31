package library.function.definition
import library.typedef.usage.Microphone
class MicrophoneUsage(p1: String, p2: Int, p3: Long, @Microphone param: Int) {
  companion object {
    @JvmStatic
    fun useMicrophone(p1: String, p2: Int, p3: Long, @Microphone param: Int) {}
  }
}
