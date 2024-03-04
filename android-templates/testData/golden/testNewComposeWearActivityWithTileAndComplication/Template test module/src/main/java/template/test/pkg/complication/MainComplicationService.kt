package template.test.pkg.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.util.Calendar

/**
 * Skeleton for complication data source that returns short text.
 */
class MainComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData("Mon", "Monday")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> createComplicationData("Sun", "Sunday")
            Calendar.MONDAY -> createComplicationData("Mon", "Monday")
            Calendar.TUESDAY -> createComplicationData("Tue", "Tuesday")
            Calendar.WEDNESDAY -> createComplicationData("Wed", "Wednesday")
            Calendar.THURSDAY -> createComplicationData("Thu", "Thursday")
            Calendar.FRIDAY -> createComplicationData("Fri!", "Friday!")
            Calendar.SATURDAY -> createComplicationData("Sat", "Saturday")
            else -> throw IllegalArgumentException("too many days")
        }
    }

    private fun createComplicationData(text: String, contentDescription: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build()
        ).build()
}