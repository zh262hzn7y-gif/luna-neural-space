package space.neural.luna

import android.content.Context
import android.content.Intent
import android.net.Uri

object IntentRouter {
    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openUber(context: Context) {
        openUrl(context, "https://m.uber.com")
    }

    fun open99(context: Context) {
        openUrl(context, "https://99app.com")
    }

    fun openInDrive(context: Context) {
        openUrl(context, "https://indrive.com")
    }

    fun dial(context: Context, number: String) {
        openUrl(context, "tel:$number")
    }

    fun openMaps(context: Context, query: String) {
        openUrl(context, "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
    }
}
