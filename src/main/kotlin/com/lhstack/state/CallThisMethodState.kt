package com.lhstack.state

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.lhstack.api.gson
import com.lhstack.view.TabObjData
import org.apache.commons.lang3.StringUtils

class HashMapConverter : com.intellij.util.xmlb.Converter<HashMap<String, String>>() {

    private val gson = Gson()
    override fun toString(value: HashMap<String, String>): String? {
        return gson.toJson(value)
    }

    override fun fromString(value: String): HashMap<String, String>? {
        if (StringUtils.isEmpty(value)) {
            return hashMapOf()
        }
        return gson.fromJson(value, object : TypeToken<HashMap<String, String>>() {}.type)
    }
}

class MethodCacheConvert : com.intellij.util.xmlb.Converter<HashMap<String, TabObjData>>() {
    override fun toString(value: HashMap<String, TabObjData>): String? {
        return gson.toJson(value)
    }

    override fun fromString(value: String): HashMap<String, TabObjData>? {
        if (StringUtils.isEmpty(value)) {
            return hashMapOf()
        }
        return gson.fromJson(value, object : TypeToken<HashMap<String, TabObjData>>() {}.type)
    }

}

class ArrayListTabObjDataConverter : com.intellij.util.xmlb.Converter<ArrayList<TabObjData>>() {
    override fun toString(value: ArrayList<TabObjData>): String? {
        return gson.toJson(value)
    }

    override fun fromString(value: String): ArrayList<TabObjData>? {
        if (StringUtils.isEmpty(value)) {
            return ArrayList()
        }
        return gson.fromJson(value, object : TypeToken<ArrayList<TabObjData>>() {}.type)
    }

}


@State(name = "CallThisMethod", storages = [Storage(value = "ToolsPluginCallThisMethod.xml")])
@Service
class CallThisMethodState : PersistentStateComponent<CallThisMethodState.State> {

    private var state = State()

    companion object {

        fun getInstance(project: Project) = project.service<CallThisMethodState>().state
//        fun getInstance(project: Project) = CallThisMethodState().state

        fun getInstance() = service<CallThisMethodState>().state
//        fun getInstance() = CallThisMethodState().state

    }

    class State {
        var preScript: String = ""

        var postScript: String = ""

        @field:OptionTag(converter = HashMapConverter::class)
        var modulePreScript: HashMap<String, String> = hashMapOf()

        @field:OptionTag(converter = HashMapConverter::class)
        var modulePostScript: HashMap<String, String> = hashMapOf()

        @field:OptionTag(converter = ArrayListTabObjDataConverter::class)
        var history = ArrayList<TabObjData>()

        @field:OptionTag(converter = MethodCacheConvert::class)
        var methodCache = hashMapOf<String, TabObjData>()

        /**
         * 历史记录保存最大条数
         */
        var historyTotal = 100
    }

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {
        this.state = state
    }
}