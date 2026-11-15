package com.couchjoust.core

data class SoundCommand(val command:Int,val hold:Int)
data class SoundEvent(val priority:Int,val commands:List<SoundCommand>)

class SoundSequencer(val events:Map<String,SoundEvent>,val send:(Int)->Unit) {
    var priority=0
    var hold=0
    var event:SoundEvent?=null
    var index=0
    fun request(name:String,gameplay:Boolean):Boolean {
        val next=events.getValue(name)
        if(next.priority<128 && !gameplay) return false
        if(hold>0 && next.priority<priority) return false
        priority=next.priority;event=next;index=0;hold=1
        return true
    }
    private fun dispatch() {
        val command=event!!.commands[index++]
        hold=command.hold
        if(command.command!=0) send(command.command)
    }
    fun tick() {
        if(hold>0 && --hold==0) {
            if(index<event!!.commands.size) dispatch()
            else { priority=0;event=null }
        }
    }
    fun reset() { priority=0;hold=0;event=null;index=0;send(63) }
}
