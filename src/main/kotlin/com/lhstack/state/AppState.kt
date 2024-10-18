package com.lhstack.state

class AppState {

    //key=project:moduleName,value=[ports]
    private val modulePorts = hashMapOf<String, HashMap<String,ArrayList<Int>>>()

    companion object {
        val INSTANCE = AppState()
    }

    fun addPort(project: String, module: String, port: Int) {
        modulePorts.computeIfAbsent(project) {
            hashMapOf()
        }.computeIfAbsent(module){
            arrayListOf()
        }.add(port)
    }

    fun removePort(project: String, module: String, port: Int) {
        modulePorts.computeIfAbsent(project) {
            hashMapOf()
        }.computeIfAbsent(module){
            arrayListOf()
        }.remove(port)
    }

    fun getPorts(project: String, module: String): ArrayList<Int>? {
        return modulePorts[project]?.get(module)
    }

    fun contains(project: String, module: String) = modulePorts[project]?.containsKey(module)

}