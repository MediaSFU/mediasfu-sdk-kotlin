package com.mediasfu.sdk.ui.components.background

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.layout.ContentScale
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/**
 * iOS implementation of ProcessedFrameView.
 * Renders preview frames from common iOS/native image payloads.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun ProcessedFrameView(
    bitmap: Any?,
    modifier: Modifier
) {
    when (bitmap) {
        is ImageBitmap -> Image(
            bitmap = bitmap,
            contentDescription = "Processed video preview",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )

        else -> {
            val image = bitmap.toUIImage() ?: return
            key(bitmap) {
                UIKitView(
                    factory = {
                        UIImageView().apply {
                            contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                            clipsToBounds = true
                            setImage(image)
                        }
                    },
                    modifier = modifier,
                    update = { imageView ->
                        imageView.setImage(image)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun Any?.toUIImage(): UIImage? = when (this) {
    is UIImage -> this
    is NSData -> UIImage(data = this)
    is ByteArray -> UIImage(data = toNSData())
    else -> null
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
}
