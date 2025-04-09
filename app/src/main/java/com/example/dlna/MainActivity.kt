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
import com.example.dlna.util.SecurityAwareSAXParser

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
     * 活动创建生命周期回调
     * 
     * 初始化UI组件并设置事件监听器。
     * 
     * @param savedInstanceState 保存的实例状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化安全XML解析器设置
        SecurityAwareSAXParser.newSecureFactory()
        Log.d(TAG, "已初始化安全XML解析器设置")
        
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
     * 启动DLNA服务
     */
    private fun startDLNAService() {
        Log.d(TAG, "启动DLNA服务")
        val intent = Intent(this, DLNAService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 停止DLNA服务
     */
    private fun stopDLNAService() {
        Log.d(TAG, "停止DLNA服务")
        val serviceIntent = Intent(this, DLNAService::class.java)
        stopService(serviceIntent)
    }

    /**
     * 更新用户界面
     * 
     * 根据服务的绑定状态更新UI元素，
     * 包括状态文本和按钮标签
     */
    private fun updateUI() {
        if (bound) {
            statusText.text = "DLNA服务状态：运行中"
            startButton.text = "停止服务"
        } else {
            statusText.text = "DLNA服务状态：已停止"
            startButton.text = "启动服务"
        }
    }

    /**
     * 活动销毁生命周期回调
     * 
     * 在活动被销毁前，确保解绑服务以防止内存泄漏
     */
    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
} 