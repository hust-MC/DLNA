package com.example.dlna

import android.util.Log
import org.fourthline.cling.support.avtransport.lastchange.AVTransportLastChangeParser

/**
 * 自定义的LastChange解析器类，用于解决Android上的XML解析器兼容性问题
 */
class CustomAVTransportLastChangeParser : AVTransportLastChangeParser() {
    // 禁用架构验证
    override fun getSchemaSources() = null

    // 重写create方法完全替换原始SAXParser中的实现，避免设置不兼容的XML解析器特性
    override fun create(): org.xml.sax.XMLReader {
        try {
            val factory = javax.xml.parsers.SAXParserFactory.newInstance()
            factory.isNamespaceAware = true

            // 不尝试设置不兼容的特性http://apache.org/xml/features/disallow-doctype-decl
            // 但仍设置其他重要安全特性（如果支持）
            try {
                // 禁用外部实体处理，防止XXE攻击
                factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
                )
                factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
                )
            } catch (e: Exception) {
                // 如果特性不被支持，记录警告但继续执行
                Log.w(TAG, "XML解析器不支持禁用外部实体特性: ${e.message}")
            }

            // 创建XMLReader而不设置不兼容的特性
            val reader = factory.newSAXParser().xmlReader

            // 设置XMLReader的特性（如果支持）
            try {
                reader.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
                )
                reader.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
                )
            } catch (e: Exception) {
                // 如果特性不被支持，记录警告但继续执行
                Log.w(TAG, "XMLReader不支持禁用外部实体特性: ${e.message}")
            }

            return reader
        } catch (e: Exception) {
            // 如果创建失败，记录错误并抛出运行时异常
            Log.e(TAG, "创建XMLReader失败", e)
            throw RuntimeException("创建XMLReader失败", e)
        }
    }

    companion object {
        const val TAG = "CustomAVTransportLastChangeParser"
    }
}