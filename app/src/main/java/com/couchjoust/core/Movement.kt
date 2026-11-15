package com.couchjoust.core

import kotlin.math.abs

fun Game.moveBird(b:Bird,input:ControllerState.Sample) {
    if(b.mode==BirdMode.GROUND) groundMotion(b,input) else airMotion(b,input)
}

private fun Game.groundMotion(b:Bird,input:ControllerState.Sample) {
    if(input.flap && !b.flapHeld) {
        b.remainder=b.stride and 255
        b.mode=BirdMode.AIR;b.vy=-128;b.yQ=(b.y-1)*256;b.bumpY=0
        b.flapHeld=true;b.flapTimer=0;b.wingHold=5;b.setPose(8)
        sound(if(b.owner>=0) "SNPLWD" else "SNELWD")
        return
    }
    b.flapHeld=input.flap
    if(b.acceleration>0) b.acceleration--
    if(b.acceleration==0) {
        val record=Tables.ground[b.ground]
        val next=when(input.direction*b.facing) { -1 -> record.reverse; 1 -> record.forward; else -> record.neutral }
        if(next!=b.ground) {
            b.ground=next;b.acceleration=8;b.stride=0
            if(next==11) sound(if(b.owner>=0) "SNPLS2" else "SNEMS2")
        }
    }
    val record=Tables.ground[b.ground]
    b.q=record.speed*b.facing
    b.stride--
    if(b.stride<=0) {
        b.stride=record.wait
        var dx=0
        when(record.routine) {
            "REVR" -> { b.facing=-b.facing;b.runningFrame=0;b.setPose(1) }
            "STANDR" -> { b.runningFrame=0;b.setPose(1) }
            "RUNR" -> {
                if(b.runningFrame<=0) b.runningFrame=4
                else {
                    b.runningFrame--
                    if(b.runningFrame==0) b.runningFrame=4
                    dx=when(b.runningFrame) { 4 -> 2; 3 -> 1; 2 -> 2; else -> 3 }
                }
                b.setPose(6-b.runningFrame)
                if(b.runningFrame==1) {
                    b.feet=b.feet xor 1
                    sound(if(b.owner>=0) "SNPRU${b.feet+1}" else "SNERU${b.feet+1}")
                }
            }
            "RUNSR" -> { dx=intArrayOf(0,0,2,4,1,1)[b.runningFrame+1];b.runningFrame=0;b.setPose(1) }
            "SKIDR" -> {
                if(b.runningFrame!=-1) sound(if(b.owner>=0) "SNPLSK" else "SNEMSK")
                b.runningFrame=-1;dx=2;b.setPose(0)
            }
        }
        b.moveX(dx*b.facing)
    } else if(b.bumpX!=0) b.moveX(0)
    if(!terrain.support(b.x,b.y)) {
        if(b.runningFrame<0) sound(if(b.owner>=0) "SNPFAL" else "SNEFAL")
        b.mode=BirdMode.AIR;b.remainder=0;b.vy=if(input.flap) 4 else 8;b.yQ=b.y*256+b.vy
        b.bumpY=0;b.wingHold=9;b.flapTimer=0;b.setPose(6)
        b.horizontal();birdTerrain(b)
        return
    }
    if(terrain.solid(b.x+6,b.y) and 8!=0) { b.facing=1;b.bumpX=abs(b.bumpX)+3 }
}

private fun Game.airMotion(b:Bird,input:ControllerState.Sample) {
    if(input.direction!=0) b.facing=input.direction
    b.flapTimer=minOf(255,b.flapTimer+1)
    val freshFlap=input.flap && !b.flapHeld
    val previousWingHold=b.wingHold
    if(freshFlap) {
        b.vy=GameMath.signed16(b.vy+GameMath.flapImpulseQ8(b.flapTimer))
        b.q=GameMath.horizontalIndexOnFlap(b.q,input.direction)
        b.flapTimer=0;b.wingHold=5
        sound(if(b.owner>=0) "SNPLWD" else "SNELWD")
    } else if(!input.flap && b.flapHeld) {
        b.flapTimer=0
        sound(if(b.owner>=0) "SNPLWU" else "SNELWU")
    }
    b.flapHeld=input.flap
    if(!freshFlap && b.wingHold>0) b.wingHold--
    b.setPose(if(freshFlap) { if(previousWingHold>0) 8 else 6 } else if(input.flap || previousWingHold>0) 6 else 8)
    val troll=b.troll
    if(troll!=null && troll.gripping) {
        b.q=0;b.bumpX=0;b.bumpY=0
        b.vy=GameMath.signed16(b.vy+troll.pull+if(input.flap) 0 else 4)
        if(b.vy < -384) { releaseTroll(troll);return }
        b.yQ+=b.vy
        if(b.y>=230) { beginSink(b);troll.retracting=true;return }
        b.x=b.x.coerceIn(8,278)
        if(b.x in 43..241) releaseTroll(troll)
        return
    }
    b.vy=GameMath.signed16(b.vy+4+if(input.flap) 0 else 4)
    var candidate=b.yQ+b.vy+256*b.bumpY
    b.bumpY=0
    if(candidate shr 8 <=32) {
        b.bumpY=1
        if(b.vy<0) b.vy=-b.vy
        candidate=32*256
    }
    if(candidate shr 8 >=230) { beginSink(b);return }
    b.yQ=candidate
    b.horizontal()
    birdTerrain(b)
}

fun Game.birdTerrain(b:Bird) {
    val mask=terrain.landing(b.x,b.y)
    if(mask!=0) {
        val level=terrain.landingLevel(mask)
        if(level>=0) {
            b.yQ=level*256+(b.yQ and 255);b.vy=0
            if(b.q!=0) b.facing=if(b.q<0) -1 else 1
            b.ground=abs(b.q)/2+1;b.acceleration=0;b.stride=0
            b.flapHeld=true;b.mode=BirdMode.GROUND;b.setPose(1)
            return
        }
        if(waveNumber>=4 && troll==null && b.ridden && b.mode==BirdMode.AIR) {
            val hand=Troll(b);troll=hand;b.troll=hand
            scheduler.insert(b.previous!!,hand)
        }
    }
    val solid=terrain.solid(b.x,b.y)
    if(solid==0) return
    solidResponse(b,solid)
}

fun Game.solidResponse(b:Actor,solid:Int) {
    val index=terrain.solidIndex(solid)
    val origins=arrayOf(intArrayOf(-32,69),intArrayOf(252,69),intArrayOf(86,81),intArrayOf(202,129),intArrayOf(-32,138),intArrayOf(202,129),intArrayOf(106,163),intArrayOf(54,211))
    val names=arrayOf("CCLF1L","CCLF1R","CCLF2","CCLF3R","CCLF3L","CCLF3R","CCLF4","CCLF5")
    val a=Tables.profiles.getValue(b.profile)
    val c=Tables.profiles.getValue(names[index])
    val origin=origins[index]
    val hit=Collision.scan(a,b.x,b.top,c,origin[0],origin[1]) ?: return
    val nearBottom=(1..4).any { hit.rowA+it>=a.size || a[hit.rowA+it]==null }
    val ends=hit.rowB+1>=c.size || c[hit.rowB+1]==null
    val ar=a[hit.rowA]!!;val cr=c[hit.rowB]!!
    val middle=cr[0]+cr[1]-ar[0]-ar[1]+2*(origin[0]-b.x)
    if((if(index==5) b.y<=147 else hit.rowB==0) && nearBottom) {
        b.bumpY=-2
        if(b.vy>=0) b.vy=(-b.vy-1) shr 1
        if(b.q==0) b.q=if(middle>=0) -2 else 2
    } else if(ends || (index==0 || index==4) && middle>=0 || (index==1 || index==3 || index==5) && middle<0) {
        b.bumpY=2
        if(b.vy<0) b.vy=(-b.vy) shr 1
    } else {
        val direction=if(index==0 || index==4 || middle<0) 1 else -1
        if(direction<0 && b.q>0) b.q=-b.q+2
        if(direction>0 && b.q<0) b.q=-b.q-2
        b.bumpX=b.q+direction*6
        b.vy=maxOf(-64,b.vy)
        b.bumpY=2
    }
    sound("SNCTHD")
}
