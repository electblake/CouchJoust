package com.couchjoust.core

import kotlin.math.abs

class Game(val emitSound:(String,Boolean)->Unit = { _,_ -> }) {
    var scheduler=Scheduler()
    var terrain=Terrain()
    val random=JoustRandom(0x12,0x34)
    var players=arrayOf(Player(0),Player(1))
    val controls=arrayOf(ControllerState(),ControllerState())
    var active=false
    var demo=false
    var demonstration=Demonstration()
    var paused=false
    var gameOver=false
    var tick=0L
    var waveNumber=1
    var wavesEntered=1
    var entry=1
    var unresolved=0
    var enemyCount=0
    var pursuing=0
    var requestedPursuers=0
    var enemyDelay=2
    var hatchCap=255
    var baiters=0
    var deathBaiterDelay=0
    var troll:Troll?=null
    var gladiatorBounty=false
    var gladiatorWinner=-1
    var waveCompleted=false
    val waveResults=mutableListOf<WaveMessage>()
    var special="intro"
    var teamWave=false
    var overTimer=0
    val targetSlots=mutableListOf<Bird>()
    val playerQueue=mutableListOf<Bird>()
    val enemyQueue=mutableListOf<Bird>()
    val difficulty=Tables.adjustments.associate { it.name to it.start }.toMutableMap()
    val difficultyTimers=IntArray(28){2}
    var director=Director()
    var trace:((String)->Unit)?=null
    fun sound(name:String) { emitSound(name,active && !demo) }
    fun start(count:Int,attract:Boolean=false) {
        val playerCount=if(attract) 2 else count
        scheduler=Scheduler();terrain=Terrain();players=arrayOf(Player(0),Player(1))
        if(playerCount==1) { players[1].lives=0;players[1].reserves=0 }
        demonstration=Demonstration()
        if(attract) players.forEach { it.lives=3;it.reserves=3 }
        active=true;demo=attract;paused=false;gameOver=false;tick=0;waveNumber=1;entry=1;wavesEntered=1
        unresolved=0;enemyCount=0;pursuing=0;requestedPursuers=0;enemyDelay=2;hatchCap=255
        baiters=0;deathBaiterDelay=0;troll=null;targetSlots.clear();playerQueue.clear();enemyQueue.clear();gladiatorBounty=false;overTimer=0
        Tables.adjustments.forEachIndexed { i,a -> difficulty[a.name]=a.start;difficultyTimers[i]=2 }
        controls.forEach { it.clearOnPauseOrDisconnect() }
        director=Director()
        scheduler.insert(scheduler.head,director)
        scheduler.insert(scheduler.head,object:Task(Category.SECONDARY) {
            override fun service(game:Game) { game.opponentContacts();delay=1 }
        })
        for(i in 0 until playerCount) {
            val bird=Bird(i,0,if(i==0) 100 else 200,210)
            bird.facing=if(i==0) 1 else -1;bird.setPose(1);players[i].bird=bird
            scheduler.insert(scheduler.head,bird)
        }
        scheduler.insert(scheduler.head,object:Task(Category.PRIMARY) {
            override fun service(game:Game) {
                game.targetSlots.forEach { if(it.targetDelay>0) it.targetDelay-- }
                game.playerContacts();delay=1
            }
        })
        enterWave(false)
        if(attract) {
            terrain.solidX=terrain.solidX.map { it and 128 }.toIntArray()
            terrain.landingX=terrain.landingX.map { it and 32 }.toIntArray()
        }
        sound("SNGS")
    }
    fun step() {
        if(!active || paused) return
        tick++
        players.forEach { if(it.overMessage>0) it.overMessage-- }
        advanceRandomClock()
        terrain.tick(this)
        if(demo) demonstration.step(this)
        scheduler.pass(this,Category.PRIMARY)
        scheduler.pass(this,Category.SECONDARY)
        if(gameOver) overTimer++
        trace?.invoke(snapshot())
    }
    // Four scanline interrupts per 260-line frame, verified in source-run traces.
    fun advanceRandomClock() { repeat(4) { random.perturb() } }
    fun snapshot():String = buildString {
        append("$tick,$entry,$waveNumber,$unresolved,$enemyCount,${random.high},${random.low}")
        for(a in scheduler.actors()) append(";${a.javaClass.simpleName}:${a.x},${a.yQ},${a.vy},${a.q},${a.remainder},${a.collidable}")
        for(p in players) append(";P${p.index}:${p.score},${p.lives},${p.reserves},${p.chain}")
    }
    fun award(player:Int,points:Int) {
        val p=players[player]
        p.score+=points
        while(p.score>=p.nextLife) {
            p.nextLife+=20_000
            if(p.lives<255) { p.lives++;p.reserves++ }
            sound("SNREPL")
        }
        if(p.score>=100_000_000) { p.score=p.score%100_000_000+10_000_000;p.nextLife-=90_000_000 }
    }
    fun enterWave(advance:Boolean) {
        if(advance) {
            if(!waveCompleted) completeWave()
            entry=GameMath.nextWaveTableEntry(entry);waveNumber=(waveNumber+1)%100
            wavesEntered++
            Tables.adjustments.forEachIndexed { i,a ->
                if(difficultyTimers[i]>0) difficultyTimers[i]--
                if(difficultyTimers[i]==0) {
                    difficultyTimers[i]=a.period
                    difficulty[a.name]=GameMath.adjustDifficulty(difficulty.getValue(a.name),a.increment,a.end)
                }
            }
        }
        val record=Tables.waves[entry-1]
        waveCompleted=false;waveResults.clear();gladiatorWinner=-1
        special=record.special;teamWave=players.all { it.lives>0 }
        gladiatorBounty=special=="gladiator" && teamWave
        players.forEach { it.chain=0;it.deaths=0;it.kills=0 }
        requestedPursuers=if(record.early) record.pursuers else 0
        hatchCap=255;enemyDelay=if(entry<=2) 2 else 1
        for(name in record.disabled-terrain.disabled) sound("SNCLIF")
        terrain.configure(record,wavesEntered)
        director.begin(record)
        if(wavesEntered==1) director.timer+=40
    }
    fun completeWave() {
        waveCompleted=true
        if(special=="team_or_survival") {
            if(teamWave) {
                val cooperate=players.all { it.kills==0 }
                if(cooperate) players.filter { it.lives>0 }.forEach { award(it.index,3000) }
                waveResults.add(WaveMessage(if(cooperate) "MSW05" else "MSW04",if(cooperate) 22 else 52,182,1))
            } else {
                val survivor=players.firstOrNull { it.lives>0 }
                if(survivor!=null && survivor.deaths==0) {
                    award(survivor.index,3000)
                    waveResults.add(WaveMessage("MSW15",if(survivor.index==0) 16 else 126,182,if(survivor.index==0) 5 else 7))
                } else waveResults.add(WaveMessage("MSW16",76,182,1))
            }
        } else if(special=="gladiator" && teamWave) {
            if(gladiatorWinner<0) waveResults.add(WaveMessage("MSW11",100,182,1))
            else waveResults.add(WaveMessage("MSW12",if(gladiatorWinner==0) 32 else 158,182,if(gladiatorWinner==0) 5 else 7))
        }
    }
    fun serviceBird(b:Bird) {
        b.delay=if(b.owner>=0) 1 else enemyDelay
        when(b.mode) {
            BirdMode.INITIAL -> {
                if(--b.stateTimer==0) {
                    if(b.owner>=0) players[b.owner].reserves--
                    activate(b)
                }
            }
            BirdMode.QUEUED -> { b.delay=1;tryTransporter(b) }
            BirdMode.MATERIALIZE -> {
                b.delay=1
                if(--b.stateTimer==0) {
                    b.mode=BirdMode.PROTECTED;b.stateTimer=75;b.protectionPhase=16
                    b.protectionColorPhase=2;b.protectionColorWait=16
                }
            }
            BirdMode.PROTECTED -> {
                b.delay=1
                val input=inputFor(b)
                if(input.direction!=0 || input.flap) {
                    if(b.owner>=0) sound("SNPTREF")
                    terrain.occupied[b.transporter]=false;b.transporter=-1
                    activate(b);moveBird(b,input)
                } else if(--b.stateTimer==0) {
                    b.stateTimer=75;b.protectionPhase=b.protectionPhase shr 1
                    if(b.protectionPhase==0) { terrain.occupied[b.transporter]=false;b.transporter=-1;activate(b) }
                }
                if(b.mode==BirdMode.PROTECTED) {
                    val own=if(b.owner==0) 5 else if(b.owner==1) 7 else 1
                    val sequence=if(b.owner<0) intArrayOf(1,1,13,1,1,13,1,1) else intArrayOf(own,own,1,own,own,13,own,own)
                    var phase=b.protectionColorPhase
                    repeat(3) { i ->
                        phase=phase and 7
                        if(phase==0) { phase=2;b.protectionColorPhase=2 }
                        b.protectionColors[i]=sequence[phase];phase--
                    }
                    if(--b.protectionColorWait==0) { b.protectionColorPhase++;b.protectionColorWait=b.protectionPhase }
                }
            }
            BirdMode.GROUND,BirdMode.AIR -> moveBird(b,inputFor(b))
            BirdMode.DEPART -> {
                if(b.x < -7 || b.x>289) {
                    b.mode=BirdMode.RESPAWN;b.stateTimer=60;b.x=150;b.delay=1
                } else {
                    val line=when { b.y<101 -> 69; b.y<161 -> 129; else -> 208 }
                    val flap=b.y>line && b.vy>=0 && !b.flapHeld
                    val saved=b.mode
                    b.mode=BirdMode.AIR
                    moveBird(b,ControllerState.Sample(b.facing<0,b.facing>0,flap))
                    if(b.mode!=BirdMode.SINK) b.mode=saved
                }
            }
            BirdMode.RESPAWN -> {
                b.delay=1
                if(b.stateTimer>0) b.stateTimer--
                else if(b.owner<0 || players[b.owner].reserves==0 || gameOver) scheduler.remove(b)
                else {
                    players[b.owner].reserves--
                    playerQueue.add(b);b.mode=BirdMode.QUEUED
                }
            }
            BirdMode.SINK -> {
                b.delay=3
                if(!b.sinkDeath && b.y>=237) { b.sinkDeath=true;kill(b,null,true) }
                b.yQ+=256
                if(b.y>=244) { b.mode=BirdMode.RESPAWN;b.stateTimer=30;b.delay=1 }
            }
            BirdMode.GONE -> scheduler.remove(b)
        }
    }
    fun activate(b:Bird) {
        b.mode=BirdMode.GROUND;b.ground=1;b.vy=0;b.q=0;b.remainder=0
        b.acceleration=0;b.stride=0;b.runningFrame=0;b.bumpX=0;b.bumpY=0
        b.ridden=true;b.collidable=true;b.flapHeld=false;b.targetDelay=90;b.setPose(1)
        if(b.owner>=0) targetSlots.add(b)
    }
    fun inputFor(b:Bird):ControllerState.Sample {
        if(b.owner>=0 && !demo) return controls[b.owner].sample()
        if(b.owner>=0) return demonstration.input(this,b)
        return enemyInput(b)
    }
    fun beginSink(b:Bird) {
        b.mode=BirdMode.SINK;b.collidable=false;b.delay=3
        sound(if(b.owner>=0) "SNPLAV" else "SNELAV")
    }
    fun kill(victim:Bird,victor:Bird?,lava:Boolean=false) {
        victim.collidable=false;victim.ridden=false
        if(!lava) victim.mode=BirdMode.DEPART
        if(victim.owner>=0) {
            scheduler.insert(victim,Poof(victim.x,victim.y))
            val p=players[victim.owner]
            p.chain=0;p.deaths++;deathBaiterDelay=37
            targetSlots.remove(victim)
            if(victor!=null && victor.owner>=0) {
                players[victor.owner].kills++
                if(gladiatorBounty) {
                    award(victor.owner,3000);gladiatorBounty=false;gladiatorWinner=victor.owner
                    scheduler.insert(victim,Popup(victim.x+6,victim.y,3000));sound("SNBOUN")
                }
            }
            if(!lava && (victor==null || victor.owner<0) && wavesEntered<4) enemyDelay=2
            award(victim.owner,50);p.lives--
            if(p.lives==0 && !demo) {
                p.overMessage=176
                if(players.all { it.lives==0 }) { gameOver=true;overTimer=0 }
            }
            sound("SNPDIE")
        } else {
            enemyCount--;if(victim.pursuing) { pursuing--;victim.pursuing=false }
            val egg=Egg(victim.x+4,victim.y-8,minOf(2,victim.species+1),victim.budget-1)
            egg.vy=victim.vy;egg.q=victim.q
            scheduler.insert(victim,egg)
            if(egg.budget==0 && victor!=null && victor.owner>=0) collect(egg,victor.owner)
            sound("SNEDIE")
        }
        if(victor!=null && victor.owner>=0) award(victor.owner,if(victim.owner>=0) 2000 else intArrayOf(500,750,1500)[victim.species])
    }
    fun collect(egg:Egg,player:Int) {
        val p=players[player]
        val result=GameMath.collectEgg(p.chain,!egg.bounced)
        p.chain=result.newChain;award(player,result.basePoints)
        if(result.airBonusPoints!=0) award(player,result.airBonusPoints)
        unresolved--;egg.collidable=false;egg.state=4;egg.delay=4
        val popup=Popup(egg.x,egg.y,result.basePoints)
        scheduler.insert(egg,popup)
        if(result.airBonusPoints!=0) scheduler.insert(popup,Popup(egg.x,egg.y-9,500))
        egg.bird?.let { it.replacement=null;it.mode=BirdMode.DEPART;enemyCount-- }
        sound("SNEGG")
    }
    fun playerContacts() {
        var cursor=scheduler.head.next
        while(cursor!=null) {
            val a=cursor
            if(a is Bird && a.owner>=0 && a.collidable) {
                var other=a.next
                while(other!=null && a.collidable) {
                    val b=other
                    if(b is Actor && b.collidable && Collision.contact(a,b)) {
                        when(b) {
                            is Egg -> collect(b,a.owner)
                            is Pterodactyl -> {
                                if(Collision.mouth(a,b)) { killPtero(b,a);Collision.bounce(a,b) }
                                else { kill(a,null);Collision.pteroBird(b,a) }
                            }
                            is Bird -> {
                                val difference=a.y+a.lance-b.y-b.lance
                                if(difference<0) { kill(b,a);Collision.bounce(a,b) }
                                else if(difference>0) { kill(a,b);Collision.bounce(a,b) }
                                else { Collision.bounce(a,b);sound("SNPTHD");break }
                            }
                        }
                    }
                    other=b.next
                }
            }
            cursor=a.next
        }
    }
    fun opponentContacts() {
        var item=scheduler.head.next
        while(item!=null) {
            val a=item
            if(a is Actor && a.collidable && (a is Pterodactyl || a is Bird && a.owner<0)) {
                var next=a.next
                while(next!=null) {
                    val b=next
                    if(b is Actor && b.collidable && (b is Pterodactyl || b is Bird && b.owner<0) && Collision.contact(a,b)) {
                        if(a is Pterodactyl && b is Bird) Collision.pteroBird(a,b)
                        else if(a is Bird && b is Pterodactyl) Collision.pteroBird(b,a)
                        else Collision.bounce(a,b)
                        sound("SNETHD")
                    }
                    next=b.next
                }
            }
            item=a.next
        }
    }
    fun chooseTarget(self:Actor):Bird? {
        if(targetSlots.isEmpty()) return null
        val a=targetSlots[0]
        val b=targetSlots.getOrNull(1)
        if(a.targetDelay!=0) return b?.takeIf { it.targetDelay==0 }
        if(b==null || b.targetDelay!=0) return a
        fun metric(p:Bird):Int {
            val px=p.x and 65535;val sx=self.x and 65535
            val delta=(px-sx) and 65535
            return if(px>=sx) (-delta-2) and 65535 else delta
        }
        var first=(-abs(a.y-self.y)) and 255
        val mx=metric(a)
        if(mx<256 && mx<=first) first=mx
        return if(metric(b) and 255 <first) a else b
    }
}

class Director:Task(Category.SECONDARY) {
    var state=0
    var timer=180
    var spawnIndex=0
    var record=Tables.waves[0]
    var pending=emptyList<Int>()
    var baitIndex=13
    var baitTimer=450
    var aggression=112
    var eggIndex=0
    var pteros=0
    val usedSlots=mutableSetOf<Int>()
    val usedGroups=mutableSetOf<Int>()
    fun begin(wave:WaveRecord) {
        record=wave;state=0;timer=180;spawnIndex=0;eggIndex=0;pteros=wave.pteros
        pending=buildList { for(s in 0..2) repeat(wave.counts[s]) { add(s) } }
        usedSlots.clear();usedGroups.clear();delay=1
    }
    override fun service(game:Game) {
        delay=1
        if(game.gameOver || game.demo) return
        when(state) {
            0 -> if(--timer==0) {
                state=1;timer=61
                if(pteros>0) game.scheduler.insert(this,object:Task(Category.SECONDARY) {
                    var remaining=pteros
                    var started=false
                    override fun service(game:Game) {
                        if(!started) { started=true;delay=65;return }
                        game.spawnPtero(false,this)
                        if(--remaining==0) game.scheduler.remove(this) else delay=65
                    }
                })
            }
            1 -> {
                if(record.special=="egg") {
                    game.createWaveEgg(this)
                    if(++eggIndex==12) beginMonitor(game)
                } else if(--timer==0) {
                    if(spawnIndex<pending.size) {
                        val species=pending[spawnIndex++]
                        val b=Bird(-1,species,150,210);b.mode=BirdMode.QUEUED
                        b.setPose(1);game.enemyQueue.add(b)
                        game.scheduler.insert(this,b);game.unresolved++;game.enemyCount++
                        if(spawnIndex==pending.size) beginMonitor(game) else timer=61
                    }
                    else beginMonitor(game)
                }
            }
            2 -> {
                delay=8
                if(game.unresolved==0) {
                    game.completeWave();state=3;timer=if(game.waveResults.isEmpty()) 0 else 120;delay=1;return
                }
                if(game.deathBaiterDelay>0) game.deathBaiterDelay--
                else {
                    if(baitTimer>0) baitTimer--
                    if(baitTimer==0 && game.baiters<3) {
                        game.spawnPtero(true,this);baitIndex=maxOf(0,baitIndex-1);baitTimer=Tables.baitTimes[baitIndex]
                    }
                }
                if(game.enemyCount>0 && --aggression==0) { game.requestedPursuers=minOf(255,game.requestedPursuers+1);aggression=112 }
            }
            3 -> { if(timer>0) timer--;if(timer==0 && next==null) game.enterWave(true) }
        }
    }
    private fun beginMonitor(game:Game) {
        state=2;delay=8;game.requestedPursuers=record.pursuers
        baitIndex=when(game.entry) { 1->13;2->12;else->11 };baitTimer=Tables.baitTimes[baitIndex];aggression=112
    }
}

data class WaveMessage(val symbol:String,val x:Int,val y:Int,val color:Int)
