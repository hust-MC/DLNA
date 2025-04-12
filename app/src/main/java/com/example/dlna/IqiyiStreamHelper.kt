package com.example.dlna

import android.util.Log

/**
 * 爱奇艺流媒体辅助类
 * 
 * 该类用于处理爱奇艺的视频流
 */
class IqiyiStreamHelper {
    companion object {
        private const val TAG = "IqiyiStreamHelper"
        
        /**
         * 检查URL是否是爱奇艺流媒体
         */
        fun isIqiyiStream(url: String): Boolean {
            return url.contains("iqiyi.com", ignoreCase = true) || 
                   url.contains("qiyi.com", ignoreCase = true) || 
                   url.contains("iqiy", ignoreCase = true)
        }
        
        /**
         * 清理爱奇艺URL，移除特殊字符
         */
        fun cleanIqiyiUrl(url: String): String {
            val cleanedUrl = url.replace("&amp;", "&")
                .replace(" ", "%20")
                .replace("https://", "http://") // 尝试使用HTTP替代HTTPS
            
            Log.d(TAG, "爱奇艺链接清理后: $cleanedUrl")
            return cleanedUrl
        }
    }
}