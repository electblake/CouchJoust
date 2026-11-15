package com.couchjoust.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CoreTest {
    @Test fun bridgeFlamesConsumeLandingAndSinkBeforePermanentFlames() {
        val g=Game();g.start(1);val t=g.terrain
        t.configure(Tables.waves[2],3);t.lavaY=224;t.lavaTarget=224
        repeat(2000) {t.tick(g)}
        assertEquals(45,t.burnLeft);assertEquals(230,t.burnRight)
        assertTrue(t.flames.none {it.visible})
        t.configure(Tables.waves[3],4)
        repeat(100) {t.tick(g)}
        assertEquals(listOf(20,262),t.flames.filter {it.visible}.map {it.x})
        assertTrue(t.flames.filter {it.visible}.all {it.y==223})
    }
    @Test fun scoreRolloverRetainsTenMillionMarkerAndCapsLives() {
        val g=Game();g.start(1);val p=g.players[0]
        p.score=99_999_950;p.nextLife=100_000_000;p.lives=255;p.reserves=254
        g.award(0,100)
        assertEquals(10_000_050L,p.score);assertEquals(10_020_000L,p.nextLife)
        assertEquals(255,p.lives);assertEquals(254,p.reserves)
    }
    @Test fun cooperationBonusIsAwardedOnceAtWaveTransition() {
        val g=Game();g.start(2);g.special="team_or_survival";g.teamWave=true
        g.completeWave();assertEquals(3000L,g.players[0].score);assertEquals(3000L,g.players[1].score)
        g.enterWave(true)
        assertEquals(3000L,g.players[0].score);assertEquals(3000L,g.players[1].score)
        assertTrue(g.waveResults.isEmpty())
    }
    @Test fun sourceRunMovementTrace() {
        compareSourceTrace("movement")
    }
    @Test fun sourceRunFlightTrace() {
        compareSourceTrace("flight")
    }
    @Test fun sourceRunSkidTrace() {
        compareSourceTrace("skid")
    }
    private fun compareSourceTrace(scenario:String) {
        val records=File("../artifacts/reference/player-$scenario.csv").readLines().drop(1)
        val g=Game();g.start(1);repeat(60) {g.step()}
        for((index,line) in records.withIndex()) {
            if(index>0) {
                val t=index-1
                if(scenario=="movement") {
                    g.controls[0].rightKey=t<40
                    g.controls[0].flapKey=t in 40..59
                } else if(scenario=="flight") {
                    g.controls[0].rightKey=t%80<40
                    g.controls[0].leftKey=t%80>=40
                    g.controls[0].flapKey=t%8<4
                } else {
                    g.controls[0].rightKey=t<55 || t in 120..174
                    g.controls[0].leftKey=t in 55..119
                    g.controls[0].flapKey=t in 220..229
                }
                g.step()
            }
            val values=line.split(',').map { it.toInt() }
            val b=g.players[0].bird!!
            assertEquals("source x tick $index",values[1],b.x)
            assertEquals("source yQ tick $index",values[2],b.yQ)
            assertEquals("source vy tick $index",values[3],b.vy)
            assertEquals("source q tick $index",values[4],b.q)
            assertEquals("source facing tick $index",if(values[10]==0) 1 else -1,b.facing)
        }
    }
    @Test fun arithmeticAndRandom() {
        for(t in 0..255) assertEquals((96*t)/256-96,GameMath.flapImpulseQ8(t))
        assertEquals(-10,GameMath.wrapMovedX(293));assertEquals(292,GameMath.wrapMovedX(-11))
        val r=JoustRandom(0x12,0x34)
        assertEquals(JoustRandom.Sample(25,0),r.next());assertEquals(9,r.high);assertEquals(26,r.low)
        val t=JoustRandom(0x12,0x34);t.perturb()
        assertEquals(JoustRandom.Sample(26,1),t.next());assertEquals(0x89,t.high);assertEquals(0x99,t.low)
        assertEquals(-512,GameMath.adjustDifficulty(-256,32,-512))
        assertEquals(-1024,GameMath.adjustDifficulty(-512,32,-1024))
    }
    @Test fun liveSchedulerInsertionAndByteWait() {
        val g=Game();val calls=mutableListOf<String>()
        val parent=object:Task(Category.PRIMARY) {
            override fun service(game:Game) {
                calls.add("parent")
                game.scheduler.insert(this,object:Task(Category.PRIMARY) {
                    override fun service(game:Game) { calls.add("child");game.scheduler.remove(this) }
                });delay=1
            }
        }
        g.scheduler.insert(g.scheduler.head,parent);g.scheduler.pass(g,Category.PRIMARY)
        assertEquals(listOf("parent","child"),calls)
        val secondary=object:Task(Category.SECONDARY) { override fun service(game:Game) { calls.add("zero");delay=1 } }
        secondary.delay=0;g.scheduler.insert(g.scheduler.head,secondary)
        repeat(255) { g.scheduler.pass(g,Category.SECONDARY) };assertFalse(calls.contains("zero"))
        g.scheduler.pass(g,Category.SECONDARY);assertEquals("zero",calls.last())
    }
    @Test fun controlsAndHeldFlap() {
        val g=Game();g.start(1);repeat(60) { g.step() }
        val b=g.players[0].bird!!
        g.controls[0].leftKey=true;g.controls[0].rightKey=true
        assertEquals(0,g.controls[0].sample().direction)
        g.controls[0].flapKey=true;g.step();assertEquals(-128,b.vy)
        repeat(5) { g.step() };assertEquals(-108,b.vy)
        g.controls[0].flapKey=false;g.step();assertEquals(-100,b.vy)
        g.controls[0].flapKey=true;g.step();assertEquals(-192,b.vy)
        g.controls[0].clearOnPauseOrDisconnect();assertEquals(ControllerState.Sample(false,false,false),g.controls[0].sample())
    }
    @Test fun takeoffAndCeiling() {
        val g=Game();val b=Bird(0,0,100,33)
        b.mode=BirdMode.AIR;b.yQ=33*256;b.vy=-260
        g.moveBird(b,ControllerState.Sample(false,false,true))
        assertEquals(32*256,b.yQ);assertTrue(b.vy>0);assertEquals(1,b.bumpY)
    }
    @Test fun deathAwardPreventsLastLifeGameOver() {
        val g=Game();g.start(1);repeat(60) {g.step()}
        val p=g.players[0];p.lives=1;p.reserves=0;p.score=19_975
        g.kill(p.bird!!,null)
        assertEquals(20_025,p.score);assertEquals(1,p.lives);assertEquals(1,p.reserves);assertFalse(g.gameOver)
    }
    @Test fun eggsKeepWaveAliveAndFourthDefeatAwards() {
        val g=Game();g.start(1);repeat(60) {g.step()}
        val enemy=Bird(-1,0,100,100);enemy.mode=BirdMode.AIR;enemy.collidable=true;enemy.budget=4
        g.scheduler.insert(g.director,enemy);g.unresolved=1;g.enemyCount=1
        g.kill(enemy,g.players[0].bird)
        assertEquals(1,g.unresolved);assertEquals(0,g.enemyCount)
        val egg=g.scheduler.actors().filterIsInstance<Egg>().single()
        assertEquals(1,egg.species);assertEquals(3,egg.budget)
        g.collect(egg,0);assertEquals(0,g.unresolved);assertEquals(1250,g.players[0].score)
        val last=Bird(-1,2,100,100);last.budget=1;g.scheduler.insert(g.director,last);g.enemyCount=1;g.unresolved=1
        g.kill(last,g.players[0].bird);assertEquals(0,g.unresolved);assertEquals(3750,g.players[0].score)
    }
    @Test fun eggChainAndGladiator() {
        assertEquals(listOf(250,500,750,1000,1000),(0..4).map { GameMath.collectEgg(it,false).points })
        val g=Game();g.start(2);repeat(60) {g.step()};g.gladiatorBounty=true
        g.kill(g.players[1].bird!!,g.players[0].bird)
        assertEquals(5000,g.players[0].score);assertEquals(50,g.players[1].score);assertFalse(g.gladiatorBounty)
    }
    @Test fun mouthBoundaries() {
        val b=Bird(0,0,100,100);val p=Pterodactyl(110,90,false);p.facing=-1;b.facing=1
        p.frame=0
        for(relative in 0..20) { b.yQ=(90+relative)*256;assertEquals(relative in 8..12,Collision.mouth(b,p)) }
        p.frame=4
        for(relative in 0..20) { b.yQ=(90+relative)*256;assertEquals(relative in 5..11,Collision.mouth(b,p)) }
        b.facing=-1;assertFalse(Collision.mouth(b,p))
    }
    @Test fun waveRecordsAndDifficulty() {
        assertEquals(90,Tables.waves.size);assertEquals(28,Tables.adjustments.size);assertEquals(69,Tables.eggPositions.size)
        assertEquals("egg",Tables.waves[4].special);assertEquals(6,Tables.waves[4].counts[0])
        val g=Game();g.start(1);g.enterWave(true);assertEquals(-256,g.difficulty["HUUPVY"])
        g.enterWave(true);assertEquals(-512,g.difficulty["HUUPVY"])
        g.entry=90;g.waveNumber=90;g.enterWave(true);assertEquals(81,g.entry);assertEquals(91,g.waveNumber)
    }
    @Test fun sourceSilhouetteDirectionalScan() {
        val a=arrayOf<IntArray?>(intArrayOf(0,2),intArrayOf(0,5))
        val b=arrayOf<IntArray?>(intArrayOf(4,6),intArrayOf(4,6))
        assertEquals(Collision.Hit(1,1),Collision.scan(a,0,0,b,0,0))
        assertNull(Collision.scan(arrayOf(intArrayOf(0,2),null),0,0,b,0,0))
        assertNotNull(Collision.scan(arrayOf(intArrayOf(0,4)),0,0,b,0,0))
    }
    @Test fun soundPrioritiesAndNaturalTail() {
        val commands=mutableListOf<Int>()
        val s=SoundSequencer(mapOf("low" to SoundEvent(1,listOf(SoundCommand(3,1))),"high" to SoundEvent(130,listOf(SoundCommand(4,2),SoundCommand(0,3),SoundCommand(63,1))))) { commands.add(it) }
        assertFalse(s.request("low",false));assertTrue(s.request("high",false));assertFalse(s.request("low",true))
        s.tick();s.tick();assertEquals(listOf(4),commands)
        repeat(4) {s.tick()};assertEquals(listOf(4,63),commands)
        s.tick();assertTrue(s.request("low",true));s.tick();assertEquals(3,commands.last())
    }
    @Test fun deterministicLongRun() {
        fun run():String {
            val g=Game();g.start(2)
            repeat(15_000) { t ->
                g.controls.forEachIndexed { i,c -> c.flapKey=t%14<4;c.rightKey=(t/240+i)%2==0;c.leftKey=!c.rightKey }
                g.step()
            }
            return g.snapshot()
        }
        assertEquals(run(),run())
    }
    @Test fun allWavesCompleteAndRepeatFinalTen() {
        val g=Game();g.start(1)
        var iterations=0
        while(g.wavesEntered<=91 && iterations<150_000) {
            g.players[0].bird!!.collidable=false
            g.step()
            if(iterations%20==0) {
                for(a in g.scheduler.actors()) {
                    if(a is Bird && a.owner<0 && a.collidable) g.kill(a,g.players[0].bird)
                    if(a is Egg && a.collidable) g.collect(a,0)
                }
            }
            iterations++
        }
        assertEquals("wave progression after $iterations ticks: ${g.snapshot()}",92,g.wavesEntered)
        assertEquals(82,g.entry)
    }
    @Test fun pterodactylSteersWithoutSkippingMovementAndDissolves() {
        val g=Game();g.start(1);repeat(60) {g.step()}
        val p=Pterodactyl(180,120,true);p.initialized=true;p.targetX=20;p.targetY=90;p.q=4;p.timer=10
        g.unresolved=1;g.baiters=1;g.scheduler.insert(g.director,p)
        g.servicePtero(p)
        assertEquals(3,p.state);assertEquals(1,p.delay);assertEquals(-64,p.vy)
        val y=p.yQ;g.servicePtero(p);assertEquals(y-64,p.yQ);assertEquals(10-1,p.timer)
        g.killPtero(p,g.players[0].bird!!);assertEquals(0,g.baiters);assertEquals(1000,g.players[0].score)
        repeat(85) {g.servicePtero(p)};assertFalse(p.removed)
        g.servicePtero(p);assertTrue(p.removed)
        assertEquals(1000,g.scheduler.actors().filterIsInstance<Popup>().single().points)
    }
    @Test fun protectedRespawnExpiresOrAcceptsHeldControl() {
        val g=Game();g.start(1);repeat(60) {g.step()}
        val b=g.players[0].bird!!
        g.targetSlots.clear();b.collidable=false;b.mode=BirdMode.QUEUED;g.playerQueue.add(b)
        g.tryTransporter(b);assertEquals(BirdMode.MATERIALIZE,b.mode)
        repeat(30) {g.serviceBird(b)};assertEquals(BirdMode.PROTECTED,b.mode)
        repeat(374) {g.serviceBird(b)};assertFalse(b.collidable)
        g.serviceBird(b);assertTrue(b.collidable);assertEquals(-1,b.transporter)
        g.targetSlots.clear();b.collidable=false;b.mode=BirdMode.QUEUED;g.playerQueue.add(b)
        g.tryTransporter(b);repeat(30) {g.serviceBird(b)}
        g.controls[0].flapKey=true;g.serviceBird(b)
        assertEquals(BirdMode.AIR,b.mode);assertEquals(-128,b.vy)
    }
    @Test fun soundRequestsAreQueuedAndOnlyWinnerReachesAudio() {
        val commands=mutableListOf<Int>()
        val s=SoundSequencer(mapOf("a" to SoundEvent(5,listOf(SoundCommand(1,4))),"b" to SoundEvent(6,listOf(SoundCommand(2,4))))) {commands.add(it)}
        s.request("a",true);s.request("b",true)
        assertTrue(commands.isEmpty());s.tick();assertEquals(listOf(2),commands)
    }
}
