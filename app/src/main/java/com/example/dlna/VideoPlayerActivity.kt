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
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.max.videoplayer.MediaPlayerManager
import java.util.Locale

/**
 * 视频播放器Activity
 * 用于显示DLNA投屏的视频内容
 */
class VideoPlayerActivity : Activity(),
    MediaPlayerManager.MediaStateListener {

    companion object {
        private const val TAG = "VideoPlayerActivity"
        private const val EXTRA_VIDEO_URI = "extra_video_uri"

        /**
         * 启动播放器Activity的静态方法
         */
        fun start(context: Context, videoUri: String) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, videoUri)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var seekBar: SeekBar
    private lateinit var tvDuration: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnBack: ImageButton

    private var videoUri: String = ""
    private val handler = Handler(Looper.getMainLooper())
    
    // 视频尺寸
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    // 本地持有的MediaPlayerManager实例
    private var mediaPlayerManager: MediaPlayerManager? = null

    // 记录后台时的播放状态和位置
    private var wasPlayingBeforeBackground = false

    // 添加一个标志，记录视频是否已经初始化过
    private var isVideoInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        // 设置屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 隐藏状态栏和导航栏，实现全屏效果
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: ""

        // 初始化MediaPlayerManager
        initMediaPlayerManager()

        initViews()
        setupListeners()

        // 获取Surface准备渲染视频
        surfaceView.holder.addCallback(VideoSurfaceCallback())

        // 注册当前Activity到MediaRendererService
        MediaRendererService.setPlayerActivity(this)
        // 将MediaPlayerManager设置到MediaRendererService
        MediaRendererService.setMediaPlayerManager(mediaPlayerManager!!)
    }

    private fun initMediaPlayerManager() {
        mediaPlayerManager = MediaPlayerManager(this).apply {
            setStateListener(this@VideoPlayerActivity)
        }
    }

    private fun initViews() {
        surfaceView = findViewById(R.id.playerSurfaceView)
        seekBar = findViewById(R.id.seekBar)
        tvDuration = findViewById(R.id.tvDuration)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnBack = findViewById(R.id.btnBack)
    }

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

    private fun updateProgress() {
        mediaPlayerManager?.let { player ->
            val position = player.getCurrentPosition()
            val duration = player.getDuration()

            if (duration > 0) {
                seekBar.max = duration
                seekBar.progress = position

                val positionStr = formatTime(position)
                val durationStr = formatTime(duration)
                tvDuration.text = "$positionStr / $durationStr"
            }
        }
    }

    private fun formatTime(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onResume() {
        super.onResume()
        // 开始定时更新进度
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

    override fun onPause() {
        super.onPause()
        // 停止定时更新进度
        handler.removeCallbacks(updateSeekBarRunnable)

        // 记录当前播放状态和位置，并暂停
        mediaPlayerManager?.let { player ->
            wasPlayingBeforeBackground = player.getCurrentState() == MediaPlayerManager.PlaybackState.PLAYING
            if (wasPlayingBeforeBackground) {
                // 保存当前播放位置
                player.pause()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放MediaPlayerManager资源
        mediaPlayerManager?.release()
        mediaPlayerManager = null
    }

    // MediaStateListener接口实现
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

    override fun onPlaybackCompleted() {
        Log.d(TAG, "onPlaybackCompleted")
    }

    fun onDurationChanged(durationMs: Int) {
        runOnUiThread {
            seekBar.max = durationMs
        }
    }

    override fun onPrepared(durationMs: Int) {
        Log.d(TAG, "onPrepared: $durationMs")
    }

    override fun onProgressUpdate(positionMs: Int) {
        // 这里不需要处理，因为我们已经有了updateProgress方法
    }

    override fun onError(errorMsg: String) {
        // 可以在这里添加错误处理逻辑，比如显示Toast提示用户
    }

    override fun onBufferingUpdate(percent: Int) {
        Log.d(TAG, "onBufferingUpdate: $percent")
    }
    
    override fun onVideoSizeChanged(width: Int, height: Int) {
        Log.d(TAG, "视频尺寸变化: ${width}x${height}")
        videoWidth = width
        videoHeight = height
        
        // 调整SurfaceView大小以适配视频，保持宽高比不拉伸
        runOnUiThread {
            adjustSurfaceViewSize()
        }
    }
    
    /**
     * 调整SurfaceView大小以适配视频宽高比
     * 在屏幕中居中显示，不拉伸变形
     */
    private fun adjustSurfaceViewSize() {
        if (videoWidth == 0 || videoHeight == 0) {
            Log.w(TAG, "视频尺寸无效: ${videoWidth}x${videoHeight}")
            return
        }
        
        // 使用DisplayMetrics获取准确的屏幕尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        Log.d(TAG, "屏幕尺寸: ${screenWidth}x${screenHeight}, 视频尺寸: ${videoWidth}x${videoHeight}")
        
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
        
        Log.d(TAG, "SurfaceView调整为: ${surfaceWidth}x${surfaceHeight} (视频比例: %.2f, 屏幕比例: %.2f)".format(videoAspectRatio, screenAspectRatio))
    }

    // 用于DLNA控制器调用的方法
    fun playFromDLNA() {
        mediaPlayerManager?.play()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
    }

    fun pauseFromDLNA() {
        mediaPlayerManager?.pause()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
    }

    fun stopFromDLNA() {
        mediaPlayerManager?.stop()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
    }

    fun seekFromDLNA(positionMs: Int) {
        mediaPlayerManager?.seekTo(positionMs)
    }

    private inner class VideoSurfaceCallback : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "surfaceCreated")
            // 当Surface创建后，将其设置给MediaPlayerManager
            mediaPlayerManager?.setSurface(holder.surface)

            // 只有在第一次初始化时才设置媒体URI并播放
            if (!isVideoInitialized) {
                // 设置URI并播放
                mediaPlayerManager?.setMediaURIAndPlay(videoUri)
                isVideoInitialized = true
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d(TAG, "surfaceChanged")

            // Surface尺寸或格式变化时，更新Surface
            mediaPlayerManager?.setSurface(holder.surface)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "surfaceDestroyed")

            // Surface销毁时，清除播放器的Surface
            mediaPlayerManager?.setSurface(null)
        }
    }
} 