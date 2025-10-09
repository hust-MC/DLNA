package com.example.dlna

import android.util.Log
import org.fourthline.cling.support.avtransport.lastchange.AVTransportLastChangeParser

/**
 * 自定义的AVTransport LastChange解析器
 * 
 * 解决Android平台XML解析器特性不兼容的问题。
 * 原生的AVTransportLastChangeParser会尝试设置某些Android不支持的XML特性，导致初始化失败。
 * 
 * @author Max
 */
class CustomAVTransportLastChangeParser : AVTransportLastChangeParser() {
    
    companion object {
        private const val TAG = "CustomLastChangeParser"
        
        /**
         * 单例实例，避免重复创建
         */
        val INSTANCE = CustomAVTransportLastChangeParser()
    }
    
    /**
     * 禁用XML架构验证
     * Android环境下可能没有对应的XSD文件
     */
    override fun getSchemaSources() = null

    /**
     * 创建兼容Android的XML解析器
     * 避免设置Android不支持的XML特性
     */
    override fun create(): org.xml.sax.XMLReader {
        try {
            val factory = javax.xml.parsers.SAXParserFactory.newInstance()
            factory.isNamespaceAware = true

            // 尝试设置安全特性（如果支持）
            trySetSecurityFeature(factory)

            val reader = factory.newSAXParser().xmlReader
            
            // 尝试在XMLReader上设置安全特性（如果支持）
            trySetSecurityFeature(reader)

            return reader
        } catch (e: Exception) {
            Log.e(TAG, "创建XML解析器失败", e)
            throw RuntimeException("创建XML解析器失败", e)
        }
    }
    
    /**
     * 尝试在SAXParserFactory上设置安全特性
     */
    private fun trySetSecurityFeature(factory: javax.xml.parsers.SAXParserFactory) {
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (e: Exception) {
            Log.w(TAG, "SAXParserFactory不支持部分安全特性: ${e.message}")
        }
    }
    
    /**
     * 尝试在XMLReader上设置安全特性
     */
    private fun trySetSecurityFeature(reader: org.xml.sax.XMLReader) {
        try {
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (e: Exception) {
            Log.w(TAG, "XMLReader不支持部分安全特性: ${e.message}")
        }
    }
}