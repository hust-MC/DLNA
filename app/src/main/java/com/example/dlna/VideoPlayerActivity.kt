package com.example.dlna

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.max.videoplayer.MediaPlayerManager
import java.util.Locale

/**
 * 视频播放器 Activity，用于展示 DLNA 投屏的视频画面。
 *
 * 由 [MediaRendererService.play] 在收到控制点播放请求时启动，接收投屏 URI，
 * 通过 [MediaPlayerManager]（ExoPlayer）解码并渲染到 SurfaceView。
 * 同时将播放器与 Activity 注册到 [MediaRendererService] / [RenderingControlService]，
 * 以便远程控制播放、暂停、停止、跳转与音量。
 *
 * @author Max
 */
class VideoPlayerActivity : Activity(),
    MediaPlayerManager.MediaStateListener {

    companion object {
        private const val TAG = "VideoPlayerActivity"
        /** Intent 中携带的视频 URI 的 key */
        private const val EXTRA_VIDEO_URI = "extra_video_uri"

        /**
         * 以新任务方式启动视频播放页并传入投屏 URI。
         *
         * @param context 应用上下文（建议 ApplicationContext）
         * @param videoUri 要播放的媒体 URI（如 M3U8、MP4 地址）
         */
        fun start(context: Context, videoUri: String) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, videoUri)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /** 视频渲染 Surface */
    private lateinit var surfaceView: SurfaceView
    /** 进度条 */
    private lateinit var seekBar: SeekBar
    /** 当前进度/总时长文本（如 00:00 / 00:00） */
    private lateinit var tvDuration: TextView
    /** 播放/暂停按钮 */
    private lateinit var btnPlayPause: ImageButton
    /** 返回按钮 */
    private lateinit var btnBack: ImageButton

    /** 本次要播放的视频 URI（来自 DLNA 控制点） */
    private var videoUri: String = ""
    /** 主线程 Handler，用于进度更新与 UI 回调 */
    private val handler = Handler(Looper.getMainLooper())
    
    /** 视频宽高（用于按比例适配 SurfaceView） */
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    /** 每秒执行一次，刷新 SeekBar 与时长显示 */
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    /** 本页持有的播放器实例，与 MediaRendererService 共享同一引用 */
    private var mediaPlayerManager: MediaPlayerManager? = null

    /** 进入后台前是否在播放，用于 onResume 时恢复播放 */
    private var wasPlayingBeforeBackground = false

    /** 是否已对当前 URI 执行过 setMediaURIAndPlay，避免 Surface 重建时重复播放 */
    private var isVideoInitialized = false

    /**
     * 初始化全屏、常亮、Surface 与播放器，并注册到 MediaRendererService / RenderingControlService。
     *
     * @param savedInstanceState 保存的实例状态（可为 null）
     * 无返回值。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        // 设置屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 隐藏状态栏和导航栏，实现全屏效果（API 30+ 使用 WindowInsetsController，避免弃用警告）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(android.view.WindowInsets.Type.systemBars())
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }

        videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: ""

        // 初始化MediaPlayerManager
        initMediaPlayerManager()

        initViews()
        setupListeners()

        // 获取Surface准备渲染视频
        surfaceView.holder.addCallback(VideoSurfaceCallback())

        // 注册当前Activity到MediaRendererService
        MediaRendererService.setPlayerActivity(this)
        // 将MediaPlayerManager设置到MediaRendererService 与 RenderingControlService（initMediaPlayerManager 后非空）
        mediaPlayerManager?.let { manager ->
            MediaRendererService.setMediaPlayerManager(manager)
            RenderingControlService.setMediaPlayerManager(manager)
        }


    }

    /**
     * 创建 MediaPlayerManager 并设置本 Activity 为状态监听器。
     * 无参数，无返回值。
     */
    private fun initMediaPlayerManager() {
        mediaPlayerManager = MediaPlayerManager(this).apply {
            setStateListener(this@VideoPlayerActivity)
        }
    }

    /**
     * 绑定布局中的 SurfaceView、SeekBar、时长文本、播放/暂停与返回按钮。
     * 无参数，无返回值。
     */
    private fun initViews() {
        surfaceView = findViewById(R.id.playerSurfaceView)
        seekBar = findViewById(R.id.seekBar)
        tvDuration = findViewById(R.id.tvDuration)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnBack = findViewById(R.id.btnBack)
    }

    /**
     * 为播放/暂停、返回、SeekBar 设置点击与拖动监听；用户拖动进度条时暂停定时刷新。
     * 无参数，无返回值。
     */
    private fun setupListeners() {
        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        btnBack.setOnClickListener {
            mediaPlayerManager?.release()
            finish()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayerManager?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // 暂停进度条自动更新
                handler.removeCallbacks(updateSeekBarRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 恢复进度条自动更新
                handler.postDelayed(updateSeekBarRunnable, 1000)
            }
        })
    }

    /**
     * 根据当前播放状态切换播放/暂停，并更新播放/暂停按钮图标。
     * 无参数，无返回值。
     */
    private fun togglePlayPause() {
        mediaPlayerManager?.let { player ->
            if (player.getCurrentState() == MediaPlayerManager.PlaybackState.PLAYING) {
                player.pause()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                player.play()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
    }

    /**
     * 从播放器读取当前进度与时长，刷新 SeekBar 和时长文本（如 00:00 / 00:00）。
     * 无参数，无返回值。
     */
    private fun updateProgress() {
        mediaPlayerManager?.let { player ->
            val position = player.getCurrentPosition()
            val duration = player.getDuration()

            if (duration > 0) {
                seekBar.max = duration
                seekBar.progress = position

                val positionStr = formatTime(position)
                val durationStr = formatTime(duration)
                tvDuration.text = getString(R.string.progress_duration_format, positionStr, durationStr)
            }
        }
    }

    /**
     * 将毫秒时长格式化为 HH:MM:SS 字符串。
     *
     * @param timeMs 毫秒数
     * @return 如 "01:23:45" 的字符串
     */
    private fun formatTime(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * 恢复时启动进度刷新；若进入后台前在播放则继续播放。
     * 无参数，无返回值。
     */
    override fun onResume() {
        super.onResume()
        handler.post(updateSeekBarRunnable)

        // 如果之前是播放状态，则恢复播放位置并继续播放
        if (wasPlayingBeforeBackground) {
            mediaPlayerManager?.play()
            wasPlayingBeforeBackground = false
        }

        // 设置播放状态对应的按钮图标
        mediaPlayerManager?.let { player ->
            val isPlaying = player.getCurrentState() == MediaPlayerManager.PlaybackState.PLAYING
            btnPlayPause.setImageResource(
                if (isPlaying) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                }
            )
        }
    }

    /**
     * 进入后台时停止进度刷新；若正在播放则暂停并记录状态，便于 onResume 恢复。
     * 无参数，无返回值。
     */
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayerManager?.let { player ->
            wasPlayingBeforeBackground = player.getCurrentState() == MediaPlayerManager.PlaybackState.PLAYING
            if (wasPlayingBeforeBackground) {
                // 保存当前播放位置
                player.pause()
            }
        }
    }

    /**
     * 销毁时释放 MediaPlayerManager，避免泄漏。
     * 无参数，无返回值。
     */
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayerManager?.release()
        mediaPlayerManager = null
    }

    /**
     * 播放状态变化时同步更新播放/暂停按钮图标。
     *
     * @param state 当前播放状态（PLAYING/PAUSED/STOPPED/ERROR）
     */
    override fun onPlaybackStateChanged(state: MediaPlayerManager.PlaybackState) {
        runOnUiThread {
            when (state) {
                MediaPlayerManager.PlaybackState.PLAYING -> {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }

                MediaPlayerManager.PlaybackState.PAUSED, MediaPlayerManager.PlaybackState.STOPPED, MediaPlayerManager.PlaybackState.ERROR -> {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }

                else -> {}
            }
        }
    }

    /**
     * 播放结束时回调，可用于记录日志或后续扩展（如自动关页）。
     */
    override fun onPlaybackCompleted() {
        Log.d(TAG, getString(R.string.log_playback_completed))
    }

    /**
     * 媒体时长就绪时设置 SeekBar 的最大值。
     *
     * @param durationMs 媒体总时长（毫秒）
     */
    fun onDurationChanged(durationMs: Int) {
        runOnUiThread {
            seekBar.max = durationMs
        }
    }

    /**
     * 媒体准备就绪回调，可获取时长并开始播放。
     *
     * @param durationMs 媒体总时长（毫秒）
     */
    override fun onPrepared(durationMs: Int) {
        Log.d(TAG, getString(R.string.log_on_prepared, durationMs))
    }

    /**
     * 播放进度更新回调。本页已通过 updateProgress 定时刷新，此处可空实现。
     *
     * @param positionMs 当前播放位置（毫秒）
     */
    override fun onProgressUpdate(positionMs: Int) {}

    /**
     * 播放出错时的回调，可在此显示 Toast 等提示。
     *
     * @param errorMsg 错误信息
     */
    override fun onError(errorMsg: String) {}

    /**
     * 缓冲进度百分比更新。
     *
     * @param percent 缓冲进度 0～100
     */
    override fun onBufferingUpdate(percent: Int) {
        Log.d(TAG, getString(R.string.log_on_buffering_update, percent))
    }
    
    /**
     * 视频尺寸已知后保存宽高，并在主线程中按比例调整 SurfaceView。
     *
     * @param width  视频宽度（像素）
     * @param height 视频高度（像素）
     */
    override fun onVideoSizeChanged(width: Int, height: Int) {
        Log.d(TAG, getString(R.string.log_video_size_changed, width, height))
        videoWidth = width
        videoHeight = height
        runOnUiThread { adjustSurfaceViewSize() }
    }
    
    /**
     * 按视频宽高比调整 SurfaceView 尺寸，在屏幕内居中、不拉伸。
     * 视频更宽时以屏幕宽度为准留上下黑边，更高时以屏幕高度为准留左右黑边。
     *
     * 无参数，无返回值。
     */
    private fun adjustSurfaceViewSize() {
        if (videoWidth == 0 || videoHeight == 0) {
            Log.w(TAG, getString(R.string.log_video_size_invalid, videoWidth, videoHeight))
            return
        }

        // 使用DisplayMetrics获取准确的屏幕尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        Log.d(TAG, getString(R.string.log_screen_video_size, screenWidth, screenHeight, videoWidth, videoHeight))
        
        // 计算视频和屏幕的宽高比
        val videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
        
        // 计算SurfaceView的实际尺寸（保持视频宽高比）
        val (surfaceWidth, surfaceHeight) = if (videoAspectRatio > screenAspectRatio) {
            // 视频更宽（横屏视频在竖屏设备）→ 以屏幕宽度为准，上下留黑边
            val width = screenWidth
            val height = (screenWidth / videoAspectRatio).toInt()
            Pair(width, height)
        } else {
            // 视频更高（竖屏视频在横屏设备）→ 以屏幕高度为准，左右留黑边
            val height = screenHeight
            val width = (screenHeight * videoAspectRatio).toInt()
            Pair(width, height)
        }
        
        // 更新SurfaceView布局参数
        surfaceView.layoutParams = surfaceView.layoutParams.apply {
            width = surfaceWidth
            height = surfaceHeight
        }
        
        Log.d(TAG, getString(R.string.log_surface_view_adjusted, surfaceWidth, surfaceHeight, videoAspectRatio, screenAspectRatio))
    }

    /**
     * 供 MediaRendererService 远程调用：从当前进度继续播放，并更新按钮图标。
     * 无参数，无返回值。
     */
    fun playFromDLNA() {
        mediaPlayerManager?.play()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
    }

    /**
     * 供 DLNA 远程调用：暂停播放并更新按钮图标。
     * 无参数，无返回值。
     */
    fun pauseFromDLNA() {
        mediaPlayerManager?.pause()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
    }

    /**
     * 供 DLNA 远程调用：停止播放并更新按钮图标。
     * 无参数，无返回值。
     */
    fun stopFromDLNA() {
        mediaPlayerManager?.stop()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
    }

    /**
     * 供 DLNA 远程调用：跳转到指定毫秒位置。
     *
     * @param positionMs 目标位置（毫秒）
     */
    fun seekFromDLNA(positionMs: Int) {
        mediaPlayerManager?.seekTo(positionMs)
    }

    /**
     * Surface 生命周期回调：在 Surface 创建/变更时交给播放器，首次创建时开始播放。
     */
    private inner class VideoSurfaceCallback : SurfaceHolder.Callback {
        /**
         * Surface 创建后设置给播放器；仅首次创建时设置 URI 并播放，避免重复。
         *
         * @param holder 当前 Surface 的 Holder
         */
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, getString(R.string.log_surface_created))
            mediaPlayerManager?.setSurface(holder.surface)
            if (!isVideoInitialized) {
                mediaPlayerManager?.setMediaURIAndPlay(videoUri)
                isVideoInitialized = true
            }
        }

        /**
         * Surface 尺寸或格式变化时，将新 Surface 交给播放器。
         *
         * @param holder  SurfaceHolder
         * @param format  像素格式
         * @param width   宽度
         * @param height  高度
         */
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d(TAG, getString(R.string.log_surface_changed))
            mediaPlayerManager?.setSurface(holder.surface)
        }

        /**
         * Surface 销毁时清除播放器上的 Surface，避免渲染异常。
         *
         * @param holder SurfaceHolder
         */
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, getString(R.string.log_surface_destroyed))
            mediaPlayerManager?.setSurface(null)
        }
    }
} 