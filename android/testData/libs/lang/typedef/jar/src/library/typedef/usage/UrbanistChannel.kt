package library.typedef.usage
import androidx.annotation.StringDef
const val STRONG_TOWNS = "Chuck Marohn"
object ClimateTownHolder {
  const val CLIMATE_TOWN = "Rollie Williams"
}
@StringDef(STRONG_TOWNS, ClimateTownHolder.CLIMATE_TOWN, UrbanistChannel.NOT_JUST_BIKES)
annotation class UrbanistChannel {
  companion object {
    const val NOT_JUST_BIKES = "Jason Slaughter"
  }
}
