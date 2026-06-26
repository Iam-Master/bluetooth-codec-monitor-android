package com.iammaster.codecmonitor.data.photos

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps ML Kit's on-device Subject Segmentation so fetched product photos can be
 * turned into transparent-background cutouts instead of showing their original
 * studio-photo backdrop as a visible box in the UI.
 */
class BackgroundRemover {

    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
        )
    }

    suspend fun removeBackground(bitmap: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            segmenter.process(image)
                .addOnSuccessListener { result -> cont.resume(result.foregroundBitmap) }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: Exception) {
            cont.resume(null)
        }
    }

    /** Releases the underlying ML Kit native segmenter. Call when the owning component is destroyed. */
    fun close() {
        segmenter.close()
    }
}
