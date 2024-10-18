package com.lhstack.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

val httpClient: CloseableHttpClient = HttpClients.createDefault()

val gson: Gson = GsonBuilder().create()

class Api {
    companion object {

    }
}

class InvokeParameter(var name: String, var classname: String, var value: String?)

class MethodInvokeEntity(
    var classname: String?,
    var method: String?,
    var type: String,
    var parameters: Array<InvokeParameter>?,
    var preScripts: Array<String>?,
    var postScripts: Array<String>?,
    var invokeProxy: Boolean?,
)

class InvokeScriptLogEntityResp(val type:String,val value:String)

class InvokeScriptResp(val result:String?,val logEntities:MutableList<InvokeScriptLogEntityResp>?)

class MockNormalParameterReq(val mockTag:String,val parameterType:String)

class MockNormalParameterResp(val value:String)

fun Api.Companion.getMockParameters(port: String, entity: MockNormalParameterReq): String {
    return invokeBase("http://localhost:${port}/mockNormalParameter", gson.toJson(entity))
}

fun Api.Companion.invokeMethod(port: Int, entity: MethodInvokeEntity): String {
    return invokeBase("http://localhost:${port}/invoke", gson.toJson(entity))
}

fun Api.Companion.invokeScript(script: String, port: Any): InvokeScriptResp {
    return gson.fromJson(invokeBase("http://localhost:${port}/script", script), InvokeScriptResp::class.java)
}

private fun invokeBase(uri: String, json: String): String {
    return httpClient.execute(HttpPost(uri).apply {
        this.entity = StringEntity(json, ContentType.APPLICATION_JSON)
    }).use {
        if (it.statusLine.statusCode != 200) {
            it.statusLine.reasonPhrase
        } else {
            EntityUtils.toString(it.entity,"UTF-8")
        }
    }
}