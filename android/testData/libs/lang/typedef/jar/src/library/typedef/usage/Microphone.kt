package library.typedef.usage
import androidx.annotation.IntDef
const val SHURE = 0
object BlueHolder {
  const val BLUE = 1
}
@IntDef(value = [SHURE, BlueHolder.BLUE, Microphone.RØDE])
annotation class Microphone {
  companion object {
    const val RØDE = 2
  }
}
