package ir.cheshmgoya.app.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import ir.cheshmgoya.core.gaze.EyeGeometry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Per-frame eye signals. [face] is false when no face was found; the other
 * values are then meaningless.
 */
data class FaceFrame(
    val timeMs: Long,
    val face: Boolean,
    val blinkLeft: Float = 0f,
    val blinkRight: Float = 0f,
    val lookOutLeft: Float = 0f,
    val lookInLeft: Float = 0f,
    val lookInRight: Float = 0f,
    val lookOutRight: Float = 0f,
    /** Iris, eye-corner and nose points for direct gaze pointing. */
    val eyes: EyeGeometry? = null,
)

/**
 * Front camera → MediaPipe Face Landmarker (blendshapes) running fully on the
 * device. Frames are analysed in memory and dropped: nothing is ever saved or
 * sent anywhere. The model file ships in assets, so it works offline.
 */
class EyeTracker(private val context: Context) {

    private val _frames = MutableSharedFlow<FaceFrame>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val frames: SharedFlow<FaceFrame> = _frames

    private val _qrCodes = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val qrCodes: SharedFlow<String> = _qrCodes

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** When true, frames are decoded as QR codes (for pairing) instead of tracked. */
    @Volatile var qrMode = false

    private var landmarker: FaceLandmarker? = null
    private var executor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var previewSurface: Preview.SurfaceProvider? = null
    private var lastTs = 0L
    private val qr = QrDecoder()

    private fun ensureLandmarker(): FaceLandmarker? {
        landmarker?.let { return it }
        return try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result: FaceLandmarkerResult, image: MPImage -> onResult(result, image.width, image.height) }
                .setErrorListener { e -> Log.w(TAG, "landmarker error", e) }
                .build()
            FaceLandmarker.createFromOptions(context, options).also { landmarker = it }
        } catch (e: Exception) {
            Log.e(TAG, "cannot create face landmarker", e)
            _error.value = "مدل تشخیص چهره بارگذاری نشد."
            null
        }
    }

    private fun onResult(result: FaceLandmarkerResult, width: Int, height: Int) {
        val shapes = result.faceBlendshapes().orElse(null)?.firstOrNull()
        if (shapes == null) {
            _frames.tryEmit(FaceFrame(result.timestampMs(), face = false))
            return
        }
        val m = HashMap<String, Float>(shapes.size)
        for (c in shapes) m[c.categoryName()] = c.score()
        val eyes = result.faceLandmarks().firstOrNull()?.takeIf { it.size >= 478 }?.let { lms ->
            val xs = FloatArray(lms.size) { lms[it].x() }
            val ys = FloatArray(lms.size) { lms[it].y() }
            EyeGeometry.fromLandmarks(xs, ys, width, height)
        }
        _frames.tryEmit(
            FaceFrame(
                timeMs = result.timestampMs(),
                face = true,
                blinkLeft = m["eyeBlinkLeft"] ?: 0f,
                blinkRight = m["eyeBlinkRight"] ?: 0f,
                lookOutLeft = m["eyeLookOutLeft"] ?: 0f,
                lookInLeft = m["eyeLookInLeft"] ?: 0f,
                lookInRight = m["eyeLookInRight"] ?: 0f,
                lookOutRight = m["eyeLookOutRight"] ?: 0f,
                eyes = eyes,
            )
        )
    }

    fun start(lifecycleOwner: LifecycleOwner) {
        owner = lifecycleOwner
        if (executor == null) executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get()
            bind()
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        provider?.unbindAll()
    }

    /** Show the live camera in the debug screen (display only, never recorded). */
    fun setPreview(surfaceProvider: Preview.SurfaceProvider?) {
        previewSurface = surfaceProvider
        if (provider != null) bind()
    }

    private fun bind() {
        val p = provider ?: return
        val o = owner ?: return
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(executor!!) { proxy -> analyze(proxy) }
        try {
            p.unbindAll()
            val preview = previewSurface?.let { sp -> Preview.Builder().build().also { it.setSurfaceProvider(sp) } }
            if (preview != null) p.bindToLifecycle(o, CameraSelector.DEFAULT_FRONT_CAMERA, analysis, preview)
            else p.bindToLifecycle(o, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            _error.value = null
        } catch (e: Exception) {
            Log.e(TAG, "camera bind failed", e)
            _error.value = "دوربین جلو در دسترس نیست."
        }
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val upright = rotate(proxy.toBitmap(), proxy.imageInfo.rotationDegrees)
            if (qrMode) {
                qr.decode(upright)?.let { _qrCodes.tryEmit(it) }
                return
            }
            val lm = ensureLandmarker() ?: return
            var ts = SystemClock.uptimeMillis()
            if (ts <= lastTs) ts = lastTs + 1 // MediaPipe needs strictly increasing timestamps
            lastTs = ts
            lm.detectAsync(BitmapImageBuilder(upright).build(), ts)
        } catch (e: Exception) {
            Log.w(TAG, "frame dropped", e)
        } finally {
            proxy.close()
        }
    }

    private fun rotate(b: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return b
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    fun close() {
        provider?.unbindAll()
        landmarker?.close()
        landmarker = null
        executor?.shutdown()
        executor = null
    }

    companion object {
        private const val TAG = "EyeTracker"
        const val MODEL_ASSET = "face_landmarker.task"
    }
}
