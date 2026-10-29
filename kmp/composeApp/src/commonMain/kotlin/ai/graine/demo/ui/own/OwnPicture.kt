package ai.graine.demo.ui.own

import ai.graine.demo.domain.OwnComponents
import ai.graine.demo.platform.Platform
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * A PICTURE IN A COMPONENT — an image block of a component built from blocks: as wide as the card, [heightDp] tall
 * (the look's picture size), cropped to fit. Fetched over https only, once per address while the app runs
 * ([Platform.picture]: off the main thread, no image library); a neutral box while it loads, and when it cannot.
 */
@Composable
internal fun OwnPicture(url: String?, heightDp: Int, radiusDp: Int, modifier: Modifier = Modifier) {
    val address = OwnComponents.pictureUrl(url)
    val image by produceState(address?.let { OwnPictures.kept(it) }, address) {
        if (value == null && address != null) value = OwnPictures.load(address)
    }
    val frame = modifier.fillMaxWidth().height(heightDp.dp).clip(RoundedCornerShape(radiusDp.dp)).testTag("own_block_image")
    val shown = image
    if (shown != null) {
        Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = frame)
    } else {
        Box(frame.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Text("Picture", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The pictures fetched while the app runs, by address: the newest few, in memory. Read and written on the main thread. */
private object OwnPictures {
    private const val KEEP = 24
    private val cache = LinkedHashMap<String, ImageBitmap>()

    fun kept(url: String): ImageBitmap? = cache[url]

    suspend fun load(url: String): ImageBitmap? = cache[url] ?: Platform.picture(url)?.also { image ->
        cache[url] = image
        while (cache.size > KEEP) cache.remove(cache.keys.first())
    }
}
