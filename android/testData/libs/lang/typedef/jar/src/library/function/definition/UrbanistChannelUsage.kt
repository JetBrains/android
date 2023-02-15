package library.function.definition
import library.typedef.usage.UrbanistChannel
class UrbanistChannelUsage(p1: String, p2: Int, p3: Long, @UrbanistChannel param: String) {
  companion object {
    @JvmStatic
    fun useUrbanistChannel(p1: String, p2: Int, p3: Long, @UrbanistChannel param: String) {}
  }
}
