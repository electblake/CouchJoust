package com.couchjoust.core

/** ATMST lessons run the same actors, controls and collision routines as a game. */
class Demonstration {
    private val waits=intArrayOf(2,9,7,5,2,2,2,1,1,5,6,2,10,5)
    private val additions=arrayOf("ATX1","ATX2","ATX3","ATX4","ATX5","ATX6","ATX7","ATX8","","","ATX13","","ATX11","")
    private val erasures=arrayOf("","","ATX2","ATX3","ATX4","","ATX6","ATX7","ATX5","ATX8","","ATX1","ATX13","ATX11")
    val pages=mutableListOf<String>()
    var stage=0
    var elapsed=0
    var complete=false
    var lavaWarning=false
    fun step(g:Game) {
        if(complete) return
        elapsed++
        if(elapsed==1) pages.remove(erasures[stage])
        if(elapsed==8 && additions[stage].isNotEmpty()) pages.add(additions[stage])
        if(elapsed==16) when(stage) {
            1 -> g.players[0].bird!!.apply { ai="LESSON_FLY";aiTimer=160;budget=160 }
            2 -> {
                val enemy=enemy(g,0,291,129);enemy.facing=-1;enemy.ai="LINET"
                g.players[1].bird!!.apply { ai="LESSON_JOUST";aiTimer=96 }
            }
            3 -> g.players[0].bird!!.ai="LESSON_EGG"
            4 -> lavaWarning=true
            5,6,7 -> enemy(g,stage-5,8,210).apply { ai="LESSON_ENEMY";aiTimer=intArrayOf(376,248,128)[stage-5];mode=BirdMode.GROUND }
            11 -> g.scheduler.actors().filterIsInstance<Bird>().filter { it.owner<0 }.forEach { it.mode=BirdMode.DEPART }
            12 -> g.spawnPtero(false,g.director)
        }
        if(elapsed==16+waits[stage]*60) {
            elapsed=0;stage++
            if(stage==waits.size) complete=true
        }
    }
    private fun enemy(g:Game,species:Int,x:Int,y:Int):Bird {
        val b=Bird(-1,species,x,y)
        b.mode=BirdMode.AIR;b.collidable=true;b.setPose(8)
        g.scheduler.insert(g.director,b);g.enemyCount++;g.unresolved++
        return b
    }
    fun input(g:Game,b:Bird):ControllerState.Sample {
        fun switches(direction:Int=0,flap:Boolean=false)=ControllerState.Sample(direction<0,direction>0,flap)
        when(b.ai) {
            "LESSON_FLY","LESSON_LEVEL" -> {
                if(b.ai=="LESSON_FLY") {
                    b.aiTimer=(b.aiTimer-1).toByte().toInt()
                    if(b.aiTimer>=0) return switches()
                    b.aiTimer=0
                    if(b.vy< -64) return switches()
                } else if(b.vy<0) return switches()
                if(b.y<=120) { b.ai="LESSON_LEVEL";return switches() }
                b.ai="LESSON_FLAP";b.aiTimer=(b.budget shr 2)+1
                return switches(flap=true)
            }
            "LESSON_FLAP" -> {
                if(--b.aiTimer>0) return switches(flap=true)
                b.budget=b.budget shr 1;b.aiTimer=b.budget;b.ai="LESSON_FLY"
                return switches()
            }
            "LESSON_JOUST" -> {
                if(b.aiTimer>0) { b.aiTimer--;if(b.q==0) return switches(1) }
                else if(b.q!=0 && b.facing>0) return switches(-1)
            }
            "LESSON_EGG" -> {
                if(b.mode==BirdMode.AIR) return if(b.q<=0) switches(1,true) else switches()
                if(b.q== -8) b.ai="LESSON_BRAKE"
                return switches(-1)
            }
            "LESSON_BRAKE" -> {
                if(b.q<0) return switches(-1)
                b.ai="LESSON_STOP"
            }
        }
        if(b.ai=="LESSON_STOP" && (b.q!=0 || b.facing>0)) return switches(-1)
        g.scheduler.actors().filterIsInstance<Pterodactyl>().firstOrNull { it.collidable }?.let { b.facing=it.facing }
        return switches()
    }
}
