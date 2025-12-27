package com.mediasfu.sdk.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Enum representing the type of virtual background.
 */
enum class BackgroundType {
    /** No virtual background (camera only) */
    NONE,
    /** Blurred background */
    BLUR,
    /** Custom image background */
    IMAGE,
    /** Solid color background */
    COLOR,
    /** Video background (animated) */
    VIDEO;

    companion object {
        fun fromString(str: String): BackgroundType {
            return when (str.lowercase()) {
                "none" -> NONE
                "blur" -> BLUR
                "image" -> IMAGE
                "color" -> COLOR
                "video" -> VIDEO
                else -> NONE
            }
        }
    }

    override fun toString(): String {
        return when (this) {
            NONE -> "none"
            BLUR -> "blur"
            IMAGE -> "image"
            COLOR -> "color"
            VIDEO -> "video"
        }
    }
}

/**
 * Virtual background configuration.
 */
data class VirtualBackground(
    /** Unique identifier for the background */
    val id: String,
    /** Type of background */
    val type: BackgroundType,
    /** Display name */
    val name: String,
    /** Thumbnail for UI display */
    val thumbnailUrl: String? = null,
    /** Full image URL (for image type) */
    val imageUrl: String? = null,
    /** Image bytes (for local images) */
    val imageBytes: ByteArray? = null,
    /** Color value (for color type) */
    val color: Color? = null,
    /** Blur intensity (for blur type, 0.0 to 1.0) */
    val blurIntensity: Float = 0.5f,
    /** Video URL (for video type) */
    val videoUrl: String? = null,
    /** Whether this is a default/preset background */
    val isPreset: Boolean = false,
    /** Whether this background is currently selected */
    val isSelected: Boolean = false
) {
    companion object {
        /**
         * Factory for creating a "None" (disabled) background
         */
        fun none(): VirtualBackground {
            return VirtualBackground(
                id = "none",
                type = BackgroundType.NONE,
                name = "None",
                isPreset = true
            )
        }

        /**
         * Factory for creating a blur background
         */
        fun blur(intensity: Float = 0.5f, name: String = "Blur"): VirtualBackground {
            return VirtualBackground(
                id = "blur_${(intensity * 100).toInt()}",
                type = BackgroundType.BLUR,
                name = name,
                blurIntensity = intensity,
                isPreset = true
            )
        }

        /**
         * Factory for creating a color background
         */
        fun color(color: Color, name: String = "Color"): VirtualBackground {
            return VirtualBackground(
                id = "color_${color.hashCode()}",
                type = BackgroundType.COLOR,
                name = name,
                color = color,
                isPreset = false
            )
        }

        /**
         * Factory for creating an image background
         */
        fun image(
            id: String,
            name: String,
            imageUrl: String? = null,
            imageBytes: ByteArray? = null,
            thumbnailUrl: String? = null,
            isPreset: Boolean = false
        ): VirtualBackground {
            return VirtualBackground(
                id = id,
                type = BackgroundType.IMAGE,
                name = name,
                imageUrl = imageUrl,
                imageBytes = imageBytes,
                thumbnailUrl = thumbnailUrl ?: imageUrl,
                isPreset = isPreset
            )
        }

        fun fromMap(map: Map<String, Any?>): VirtualBackground {
            val colorValue = (map["color"] as? Number)?.toLong()
            return VirtualBackground(
                id = map["id"] as? String ?: "",
                type = BackgroundType.fromString(map["type"] as? String ?: "none"),
                name = map["name"] as? String ?: "",
                thumbnailUrl = map["thumbnailUrl"] as? String,
                imageUrl = map["imageUrl"] as? String,
                color = colorValue?.let { Color(it) },
                blurIntensity = (map["blurIntensity"] as? Number)?.toFloat() ?: 0.5f,
                videoUrl = map["videoUrl"] as? String,
                isPreset = map["isPreset"] as? Boolean ?: false
            )
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "type" to type.toString(),
            "name" to name,
            "thumbnailUrl" to thumbnailUrl,
            "imageUrl" to imageUrl,
            "color" to color?.value,
            "blurIntensity" to blurIntensity,
            "videoUrl" to videoUrl,
            "isPreset" to isPreset
        )
    }

    fun copyWith(
        id: String? = null,
        type: BackgroundType? = null,
        name: String? = null,
        thumbnailUrl: String? = null,
        imageUrl: String? = null,
        imageBytes: ByteArray? = null,
        color: Color? = null,
        blurIntensity: Float? = null,
        videoUrl: String? = null,
        isPreset: Boolean? = null,
        isSelected: Boolean? = null
    ): VirtualBackground {
        return VirtualBackground(
            id = id ?: this.id,
            type = type ?: this.type,
            name = name ?: this.name,
            thumbnailUrl = thumbnailUrl ?: this.thumbnailUrl,
            imageUrl = imageUrl ?: this.imageUrl,
            imageBytes = imageBytes ?: this.imageBytes,
            color = color ?: this.color,
            blurIntensity = blurIntensity ?: this.blurIntensity,
            videoUrl = videoUrl ?: this.videoUrl,
            isPreset = isPreset ?: this.isPreset,
            isSelected = isSelected ?: this.isSelected
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as VirtualBackground

        if (id != other.id) return false
        if (type != other.type) return false
        if (name != other.name) return false
        if (thumbnailUrl != other.thumbnailUrl) return false
        if (imageUrl != other.imageUrl) return false
        if (imageBytes != null) {
            if (other.imageBytes == null) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
        } else if (other.imageBytes != null) return false
        if (color != other.color) return false
        if (blurIntensity != other.blurIntensity) return false
        if (videoUrl != other.videoUrl) return false
        if (isPreset != other.isPreset) return false
        if (isSelected != other.isSelected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (thumbnailUrl?.hashCode() ?: 0)
        result = 31 * result + (imageUrl?.hashCode() ?: 0)
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (color?.hashCode() ?: 0)
        result = 31 * result + blurIntensity.hashCode()
        result = 31 * result + (videoUrl?.hashCode() ?: 0)
        result = 31 * result + isPreset.hashCode()
        result = 31 * result + isSelected.hashCode()
        return result
    }
}

/**
 * Default preset backgrounds available in the SDK.
 */
object PresetBackgrounds {
    /** Base URL for MediaSFU background images */
    private const val BASE_URL = "https://mediasfu.com/images/backgrounds"

    /** Default image names available from MediaSFU */
    private val DEFAULT_IMAGE_NAMES = listOf(
        "wall", "wall2", "shelf", "clock", "desert", "flower"
    )

    /** Get thumbnail URL for an image */
    fun getThumbnailUrl(imageName: String): String = "$BASE_URL/${imageName}_thumbnail.jpg"

    /** Get small resolution URL for an image */
    fun getSmallUrl(imageName: String): String = "$BASE_URL/${imageName}_small.jpg"

    /** Get large resolution URL for an image */
    fun getLargeUrl(imageName: String): String = "$BASE_URL/${imageName}_large.jpg"

    /** Get full resolution URL for an image */
    fun getFullUrl(imageName: String): String = "$BASE_URL/$imageName.jpg"

    /** Get all default preset backgrounds */
    val all: List<VirtualBackground>
        get() = listOf(VirtualBackground.none()) + blurs + colors + images

    /** Get blur presets only */
    val blurs: List<VirtualBackground>
        get() = listOf(
            VirtualBackground.blur(intensity = 0.3f, name = "Light Blur"),
            VirtualBackground.blur(intensity = 0.6f, name = "Medium Blur"),
            VirtualBackground.blur(intensity = 0.9f, name = "Strong Blur")
        )

    /** Get color presets only */
    val colors: List<VirtualBackground>
        get() = listOf(
            VirtualBackground.color(Color(0xFF1976D2), name = "Blue"),      // Blue 700
            VirtualBackground.color(Color(0xFF388E3C), name = "Green"),     // Green 700
            VirtualBackground.color(Color(0xFF7B1FA2), name = "Purple"),    // Purple 700
            VirtualBackground.color(Color(0xFF424242), name = "Dark Gray"), // Grey 800
            VirtualBackground.color(Color.White, name = "White")
        )

    /** Get default image presets from MediaSFU */
    val images: List<VirtualBackground>
        get() = DEFAULT_IMAGE_NAMES.map { name ->
            VirtualBackground.image(
                id = "preset_$name",
                name = formatName(name),
                thumbnailUrl = getThumbnailUrl(name),
                imageUrl = getFullUrl(name),
                isPreset = true
            )
        }

    /**
     * Get an image preset with appropriate resolution based on target
     */
    fun getImageForResolution(imageName: String, targetResolution: String): VirtualBackground {
        val imageUrl = if (targetResolution == "fhd" || targetResolution == "qhd") {
            getLargeUrl(imageName)
        } else {
            getFullUrl(imageName)
        }

        return VirtualBackground.image(
            id = "preset_$imageName",
            name = formatName(imageName),
            thumbnailUrl = getThumbnailUrl(imageName),
            imageUrl = imageUrl,
            isPreset = true
        )
    }

    /** Format image name for display */
    private fun formatName(name: String): String {
        if (name == "wall2") return "Wall 2"
        return name.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Result of background segmentation processing.
 */
data class SegmentationResult(
    /** The processed frame with background replaced */
    val processedFrame: ByteArray? = null,
    /** The segmentation mask (grayscale: 0=background, 255=person) */
    val mask: ByteArray? = null,
    /** Mask width */
    val maskWidth: Int? = null,
    /** Mask height */
    val maskHeight: Int? = null,
    /** Per-pixel confidence (0.0-1.0) from ML Kit, if available */
    val confidence: Float? = null,
    /** Processing time in milliseconds */
    val processingTimeMs: Int = 0,
    /** Whether processing was successful */
    val success: Boolean = true,
    /** Error message if processing failed */
    val error: String? = null
) {
    companion object {
        fun error(message: String): SegmentationResult {
            return SegmentationResult(
                success = false,
                error = message
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SegmentationResult

        if (processedFrame != null) {
            if (other.processedFrame == null) return false
            if (!processedFrame.contentEquals(other.processedFrame)) return false
        } else if (other.processedFrame != null) return false
        if (mask != null) {
            if (other.mask == null) return false
            if (!mask.contentEquals(other.mask)) return false
        } else if (other.mask != null) return false
        if (maskWidth != other.maskWidth) return false
        if (maskHeight != other.maskHeight) return false
        if (confidence != other.confidence) return false
        if (processingTimeMs != other.processingTimeMs) return false
        if (success != other.success) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = processedFrame?.contentHashCode() ?: 0
        result = 31 * result + (mask?.contentHashCode() ?: 0)
        result = 31 * result + (maskWidth ?: 0)
        result = 31 * result + (maskHeight ?: 0)
        result = 31 * result + (confidence?.hashCode() ?: 0)
        result = 31 * result + processingTimeMs
        result = 31 * result + success.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}
