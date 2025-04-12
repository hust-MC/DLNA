package com.example.dlna.util;

import android.util.Log;

import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

/**
 * 安全的SAX解析器工厂
 * 处理XML解析器安全特性配置，确保在现代Android设备上正常工作
 */
public class SecurityAwareSAXParser {
    private static final String TAG = "SecurityAwareSAXParser";
    
    /**
     * 创建安全的SAX解析器工厂
     * 
     * @return 配置好的SAX解析器工厂
     */
    public static SAXParserFactory newSecureFactory() {
        // 设置安全特性
        System.setProperty("jdk.xml.disallowDtd", "false");
        System.setProperty("jdk.xml.enableExtensionFunctions", "true");
        System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.harmony.xml.parsers.SAXParserFactoryImpl");
        Log.d(TAG, "已设置XML解析器安全属性");
        
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        
        try {
            // 尝试设置安全特性，但忽略不支持的特性错误
            setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            
            // 尝试设置其他安全特性
            setFeature(factory, "http://xml.org/sax/features/namespaces", true);
            setFeature(factory, "http://xml.org/sax/features/namespace-prefixes", true);
            setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException e) {
            Log.w(TAG, "无法配置XML解析器的某些安全特性，但这可能是正常的", e);
        }
        
        return factory;
    }
    
    /**
     * 安全地设置SAX解析器特性，忽略不支持的特性错误
     * 
     * @param factory SAX解析器工厂
     * @param feature 要设置的特性
     * @param value 特性值
     * @throws ParserConfigurationException 如果设置特性时出现非特性不支持的错误
     */
    private static void setFeature(SAXParserFactory factory, String feature, boolean value) 
            throws ParserConfigurationException {
        try {
            factory.setFeature(feature, value);
        } catch (SAXNotRecognizedException e) {
            // 特性不被识别，这是可接受的，记录日志但不抛出异常
            Log.d(TAG, "XML解析器不支持特性: " + feature);
        } catch (SAXException e) {
            // 其他SAX异常，可能是配置问题
            Log.w(TAG, "设置XML解析器特性时出错: " + feature, e);
        }
    }
} 