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
     * 禁用 XML 架构验证。Android 环境下可能没有对应 XSD 文件。
     *
     * @return null 表示不进行架构验证
     */
    override fun getSchemaSources() = null

    /**
     * 创建兼容 Android 的 XML 解析器，避免设置 Android 不支持的 XML 特性（如 disallow-doctype-decl）。
     *
     * @return 配置好的 XMLReader 实例
     * @throws RuntimeException 若创建失败
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
     * 在 SAXParserFactory 上尝试设置安全特性（禁用外部实体），避免 XXE。
     * 若平台不支持则忽略异常。
     *
     * @param factory SAX 解析器工厂
     * 无返回值。
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
     * 在 XMLReader 上尝试设置安全特性（禁用外部实体）。
     *
     * @param reader XMLReader 实例
     * 无返回值。
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