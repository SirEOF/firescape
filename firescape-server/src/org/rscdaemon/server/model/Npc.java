package org.rscdaemon.server.model;

import java.util.Random;

import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.NPCDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemDropDef;
import org.rscdaemon.server.entityhandling.locs.NPCLoc;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.FightEvent;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.states.CombatState;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class Npc extends Mob {
  /**
   * Random
   */
  private Random r = new Random();

  public final int Rand(int low, int high) {
    return low + r.nextInt(high - low);
  }

  /**
   * World instance
   */
  private static final World world = World.getWorld();
  /**
   * The location of this npc
   */
  private NPCLoc loc;
  /**
   * The definition of this npc
   */
  private NPCDef def;
  /**
   * The npcs hitpoints
   */
  private int curHits;
  private int curAttack;
  private int curStrength;
  private int curDefense;
  /**
   * DelayedEvent used for unblocking an npc after set time
   */
  private DelayedEvent timeout = null;
  /**
   * The player currently blocking this npc
   */
  private Player blocker = null;
  /**
   * Should this npc respawn once it has been killed?
   **/
  private boolean shouldRespawn = true;

  public void setRespawn(boolean respawn) {
    shouldRespawn = respawn;
  }

  public void blockedBy(Player player) {
    blocker = player;
    player.setNpc(this);
    setBusy(true);
    timeout = new DelayedEvent(null, 15000) {
      public void run() {
        unblock();
        running = false;
      }
    };
    world.getDelayedEventHandler().add(timeout);
  }

  public void unblock() {
    if (blocker != null) {
      blocker.setNpc(null);
      blocker = null;
    }
    if (timeout == null) {
      return;
    }
    setBusy(false);
    timeout.stop();
    timeout = null;
  }

  public Npc(NPCLoc loc) {
    def = EntityHandler.getNpcDef(loc.getId());
    curHits = def.getHits();
    this.loc = loc;
    super.setID(loc.getId());
    super.setLocation(Point.location(loc.startX(), loc.startY()), true);
    super.setCombatLevel(Formulae.getCombatLevel(def.getAtt(), def.getDef(), def.getStr(), def.getHits(), 0, 0, 0));
  }

  public Npc(int id, int startX, int startY, int minX, int maxX, int minY, int maxY) {
    this(new NPCLoc(id, startX, startY, minX, maxX, minY, maxY));
  }

  public void remove() {
    if (!removed && shouldRespawn && def.respawnTime() > 0) {
      world.getDelayedEventHandler().add(new DelayedEvent(null, def.respawnTime() * 1000) {
        public void run() {
          world.registerNpc(new Npc(loc));
          running = false;
        }
      });
    }
    removed = true;
  }

  public void killedBy(Mob mob, boolean stake) {
    if (mob instanceof Player) {
      Player player = (Player) mob;
      player.getActionSender().sendSound("victory");
    }
    Mob opponent = super.getOpponent();
    if (opponent != null) {
      opponent.resetCombat(CombatState.WON);
    }
    resetCombat(CombatState.LOST);
    world.unregisterNpc(this);
    remove();

    Player owner = mob instanceof Player ? (Player) mob : null;
    ItemDropDef[] drops = def.getDrops();

    int total = 0;
    for (ItemDropDef drop : drops) {
      total += drop.getWeight();
    }

    int hit = DataConversions.random(0, total);
    total = 0;
    for (ItemDropDef drop : drops) {
      if (drop.getWeight() == 0) {
        world.registerItem(new Item(drop.getID(), getX(), getY(), drop.getAmount(), owner));
        continue;
      }
      if (hit >= total && hit < (total + drop.getWeight())) {
        world.registerItem(new Item(drop.getID(), getX(), getY(), drop.getAmount(), owner));
        break;
      }
      total += drop.getWeight();
    }
  }

  public int getCombatStyle() {
    return 0;
  }

  public int getWeaponPowerPoints() {
    return 1;
  }

  public int getWeaponAimPoints() {
    return 1;
  }

  public int getArmourPoints() {
    return 1;
  }

  public int getAttack() {
    return def.getAtt();
  }

  public int getDefense() {
    return def.getDef();
  }

  public int getStrength() {
    return def.getStr();
  }

  public int getHits() {
    return curHits;
  }

  public void setHits(int lvl) {
    if (lvl <= 0) {
      lvl = 0;
    }
    curHits = lvl;
  }

  public void setAttack(int lvl) {
    if (lvl <= 0) {
      lvl = 0;
    }
    curAttack = lvl;
  }

  public void setStrength(int lvl) {
    if (lvl <= 0) {
      lvl = 0;
    }
    curStrength = lvl;
  }

  public void setDefense(int lvl) {
    if (lvl <= 0) {
      lvl = 0;
    }
    curDefense = lvl;
  }

  private Player findVictim() {
    long now = System.currentTimeMillis();
    ActiveTile[][] tiles = getViewArea().getViewedArea(2, 2, 2, 2);
    for (int x = 0; x < tiles.length; x++) {
      for (int y = 0; y < tiles[x].length; y++) {
        ActiveTile t = tiles[x][y];
        if (t != null) {
          for (Player p : t.getPlayers()) {
            if (p.isBusy()
                || now - p.getCombatTimer() < (p.getCombatState() == CombatState.RUNNING
                    || p.getCombatState() == CombatState.WAITING ? 3000 : 500)
                || !p.nextTo(this)
                || !p.getLocation().inBounds(loc.minX - 4, loc.minY - 4, loc.maxX + 4, loc.maxY + 4)) {
              continue;
            }
            if (getLocation().inWilderness() || p.getCombatLevel() < (getCombatLevel() * 2) + 1) {
              return p;
            }
          }
        }
      }
    }
    return null;
  }

  public void updatePosition() {
    long now = System.currentTimeMillis();
    Player victim = null;
    if (!isBusy() && def.isAggressive() && now - getCombatTimer() > 3000 && (victim = findVictim()) != null) {
      resetPath();
      victim.resetPath();
      victim.resetAll();
      victim.setStatus(Action.FIGHTING_MOB);
      victim.getActionSender().sendMessage("You are under attack!");

      setLocation(victim.getLocation(), true);
      for (Player p : getViewArea().getPlayersInView()) {
        p.removeWatchedNpc(this);
      }

      victim.setBusy(true);
      victim.setSprite(9);
      victim.setOpponent(this);
      victim.setCombatTimer();

      setBusy(true);
      setSprite(8);
      setOpponent(victim);
      setCombatTimer();
      FightEvent fighting = new FightEvent(victim, this, true);
      fighting.setLastRun(0);
      world.getDelayedEventHandler().add(fighting);
    }
    if (now - lastMovement > 6000) {
      lastMovement = now;
      if (!isBusy() && finishedPath() && DataConversions.random(0, 2) == 1) {
        super.setPath(new Path(getX(), getY(), DataConversions.random(loc.minX(), loc.maxX()),
            DataConversions.random(loc.minY(), loc.maxY())));
      }
    }
    super.updatePosition();
  }

  public NPCLoc getLoc() {
    return loc;
  }

  public void moveNpc(Path path) {
    super.setPath(path);
    super.updatePosition();
  }

  public NPCDef getDef() {
    return EntityHandler.getNpcDef(getID());
  }

}
