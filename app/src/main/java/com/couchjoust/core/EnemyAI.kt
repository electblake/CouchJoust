package com.couchjoust.core

import kotlin.math.abs

fun brain(species:Int)=arrayOf("BOUNDR","B2UNDR","SHADOW")[species]

/** AI returns the same three switches as a human; all motion stays in Movement. */
fun Game.enemyInput(b:Bird):ControllerState.Sample {
    if(b.ai=="LESSON_ENEMY") {
        if(--b.aiTimer>0) return ControllerState.Sample(false,b.q!=2,false)
        b.ai=brain(b.species)
        return ControllerState.Sample(false,false,false)
    }
    if(b.replacement!=null) return seekEgg(b)
    fun input(flap:Boolean,direction:Int=b.facing)=ControllerState.Sample(direction<0,direction>0,flap)
    fun value(suffix:String)=difficulty.getValue((if(b.species==0) "BO" else if(b.species==1) "HU" else "SH")+suffix)
    fun setLevel(target:Bird?,trackPlayer:Boolean=false) {
        b.line=if(trackPlayer) target!!.y else b.y
        if(target!=null) b.targetQ=target.q
        b.aiTimer=value(if(trackPlayer) "UPTM" else "LETM")
        b.ai=if(trackPlayer) "PLAYER_LEVEL" else "LEVEL"
    }
    fun turnLikeTarget() {
        if(b.targetQ==b.q) {
            b.directionTimer=(b.directionTimer-1).toByte().toInt()
            if(b.directionTimer>=0) { b.directionTimer=0;b.facing=-b.facing }
        }
    }
    fun direction(flap:Boolean,avoid:Boolean=true,preserve:Boolean=false):ControllerState.Sample {
        val checkLava=if(b.species==2) b.ai in listOf("LEVEL","LEVEL_RELEASE") else b.ai in listOf("UP","UP_PULSE","DOWN","DOWN_PULSE")
        if(checkLava && b.y>=(if(b.species==2) 208 else 211) && b.vy>=0) {
            b.ai="LAVA_PULSE"
            return input(true)
        }
        if(b.species==0) return input(flap)
        if(avoid && b.q!=0) {
            val dx=if(b.q<0) -31 else 31
            val dy=GameMath.signed16(b.vy*8) shr 8
            if(terrain.solid(b.x+dx,b.y+dy)!=0) {
                b.facing=if(b.q<0) 1 else -1;b.ai="AVOID";b.aiTimer=if(b.species==1) 8 else value("CLTM")
                return input(true)
            }
        }
        if(b.bumpX!=0) b.facing=if(b.bumpX<0) -1 else 1
        return input(flap,if(preserve && b.q!=0) 0 else b.facing)
    }
    while(true) {
        when(b.ai) {
            "LINET" -> {
                if(pursuing<requestedPursuers) { pursuing++;b.pursuing=true;b.ai=brain(b.species);continue }
                val line=when { b.y<101 ->69;b.y<161 ->129;else ->208 }
                b.lavaTimer--
                val trollCheck=b.lavaTimer<=0 && b.previous is Troll
                if(b.lavaTimer<=0) b.lavaTimer=difficulty.getValue("LNTLAV")
                if(trollCheck || b.y>line && b.vy>=0) { b.ai="LNTOFP";return input(true) }
                return input(false)
            }
            "LNTOFP" -> { b.ai="LINET";return input(false) }
            "BOUNDR","B2UNDR","SHADOW" -> {
                b.lavaTimer--
                if(b.lavaTimer<=0) {
                    b.lavaTimer=difficulty.getValue("LNTLAV")
                    if(b.previous is Troll) {
                        if(b.species==2) { b.ai="SH_UP";return direction(false,false,true) }
                        b.ai="DOWN_PULSE";b.aiTimer=2;return direction(true)
                    }
                }
                val target=chooseTarget(b)
                if(target==null) { setLevel(null);continue }
                val dy=target.y-b.y
                if(dy>=value("DNRG")) {
                    if(b.species==2) {
                        b.ai="SH_DOWN_RELEASE"
                        return direction(b.y>=211 && b.q>=0,false,true)
                    }
                    b.excursion=value("DNDI")
                    b.ai=if(b.species==0) "DOWN" else "DOWN_PULSE"
                    if(b.species==1) return direction(false)
                    if(b.vy>=value("DNVY")) { b.ai="DOWN_PULSE";b.aiTimer=2;return direction(true) }
                    return direction(false)
                }
                if(dy<=value("UPRG")) {
                    if(terrain.solid(b.x,b.y-14)!=0) {
                        if(b.species==0) { setLevel(target);continue }
                        b.line=b.y;b.aiTimer=21;b.ai="OVERHEAD";continue
                    }
                    if(b.species==2) { b.ai="SH_UP";return direction(false,false,true) }
                    b.excursion=value("UPDI")
                    if(b.species==0) { b.ai="UP_PULSE";b.aiTimer=value("UPWD");return direction(true) }
                    b.ai="UP";b.aiTimer=value("UPWU");return direction(false)
                }
                setLevel(target,b.species==2);continue
            }
            "DOWN","DOWN_PULSE" -> {
                if(b.ai=="DOWN" && b.mode==BirdMode.GROUND) { b.ai=brain(b.species);continue }
                if(b.vy>=0) {
                    b.excursion=GameMath.signed16(b.excursion+b.vy)
                    if(b.excursion>=0) { b.ai=brain(b.species);continue }
                }
                if(b.ai=="DOWN_PULSE") {
                    if(--b.aiTimer>0) return direction(true)
                    b.ai="DOWN";return direction(false)
                }
                if(b.vy>=value("DNVY")) { b.ai="DOWN_PULSE";b.aiTimer=2;return direction(true) }
                if(b.y>=211 && b.vy>=0) { b.ai="LAVA_PULSE";return input(true) }
                return direction(false)
            }
            "UP","UP_PULSE" -> {
                if(b.vy<0) {
                    b.excursion=GameMath.signed16(b.excursion+b.vy)
                    if(b.excursion<0) { b.ai=brain(b.species);continue }
                }
                if(b.ai=="UP_PULSE") {
                    if(--b.aiTimer>0) return direction(true)
                    b.ai="UP";b.aiTimer=value("UPWU");return direction(false)
                }
                if(--b.aiTimer<=0) {
                    if(b.species==1 && b.vy<value("UPVY")) { b.aiTimer=1;return direction(false) }
                    b.ai="UP_PULSE";b.aiTimer=value("UPWD");return direction(true)
                }
                return direction(false)
            }
            "LEVEL","PLAYER_LEVEL" -> {
                if(--b.aiTimer<=0) { b.ai=brain(b.species);continue }
                val playerLevel=b.ai=="PLAYER_LEVEL"
                var flap=if(playerLevel) b.y>b.line else b.vy>=0 && b.line>=b.y
                if(b.y>=211 && (b.species==0 || b.vy>=0)) {
                    val target=chooseTarget(b)
                    val lure=if(b.species==0) 63 else 45
                    if(playerLevel || target==null || abs(target.x-b.x)>lure) flap=true
                }
                if(flap) b.ai=if(playerLevel) "PLAYER_RELEASE" else "LEVEL_RELEASE"
                turnLikeTarget()
                return direction(flap,!playerLevel)
            }
            "LEVEL_RELEASE","PLAYER_RELEASE" -> {
                val player=b.ai=="PLAYER_RELEASE"
                b.ai=if(player) "PLAYER_LEVEL" else "LEVEL"
                turnLikeTarget();return direction(false,!player)
            }
            "SH_UP" -> { b.ai="SHADOW";return direction(b.vy>=value("UPVY"),false,true) }
            "SH_DOWN_RELEASE" -> {
                b.ai="SHADOW"
                return direction(b.mode==BirdMode.GROUND && b.q!=0 && terrain.solid(b.x,b.y)!=0,false,true)
            }
            "OVERHEAD" -> {
                if(terrain.solid(b.x,b.y-14)==0) {
                    if(b.species==2) { b.ai="SH_UP";return direction(false,false,true) }
                    b.excursion=value("UPDI");b.ai="UP";b.aiTimer=value("UPWU");return direction(false)
                }
                if(--b.aiTimer<=0) { b.ai=brain(b.species);continue }
                val flap=b.vy>=64 && b.line>=b.y
                if(flap) b.ai="OVERHEAD_RELEASE"
                return direction(flap,false)
            }
            "OVERHEAD_RELEASE" -> { b.ai="OVERHEAD";return direction(false,false) }
            "AVOID" -> { if(--b.aiTimer<=0) { b.ai=brain(b.species);continue };return direction(false,false) }
            "LAVA_PULSE" -> { b.ai="LAVA_RELEASE";return input(false) }
            "LAVA_RELEASE" -> {
                if(b.y<211 || b.vy<0) { b.ai=brain(b.species);continue }
                b.ai="LAVA_PULSE";return input(true)
            }
        }
    }
}
