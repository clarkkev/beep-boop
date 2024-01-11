package kc.mega.shared;

import kc.mega.aim.wave.AimWave;
import kc.mega.game.BattleField;
import kc.mega.game.BotState;
import kc.mega.game.GameState;
import kc.mega.game.HitRateTracker;
import kc.mega.game.PredictState;
import kc.mega.utils.MathUtils;
import kc.mega.utils.MovingAverage;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

import jk.math.FastTrig;
import robocode.AdvancedRobot;

/** Handles various modes and special cases that are turned on against certain enemies. */
public class Strategy {
  public final AdvancedRobot bot;
  public final GameState gs;
  public final WaveManager waveManager;
  public final MovingAverage mirrorDistance;
  public final MovingAverage ramScore;
  public int antiRamCount = 0;
  public int nonHeadOnHits = 0;

  public boolean walkingStickSmooth;  // looser wall smoothing so linear/circular targeters with
                                      // wall collision detection are more predictable
  public boolean antiRam;  // avoid a rambot (https://robowiki.net/wiki/Ramming_Movement)
  public boolean antiMirror;  // trick mirror bots (https://robowiki.net/wiki/Mirror_Movement)
  public boolean antiBasicSurfer;  // use bullet powers that exploit a bug in BasicSurfer
  public boolean antiHOT = true;  // opponent is using head on targeting
  public boolean shield;  // use bullet shielding (https://robowiki.net/wiki/Bullet_Shielding)
  public boolean tryToDisable;  // disable rather than destroy the opponent so we can ram them
  public boolean ram;  // ram the enemy to get more bonus points

  public Strategy(AdvancedRobot bot, GameState gs, WaveManager waveManager, boolean shield) {
    this.bot = bot;
    this.gs = gs;
    this.waveManager = waveManager;
    this.shield = shield;
    this.mirrorDistance = new MovingAverage(0.998);
    this.ramScore = new MovingAverage(0.998);
  }

  public void strategize() {
    BotState myState = gs.myState, enemyState = gs.enemyState;

    walkingStickSmooth = (!antiHOT || bot.getRoundNum() == 0) &&
        gs.enemyHitRateTracker.hitRateInBounds(0, 0.03);

    tryToDisable = myState.energy > 3 && gs.enemyIsAlive && gs.enemyState.energy < 95 &&
        waveManager.movementWaves.noDangerousWavesExist(myState) &&
        enemyState.gunHeat < -BattleField.INSTANCE.getGunCoolingRate();
    boolean ramm = tryToDisable && enemyState.energy < 0.03;
    if (!ram && ramm && !shield && gs.enemyHistory.size() > 1) {
      System.out.println("BEEEEEEEEEEEEEEP!");
      bot.setRadarColor(Color.red);
    }
    ram = ramm;

    double distance = myState.distance(enemyState);
    double advHeading = -FastTrig.cos(enemyState.heading - myState.absoluteBearing(enemyState));
    double advVel = enemyState.velocity * advHeading;
    if (!ram && gs.gameTime > 30 && gs.enemyIsAlive) {
      ramScore.update(advVel <= 0 ? 0 : (distance < 70 ? 1 : Math.abs(advHeading)));
      antiRamCount += antiRam ? 1 : -1;
    }
    antiRam = !ram && (ramScore.get() > 0.7 || (distance < (Math.max(advVel, 0) + 8) * 15));
    antiBasicSurfer = gs.roundNum == 0 || gs.myHitRateTracker.getMaxHitRate() > 0.2;

    HitRateTracker enemyHRT = gs.enemyHitRateTracker;
    if (shield && (
        !Arrays.asList(SHIELDABLE).stream().anyMatch(s -> gs.enemyName.equals(s)) ||
        (gs.gameTime > 300 && enemyHRT.getShots() == 0) ||
        enemyHRT.getDamage() > 150)) {
      shield = false;
      gs.myHitRateTracker.clear();
      gs.enemyHitRateTracker.clear();
    }
    if (gs.gameTime > 30 && gs.enemyIsAlive) {
      mirrorDistance.update(enemyState.location.distance(
          BattleField.INSTANCE.mirrorLocation(myState.location)));
      boolean antiMirrorr = mirrorDistance.get() < 90;
      if (antiMirrorr && !antiMirror) {
        System.out.println("Anti-mirror on!");
      } else if (!antiMirrorr && antiMirror) {
        System.out.println("Anti-mirror off!");
      }
      antiMirror = antiMirrorr;
      walkingStickSmooth |= antiMirror;
    }
  }

  public void onNonHeadOnHit() {
    if (++nonHeadOnHits > (bot.getRoundNum() < 5 ? 0 : 1)) {
      antiHOT = false;
    }
  }

  public double getAntiMirrorDanger(List<PredictState> states, double bulletDanger) {
    if (!antiMirror || !gs.enemyIsAlive) {
      return 0;
    }
    int ticksUntilBreak = 0;
    int index = states.size() - 1;
    PredictState enemyState = null;
    AimWave w = null;
    for (int i = 0; i < 3; i++) {
      PredictState myState = states.get(index);
      enemyState = new PredictState(
          BattleField.INSTANCE.mirrorLocation(myState.location), 0, 0, myState.gameTime);
      if (w == null) {
        w = waveManager.aimWaves.closestWave(enemyState, true);
        if (w == null) {
          return 0;
        }
      }
      ticksUntilBreak = w.getTicksUntilBreak(enemyState);
      index = MathUtils.clip(index + ticksUntilBreak - 1, 1, states.size() - 1);
    }
    double bulletGF = w.getGF(w.bullet.heading - w.absoluteBearing);
    double enemyGF = w.getGF(enemyState.location);
    return 0.75 * Math.max(-bulletDanger, -Math.exp(-30 * Math.abs(bulletGF - enemyGF)) /
        (1 + Math.max(ticksUntilBreak, 0)));
  }

  // pre-computed list of bots that bullet shielding works against
  private static final String[] SHIELDABLE =
      ("EH.kms.LightningStorm 0.11B,ICS4U1.Patrick_White_Schrodinger 1.1,KiraNL.Cataris 1.0," +
       "KiraNL.ChupaLite 0.4,KiraNL.Chupacabra 0.5,Krabb.krabby.Krabby 1.18b," +
       "Noran.BitchingElk 0.054,Noran.RandomTargeting 0.02,PkKillers.PkAssassin 1.0," +
       "Polkwane.Intensive 1.0,SuperSample.SuperCrazy 1.0," +
       "WarfaJibril.Jibril_Warfa_Andromeda 1.11,ad.Quest 0.10,ags.micro.Carpet 1.1," +
       "ahf.r2d2.R2d2 0.86,ak.Fermat 2.0,alpha.BlackIce 1.0,amk.ChumbaWumba 0.3,apv.Aspid 1.7," +
       "apv.LauLectrik 1.2,apv.MicroAspid 1.8,apv.NanoLauLectrik 1.0," +
       "apv.NanoLauLectrikTheCannibal 1.1,apv.test.Virus 0.6.1,arthord.KostyaTszyu Beta2," +
       "ary.FourWD 1.3d,ary.Help 1.0,ary.mini.Nimi 1.0,ary.nano.ColorNanoP 1.1," +
       "asm.Statistas 0.1,axeBots.Musashi 2.18,bayen.UbaMicro 1.4,brainfade.Fallen 0.63," +
       "brainfade.melee.Dusk 0.44,buba.Archivist 0.1,bvh.fnr.Fenrir 0.36l," +
       "bvh.frg.Friga 0.112dev,bvh.mini.Fenrir 0.39,bvh.mini.Freya 0.55,casey.Flee 1.0," +
       "cbot.agile.Nibbler 0.2,cf.proto.Shiva 2.2,chase.pm.Pytko 1.0,cjm.Charo 1.1,cjm.Che 1.2," +
       "cjm.Chomsky 1.5,cjm.chalk.Chalk 2.6.Be,cli.WasteOfAmmo 1.0,codemojo.nano.Woot 1.0," +
       "conscience.Electron 1.3g,cs.s2.Seraphim 2.3.1,css.Delitioner 0.11,cuoq.Kakera 1.0," +
       "cw.megas.Blade 0.8,cx.CigaretBH 1.03,cx.Princess 1.0,cx.micro.Spark 0.6," +
       "cx.mini.BlackSwans 0.60,cx.mini.Cigaret 1.31,cx.mini.Nimrod 0.55," +
       "darkcanuck.Holden 1.13a,davidalves.Firebird 0.25,davidalves.Phoenix 1.02," +
       "davidalves.net.DuelistMicroMkII 1.1,davidalves.net.DuelistMini 1.1," +
       "de.erdega.robocode.Polyphemos 0.4,dft.Cyanide 1.90,dft.Cyprus 3.0,dft.Virgin 1.25," +
       "dmh.robocode.robot.BlackDeath 9.2,dmh.robocode.robot.YellowBird 0.12," +
       "dmp.micro.Aurora 1.41,dmp.nano.Eve 3.41,ds.OoV4 0.3b,dummy.micro.Sparrow 2.5," +
       "dz.Caedo 1.4,ej.ChocolateBar 1.1,ejbots.AngryGrandpappy 1.2,et.Predator 1.8," +
       "exauge.GateKeeper 1.1.121g,exauge.LemonDrop 1.6.130,fala.robocode.FalaRobot 1.0," +
       "fcr.First 1.0,florent.small.LittleAngel 1.8,fnc.bandit2002.Bandit2002 4.0.2," +
       "frag.FragBot 1.0,fromHell.C22H30N2O2S 2.2,gg.Wolverine 2.0,gh.GrubbmGrb 1.2.4," +
       "gh.micro.Grinnik 1.0,ha2.T3 0.1,ha2.T3 0.2,ins.MobyNano 0.8,intruder.PrairieWolf 2.61," +
       "jam.micro.RaikoMicro 1.44,jam.mini.Raiko 0.43,jasolo.Sonda 0.55," +
       "jaybot.adv.bots.JayBot 2.0,jbot.Rabbit2 1.1,jcs.Decepticon 2.5.3,jcs.Megatron 1.2," +
       "jekl.DarkHallow .90.9,jekl.Jekyl .70,jekl.mini.BlackPearl .91,jk.mini.CunobelinDC 1.2," +
       "jk.sheldor.nano.Yatagan 1.2.3,kawigi.f.FhqwhgadsMicro 1.0,kawigi.mini.Coriantumr 1.1," +
       "kawigi.mini.Fhqwhgads 1.1,kawigi.nano.FunkyChicken 1.1,kawigi.sbf.FloodMini 1.4," +
       "kawigi.sbf.FloodSonnet 0.9,kc.micro.Thorn 1.252,kc.micro.WaveShark 0.4," +
       "kc.mini.Vyper 0.311,kc.serpent.Hydra 0.21,kcn.unnamed.Unnamed 1.21,kid.Gladiator .7.2," +
       "kid.Toa .0.5,kinsen.nano.Charp 1.0,kinsen.nano.Quarrelet 1.0,kneels.nano.Derp 0.2," +
       "krzysiek.robbo2.Robbo 1.0.0,learn.ISmart 2.0s,lechu.Ala 0.0.4," +
       "lrem.magic.TormentedAngel Antiquitie,lrem.micro.FalseProphet Alpha," +
       "lrem.quickhack.QuickHack 1.0,lucasslf.Dodger 1.0,lucasslf.HariSeldon 0.2.1," +
       "lucasslf.Wiggins 0.6,m3thos.mini.Eva01 0.5.5,maribo.Omicron 1.0,mb.Monte 0.1.0," +
       "metal.small.MCool 1.21,metal.small.dna2.MCoolDNA 1.5,mk.Alpha 0.2.1,mladjo.Grrrrr 0.9," +
       "mladjo.Startko 1.0,mld.Moebius 2.9.3,mld.Wisdom 1.0,mnt.AHEB 0.6a,mnt.SurferBot 0.2.5," +
       "mrm.MightyMoose .2,myl.micro.Avipes 1.00,myl.micro.NekoNinja 1.30,mz.Adept 2.65," +
       "nat.Hikari dev0001,nat.nano.Ocnirp 1.73,nat.nano.OcnirpPM 1.0,nat.nano.OcnirpSNG 1.0b," +
       "ncj.MoxieBot 1.0,ngf.nano.Sparky 0.1.5,nkn.mini.Jskr0 0.1,nz.jdc.micro.HedgehogGF 1.5," +
       "nz.jdc.nano.NeophytePRAL 1.4,nz.jdc.nano.NeophytePattern 1.1," +
       "nz.jdc.nano.NeophyteSRAL 1.3,nz.jdc.nano.PatternAdept 1.0,nz.jdc.nano.PralDeGuerre 1.2," +
       "oog.micro.Claudius 1.11,oog.micro.MagicD3 0.41,oog.micro.SavantMicro 1.1," +
       "oog.mini.AlphaDragon 0.1,oog.nano.Fuatisha 1.1,origin.SleepSiphon 1.7b," +
       "ouroboros.Dragon 0.0.3,pa3k.Quark 1.02,pe.mini.SandboxMini 1.2," +
       "pe.minimelee.SandboxMiniMelee 1.1,pedersen.Hubris 2.4,pez.clean.Swiffer 0.2.9," +
       "pez.gloom.GloomyDark 0.9.2,pez.mako.Mako 1.5,pez.micro.Aristocles 0.3.7," +
       "pez.mini.Gouldingi 1.5,pez.mini.Pugilist 2.5.1f,pez.rumble.Ali 0.4.9," +
       "pez.rumble.CassiusClay 2rho.02no,pfvicm.Sobieski 7.2.3b,ph.micro.Pikeman 0.4.5," +
       "ph.mini.Archer 0.6.6,ph.musketeer.Musketeer 0.6,pkbots.BoyTDSurfer 1.0," +
       "pkdeken.Paladin 1.0,qwaker00.Ahchoo 1.6,racso.Crono 1.0,racso.Frog 0.9,rc.RCBot 2.0," +
       "rdt.AgentSmith.AgentSmith 0.5,rdt199.Warlord 0.73,reaper.Reaper 1.1," +
       "robar.micro.Gladius 1.15,robar.micro.Kirbyi 1.0,robar.micro.Topaz 0.25," +
       "robar.nano.BlackWidow 1.3,robar.nano.MosquitoPM 1.0,robar.nano.Prestige 1.0," +
       "robar.nano.Pugio 1.49,robar.nano.Scytodes 0.3,robar.nano.Vespa 0.95," +
       "rsim.mini.BulletCatcher 0.4,rsk1.RSK1 4.0,rtk.Tachikoma 1.0,ry.LightningBug 1.0," +
       "ry.VirtualGunExperiment 1.2.0,rz.Aleph 0.34,rz.Apollon 0.23,rz.GlowBlow 2.31," +
       "rz.GlowBlowAPM 1.0,rz.GlowBlowMelee 1.4,rz.SmallDevil 1.502," +
       "sheldor.micro.EpeeistMicro 2.1.0,sheldor.micro.FoilistMicro 1.2.0," +
       "sheldor.nano.Epeeist 1.1.0,sheldor.nano.Foilist 2.0.0,sheldor.nano.PointInLine 1.0," +
       "sheldor.nano.PointInLineRRAL 1.0.0,shinh.Entangled 0.3,simonton.GFNano_D 3.1b," +
       "simonton.beta.LifelongObsession 0.5.1,simonton.mega.SniperFrog 1.0.fix2," +
       "simonton.micro.GFMicro 1.0,simonton.mini.WeeksOnEnd 1.10.4," +
       "simonton.nano.WeekendObsession_S 1.7,slugzilla.Basilite 0.13,slugzilla.ButtHead 2.0," +
       "slugzilla.OrbitGF 1.0,slugzilla.OrbitLinear 1.1,slugzilla.OrbitPattern 1.1," +
       "slugzilla.OscillateGF 1.0,slugzilla.OscillatePattern 1.0,slugzilla.RandomGF 1.0," +
       "slugzilla.RandomPattern 1.0,slugzilla.SNGGF 1.0,slugzilla.SNGPattern 1.0," +
       "slugzilla.SquirmyToad 3.9,spinnercat.CopyKat 1.2.3,spinnercat.Kitten 1.6," +
       "starpkg.StarViewerZ 1.26,stefw.Tigger 0.0.23,stelo.MatchupMicro 1.2," +
       "stelo.MatchupWS 1.2c,stelo.PianistNano 1.3,stelo.SteloTestNano 1.0," +
       "stelo.UnfoolableNano 1.0,stelo.UntouchableNano 1.4,step.NanoBidu 1.0," +
       "suh.nano.AntiGravityL 1.01,suh.nano.RandomPM 1.02,supersample.SuperMercutio 1.0," +
       "syl.Centipede 0.5,synapse.rsim.GeomancyBS 0.11,tcf.Drifter 29,tcf.Repat3 2," +
       "theo.Hydrogen 2.1r,theo.QuarkSoup 1.5fga,theo.Tungsten 1.0a,theo.avenge.Pequod 1.0," +
       "theo.real.Ahab 1.0,theo.simple.Bones 1.0,theo.simple.Lucid 1.0,throxbot.ThroxBot 0.1," +
       "tide.pear.Pear 0.62.1,timmit.TimmiT 0.22,tkt.RedShift 1.1.CS.0,tobe.Saturn lambda," +
       "tobe.calypso.Calypso 4.1,trab.Crusader 0.1.7,trab.nano.AinippeNano 1.3," +
       "trm.Wrekt 1.1.6.f,tw.Exterminator 1.0,tzu.TheArtOfWar 1.2,vic.Locke 0.7.5.5," +
       "voidious.mini.Komarious 1.88,vuen.Fractal 0.55,wcsv.Engineer.Engineer 0.5.4," +
       "wcsv.PowerHouse.PowerHouse 1.7e3,wcsv.mega.PowerHouse2 0.2,whind.Constitution 0.7.1," +
       "whind.Strength 0.6.4,wiki.SuperSampleBot.SuperSittingDuck 1.0,wiki.Wolverine 2.1," +
       "wiki.mako.MakoHT 1.2.2.1,wiki.mini.BlackDestroyer 0.9.0,wiki.mini.Griffon 0.1," +
       "wiki.mini.Sedan 1.0,wiki.nano.RaikoNano 1.1,wilson.Chameleon 0.91," +
       "winamp32.micro.MicroMacro 1.0,wompi.Numbat 1.9,zen.Lindada 0.2,zeze2.OperatorZeze 1.05," +
       "zyx.micro.Ant 1.1,zzx.StormHead 1.0.1,kms.Golden 0.10,NDH.GuessFactor 1.0").split(",");
}
