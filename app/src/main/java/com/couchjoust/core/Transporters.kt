package com.couchjoust.core

fun Game.tryTransporter(b:Bird) {
    val queue=if(b.owner>=0) playerQueue else enemyQueue
    if(queue.first()!==b || b.owner<0 && playerQueue.isNotEmpty()) return
    val blocked=BooleanArray(4) { i -> terrain.occupied[i] || !terrain.support(terrain.transporters[i][0],terrain.transporters[i][1]) }
    val active=scheduler.actors().filter { a ->
        when(a) {
            is Bird -> a.mode in listOf(BirdMode.GROUND,BirdMode.AIR,BirdMode.DEPART,BirdMode.SINK,BirdMode.MATERIALIZE,BirdMode.PROTECTED)
            is Egg -> a.bird!=null && a.state!=4
            is Pterodactyl -> a.collidable || a.departing
            else -> false
        }
    }
    var chosen:Int?=null
    if(b.owner>=0) {
        val area=BooleanArray(3)
        for(a in active) area[when { a.y<81->0;a.y<169->1;else->2 }]=true
        chosen=when {
            !area[2] && !blocked[3] ->3
            !area[1] && !blocked[1] ->1
            !area[1] && !blocked[2] ->2
            !area[0] && !blocked[0] ->0
            else ->null
        }
    }
    if(chosen==null) {
        val wide=b.owner>=0 && wavesEntered<=13
        val bounds=if(wide) arrayOf(intArrayOf(16,210,0),intArrayOf(134,36,1),intArrayOf(218,120,1),intArrayOf(30,224,0))
            else arrayOf(intArrayOf(40,186,0),intArrayOf(158,277,0),intArrayOf(269,96,1),intArrayOf(54,200,0))
        val speeds=intArrayOf(-60,-30,-15,-8,0,8,15,30,60)
        for(a in active) {
            var x=a.x+speeds[(a.q+8)/2]
            if(x< -10) x+=302
            if(x>292) x-=302
            val levels=when { a.y<=88 ->intArrayOf(0);a.y<=145 ->intArrayOf(1,2);a.y>=170 ->intArrayOf(3);else ->intArrayOf() }
            for(i in levels) {
                val bound=bounds[i]
                if(if(bound[2]==0) x in bound[0]..bound[1] else x>bound[0] || x<=bound[1]) blocked[i]=true
            }
        }
        val r=random.next()
        val first=if(r.bit==0) (r.sevenBit and 1) else 2+(r.sevenBit and 1)
        for(i in (first..3)+(0..3)) if(!blocked[i]) { chosen=i;break }
    }
    if(chosen==null) return
    queue.removeAt(0)
    val position=terrain.transporters[chosen]
    b.transporter=chosen;terrain.occupied[chosen]=true;b.x=position[0];b.yQ=position[1]*256
    b.mode=BirdMode.MATERIALIZE;b.stateTimer=30;b.ridden=true;b.sinkDeath=false;b.facing=if(chosen==1) -1 else 1;b.setPose(1)
    sound(when(b.owner) { 0->"SNPCR1";1->"SNPCR2";else->"SNECRE" })
}
