package com.couchjoust.core

import kotlin.math.abs

fun Game.createWaveEgg(d:Director) {
    val slot:Int
    if(d.eggIndex<6) {
        var group=(random.next().sevenBit*12) shr 8
        while(group in d.usedGroups) group=if(group==0) 5 else group-1
        d.usedGroups.add(group)
        val bounds=Tables.eggGroups[group]
        slot=bounds[0]+((random.next().fullByte*(bounds[1]-bounds[0]+1)) shr 8)
    } else {
        var candidate=(random.next().sevenBit*138) shr 8
        while(candidate in d.usedSlots) candidate=(candidate+1)%69
        slot=candidate
    }
    d.usedSlots.add(slot)
    val counts=d.record.counts
    val species=if(counts[1]!=0) 1 else if(counts[0]!=0) 0 else 2
    hatchCap=counts[species]
    val pos=Tables.eggPositions[slot]
    val egg=Egg(pos[0],pos[1],species,4)
    egg.state=1;egg.bounced=true;egg.delay=12
    var wait=difficulty.getValue("EGGWT2")
    if(players.all { it.lives>0 }) wait=wait shr 1
    if(d.eggIndex<2) wait-=(random.next().sevenBit*wait) shr 8
    egg.timer=wait
    scheduler.insert(d,egg);unresolved++
}

fun Game.serviceEgg(e:Egg) {
    when(e.state) {
        0 -> {
            e.delay=enemyDelay
            e.vy=GameMath.signed16(e.vy+4)
            e.yQ+=e.vy+256*e.bumpY;e.bumpY=0
            if(e.y<=32) { if(e.vy<0) e.vy=-e.vy;e.yQ=32*256;e.bumpY=1 }
            e.horizontal()
            if(e.x>288) e.x-=284
            if(e.x<4) e.x+=284
            if(e.y>=227) { unresolved--;scheduler.remove(e);return }
            val landing=eggSupportMask(e.x,e.y)
            val level=if(landing!=0) terrain.landingLevel(landing) else -1
            if(level>=0) {
                e.bounced=true;e.yQ=level*256+(e.yQ and 255)
                if(e.q>0) e.q-=2 else if(e.q<0) e.q+=2
                if(e.vy>=0) e.vy=-(e.vy shr 2)
                if(e.vy>=-32 && e.q==0) { e.state=1;e.timer=difficulty.getValue("EGGWT");e.delay=12;e.frame=0 }
                sound("SNETHD")
            } else {
                val mask=terrain.solidX[e.x+32] and terrain.solidX[e.x+22] and Tables.background_y[e.y+6] and Tables.background_y[e.y+13]
                if(mask!=0) solidResponse(e,mask)
            }
            if(e.state==0) e.frame=intArrayOf(0,1,0,2)[((tick/4)%4).toInt()]
        }
        1 -> {
            e.delay=12
            if(eggSupportMask(e.x,e.y+1)==0) { e.state=0;e.vy=0;e.delay=enemyDelay;return }
            if(--e.timer==0) {
                if(enemyCount>=hatchCap) { e.timer=1;return }
                enemyCount++
                val fromLeft=e.x>151
                val b=Bird(-1,e.species,if(fromLeft) -9 else 291,when { e.y>187 -> 179;e.y>105 -> 104;else -> 69 })
                b.mode=BirdMode.AIR;b.ridden=false;b.collidable=false;b.facing=if(fromLeft) 1 else -1
                b.q=b.facing*8;b.budget=e.budget;b.replacement=e;b.ai="SEEKE";b.setPose(6)
                e.bird=b;scheduler.insert(e,b)
                e.state=2;e.hatchFrame=0;e.frame=1;e.delay=7
                sound("SNEGGH")
            }
        }
        2 -> {
            e.hatchFrame++
            val frames=intArrayOf(1,0,2,0,3,4,5,6)
            val waits=intArrayOf(7,3,7,67,7,7,7,7)
            if(e.hatchFrame==8) { e.state=3;e.frame=6;e.delay=8 }
            else { e.frame=frames[e.hatchFrame];e.delay=waits[e.hatchFrame] }
        }
        3 -> { e.delay=8 }
        4 -> scheduler.remove(e)
    }
}

private fun Game.eggSupportMask(x:Int,y:Int):Int =
    (terrain.landingX[x+32] or terrain.landingX[x+30]) and terrain.landingX[x+23] and Tables.landing_y[y] and 127

fun Game.seekEgg(b:Bird):ControllerState.Sample {
    val e=b.replacement!!
    fun input(dir:Int,flap:Boolean)=ControllerState.Sample(dir<0,dir>0,flap)
    if(b.ai=="REMOUNT") {
        if(--b.aiTimer==0) {
            e.collidable=false;scheduler.remove(e);b.replacement=null;b.ridden=true;b.collidable=true
            b.pursuing=true;pursuing++;b.ai=brain(b.species);sound("SNMOUN")
            return enemyInput(b)
        }
        return input(b.facing,false)
    }
    if(b.ai=="SEEKF2") {
        if(--b.aiTimer>0) return input(0,true)
        b.ai="SEEKE";return input(0,false)
    }
    var flap=false
    if(e.y!=b.y && (e.y<b.y || b.vy>=512 || terrain.solid(b.x+if(b.q<0) -18 else 18,b.y) and 8!=0) && b.vy>=-256) {
        b.ai="SEEKF2";b.aiTimer=4;flap=true
    }
    val delta=e.x-b.x
    if(delta in -8..11) {
        b.seekNear=true
        if(b.mode==BirdMode.GROUND) {
            // CMPB tests the low byte of delta+8, not the rider Y loaded into A.
            if(((delta+8) and 255)<=b.y) {
                if(b.q==0) {
                    b.ai="REMOUNT";b.aiTimer=5
                    return input(b.facing,false)
                }
                return input(if(b.q<0) 1 else -1,flap)
            }
            return input(0,flap)
        }
        return input(when { b.q==0->b.facing;b.q==2 || b.q== -2->0;b.q<0->1;else-> -1 },flap)
    }
    var dx=delta
    if(b.seekNear) {
        if(dx>=151) dx-=302
        else if(dx<= -151) dx+=302
    }
    val direction=if(dx<0) { if(b.q<0) 0 else -1 } else { if(b.q>0) 0 else 1 }
    return input(direction,flap)
}

fun Game.spawnPtero(baiter:Boolean,anchor:Task) {
    val left=random.next().bit==0
    val birds=targetSlots
    val y=when {
        birds.none { it.y>=169 } -> 209
        birds.none { it.y in 81..168 } -> 128
        else -> 61
    }
    val p=Pterodactyl(if(left) -9 else 291,y,baiter)
    p.facing=if(left) 1 else -1;p.q=2*p.facing
    scheduler.insert(anchor,p)
    if(baiter) baiters++
    sound("SNPTEI")
}

fun Game.servicePtero(p:Pterodactyl) {
    p.delay=1
    if(p.deathAge>=0) {
        p.deathAge++
        if(p.deathAge==36) p.facing=1
        if(p.deathAge<60) p.frame=(if(p.deathAge%8<4) 4 else 2)+if(p.facing<0) 1 else 0
        if(p.deathAge==86) {
            scheduler.insert(p,Popup(p.x,p.y,1000))
            scheduler.remove(p)
        }
        return
    }
    if(!p.collidable && !p.departing) {
        if(--p.attackTimer==0) scheduler.remove(p)
        return
    }
    if(p.departing && (p.x< -7 || p.x>289)) {
        p.departing=false;p.collidable=false;p.hidden=true;p.attackTimer=1;p.delay=60;return
    }
    if(p.initialized) {
        var fast=p.departing || p.state==2
        if(!p.departing && p.state==0) {
            if(--p.timer==0) {
                p.timer=p.interval;p.targetY=0
                val target=chooseTarget(p)
                if(target!=null) {
                    if(p.interval>15) p.interval--
                    p.targetX=target.x;p.targetY=minOf(210,target.y)-5+if(p.baiter) 2 else 0
                }
            }
            if(p.targetY!=0) {
                if(p.targetY<p.y) { if(p.vy> -192) p.vy-=64;p.state=3;p.cooldown=2 }
                else if(p.targetY-15>p.y) { if(p.vy<256) p.vy+=16;p.state=3;p.cooldown=2 }
                else { p.state=2;p.attackTimer=128;if(p.baiter) p.vy=p.vy shr 2;sound("SNPTE") }
            }
        } else if(!p.departing && p.state==3) {
            if(--p.cooldown==0) p.state=0
        }
        if(!p.departing && p.state==2) {
            fast=true
            if(p.baiter) p.timer=maxOf(1,p.timer-1)
            if(--p.attackTimer==0 || p.targetX-p.x in -13..12) { p.state=0;p.targetY=0 }
            else {
                if(p.frame/2==2) p.animationWait=10
                if(p.y>p.targetY && p.vy>=0) p.vy-=16
                else if(p.y<p.targetY && p.vy<256) p.vy+=16
                else if(p.y==p.targetY) p.vy=p.vy and 255
            }
        } else if(p.departing || p.targetY==0) {
            var line=when { p.y<101 -> 67;p.y<161 -> 127;else -> 206 }
            if(!p.departing && p.baiter && line==127 && (p.x<=165 || p.x>267)) line=136
            if(p.y>line && p.vy> -256) p.vy-=32
            else if(p.y<line && p.vy<256) p.vy+=32
            else if(p.y==line) p.vy=p.vy shr 2
        }
        val direction=if(p.bumpX!=0) { if(p.bumpX<0) -1 else 1 } else if(fast) p.facing else if(p.q<0) -1 else 1
        p.facing=direction;p.q=direction*if(fast) 8 else 4
    }
    p.initialized=true
    p.yQ+=p.vy+256*p.bumpY;p.bumpY=0
    if(p.y<=32) { p.yQ=32*256;p.bumpY=1;if(p.vy<0) p.vy=-p.vy }
    if(p.y>=210) { p.yQ=210*256+(p.yQ and 255);p.vy=0 }
    val speeds=intArrayOf(-768,-384,-192,-96,0,96,192,384,768)
    val total=p.remainder+speeds[(p.q+8)/2];p.remainder=total and 255;p.moveX(total shr 8)
    if(--p.animationWait<=0) { p.animation=(p.animation+3)%4;p.animationWait=8 }
    val pose=intArrayOf(0,1,2,1)[p.animation]
    p.frame=pose*2+if(p.facing<0) 1 else 0
    val mask=(terrain.solidX[p.x+32] or terrain.solidX[p.x+42]) and Tables.background_y[p.y+6] and Tables.background_y[p.y+7]
    if(mask!=0) solidResponse(p,mask)
    if((unresolved==0 || p.baiter && deathBaiterDelay>0) && !p.departing) {
        p.departing=true
        if(p.countedBaiter) { baiters--;p.countedBaiter=false }
    }
}

fun Game.killPtero(p:Pterodactyl,victor:Bird) {
    p.collidable=false;p.deathAge=0;p.facing=-p.facing
    p.frame=4+if(p.facing<0) 1 else 0
    if(p.countedBaiter) { baiters--;p.countedBaiter=false;enemyDelay=1 }
    award(victor.owner,1000);sound("SNPTED")
}

fun Game.serviceTroll(t:Troll) {
    t.delay=1
    val b=t.victim
    val valid=t.next===b && b.ridden && b.mode==BirdMode.AIR && b.y>=198 && (b.x<=42 || b.x>=242)
    if(!valid && !t.gripping) t.retracting=true
    if(t.retracting) {
        if(--t.timer<=0) {
            t.timer=5
            if(--t.stage<=0) { b.troll=null;troll=null;scheduler.remove(t);return }
        }
    } else if(t.gripping) {
        t.delay=2
        if(!valid) t.retracting=true
    } else {
        t.x=b.x-2
        if(t.stage<5) {
            if(--t.timer<=0) { t.stage++;t.timer=difficulty.getValue("LAVTIM") }
        } else {
            if(!t.tracking) {
                if(--t.timer>0) return
                t.tracking=true
            }
            if(t.y>b.y+3) t.yQ-=256
            else if(t.y<b.y+3) t.yQ+=256
            else {
                t.gripping=true;t.pull=difficulty.getValue("LAVGRA");t.delay=2
                scheduler.insert(b,TrollFront(t));sound("SNTROL")
            }
        }
    }
    t.frame=maxOf(0,t.stage-1)
}

fun Game.releaseTroll(t:Troll) {
    val b=t.victim
    b.yQ+=b.vy;b.troll=null;t.gripping=false;t.retracting=true;t.timer=5
    if(b.owner>=0 && b.ridden) award(b.owner,50)
}
