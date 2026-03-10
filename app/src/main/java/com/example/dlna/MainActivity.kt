package com.example.dlna

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView

/**
 * 主活动界面
 *
 * 该活动是应用程序的主要入口点，用于控制DLNA服务的启动和停止。
 * 它提供了简单的用户界面，显示服务状态并允许用户通过按钮交互启停服务。
 *
 * @author Max
 */
class MainActivity : Activity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    /**
     * DLNA服务引用
     * 
     * 保存对绑定服务的引用，以便可以与之交互
     */
    private var dlnaService: DLNAService? = null
    
    /**
     * 服务绑定状态
     * 
     * 跟踪当前是否已绑定到DLNA服务
     */
    private var bound = false
    
    /**
     * 状态文本视图
     * 
     * 用于显示当前DLNA服务的运行状态
     */
    private lateinit var statusText: TextView
    
    /**
     * 控制按钮
     * 
     * 用于启动或停止DLNA服务的按钮
     */
    private lateinit var startButton: Button

    /**
     * 服务连接对象
     * 
     * 用于处理与DLNA服务的绑定和解绑过程，
     * 包括服务连接和断开连接的回调处理。
     */
    private val connection = object : ServiceConnection {
        /**
         * 服务连接成功回调
         * 
         * 当成功绑定到DLNA服务时调用。
         * 获取服务引用并更新UI状态。
         * 
         * @param name 服务的组件名称
         * @param service 服务的IBinder接口
         */
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as DLNAService.LocalBinder
            dlnaService = binder.getService()
            bound = true
            updateUI()
        }

        /**
         * 服务断开连接回调
         * 
         * 当与DLNA服务的连接意外断开时调用。
         * 更新UI以反映服务已停止。
         * 
         * @param name 服务的组件名称
         */
        override fun onServiceDisconnected(name: ComponentName) {
            bound = false
            updateUI()
        }
    }

    /**
     * 活动创建生命周期回调，初始化 UI 并设置启停按钮监听。
     *
     * @param savedInstanceState 保存的实例状态（可为 null）
     * 无返回值。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        startButton = findViewById(R.id.start_button)
        
        startButton.setOnClickListener {
            if (!bound) {
                startDLNAService()
            } else {
                stopDLNAService()
            }
        }
    }

    /**
     * 通过 bindService 启动并绑定 DLNA 服务，连接成功后由 connection 回调更新 UI。
     * 无参数，无返回值。
     */
    private fun startDLNAService() {
        Log.d(TAG, getString(R.string.log_main_activity_start_bind_service))
        val intent = Intent(this, DLNAService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 解绑 DLNA 服务并更新 UI 状态。
     * 无参数，无返回值。
     */
    private fun stopDLNAService() {
        Log.d(TAG, getString(R.string.log_stop_dlna_service))
        unbindService(connection)
        bound = false
        updateUI()
    }

    /**
     * 根据当前是否已绑定 DLNA 服务，更新状态文案与按钮文字（运行中/已停止、启动/停止）。
     * 无参数，无返回值。
     */
    private fun updateUI() {
        if (bound) {
            statusText.text = getString(R.string.dlna_service_running)
            startButton.text = getString(R.string.stop_service)
        } else {
            statusText.text = getString(R.string.dlna_service_stopped)
            startButton.text = getString(R.string.start_service)
        }
    }

    /**
     * 活动销毁前解绑 DLNA 服务，防止泄漏。
     * 无参数，无返回值。
     */
    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
} 