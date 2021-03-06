/*
 * Jacob_'s Capture the Flag for Minecraft Classic and ClassiCube
 * Copyright (c) 2010-2014 Jacob Morgan
 * Based on OpenCraft v0.2
 *
 * OpenCraft License
 *
 * Copyright (c) 2009 Graham Edgecombe, S�ren Enevoldsen and Brett Russell.
 * All rights reserved.
 *
 * Distribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Distributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *
 *     * Distributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *
 *     * Neither the name of the OpenCraft nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opencraft.server.model;

import java.util.concurrent.atomic.AtomicBoolean;

import org.opencraft.server.Configuration;
import org.opencraft.server.Server;
import org.opencraft.server.game.impl.TagGameMode;
import org.opencraft.server.game.impl.GameSettings;
import org.opencraft.server.net.ActionSender;
import org.opencraft.server.net.MinecraftSession;
import org.opencraft.server.net.PingList;
import org.opencraft.server.persistence.LoadPersistenceRequest;
import org.opencraft.server.persistence.SavePersistenceRequest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Represents a connected player.
 *
 * @author Graham Edgecombe
 */
public class Player extends Entity {

  public static short NAME_ID = 0;
  /** The player's session. */
  private final MinecraftSession session;
  /** The player's name. */
  private final String name;
  /** A map of attributes that can be attached to this player. */
  private final Map<String, Object> attributes = new HashMap<String, Object>();

  // There is literally no organization for these attributes, have fun!
  public short nameId;
  public boolean isNewPlayer = false;
  public boolean appendingChat = false;
  public String partialChatMessage = "";
  public String lastMessage;
  public String announcement = "";
  public long lastMessageTime;
  public long lastPacketTime;
  public int heldBlock = 0;
  public boolean joinedDuringTournamentMode;
  public boolean muted = false;
  public boolean frozen = false;
  public long moveTime = 0;
  public long respawnTime = 0;
  public int team = -1;
  public int outOfBoundsBlockChanges = 0;
  public int placeBlock = -1;
  public boolean placeSolid = false;
  public boolean isHidden = false;
  public boolean brush = false;
  public boolean hasVoted = false;
  public boolean hasNominated = false;
 public long lastBlockTimestamp;
  public int boxStartX = -1;
  public int boxStartY = -1;
  public int boxStartZ = -1;
  public int buildMode;
  public int headBlockType = 0;
  public Position headBlockPosition = null;
  public int currentRoundPoints = 0;
  private HashSet<String> ignorePlayers = new HashSet<String>();
  private ActionSender actionSender = null;
  private Player instance;
  private Thread followThread;
  private AtomicBoolean follow = new AtomicBoolean(false);
  public ChatMode chatMode = ChatMode.DEFAULT;
  public Player chatPlayer;
  public boolean sendCommandLog = false;
  public final PingList pingList = new PingList();
  private final PlayerUI ui;
  public Position safePosition = new Position(0, 0, 0);

  private int ammo;
  private int health;
  public boolean isDead = false;
  public boolean isReloading = false;
  public int reloadStep = 0;

  public Player(MinecraftSession session, String name) {
    this.session = session;
    this.name = name;
    instance = this;
    nameId = NAME_ID;
    NAME_ID++;
    if (NAME_ID == 256) {
      NAME_ID = 0;
    }
    ui = new PlayerUI(this);
    setAmmo(GameSettings.getInt("Ammo"));
    setHealth(GameSettings.getInt("Health"));
  }

  public boolean isVisible() {
    return !isHidden && team != -1 && health > 0;
  }

  public int getAmmo() {
    return ammo;
  }

  public void setAmmo(int value) {
    this.ammo = value;
    ui.setAmmo(value);
  }

  public int getHealth() {
    return health;
  }

  public void setHealth(int value) {
    this.health = value;
    ui.setHealth(value);
  }

  public static Player getPlayer(String name, ActionSender source) {
    Player player = null;
    for (Player p : World.getWorld().getPlayerList().getPlayers()) {
      if (p.getName().toLowerCase().equals(name.toLowerCase())) return p;
      if (p.getName().toLowerCase().contains(name.toLowerCase())) {
        if (player == null) player = p;
        else {
          player = null;
          if (source != null)
            source.sendChatMessage("- &e\"" + name + "\" matches multiple players.");
          break;
        }
      }
    }
    return player;
  }

  public static Player setAttributeFor(String name, String k, String v, ActionSender source) {
    Player player = getPlayer(name, source);
    if (player != null) {
      player.setAttribute(k, v);
      return player;
    } else {
      Player p = new Player(null, name);
      try {
        new LoadPersistenceRequest(p).perform();
        p.setAttribute(k, v);
        new SavePersistenceRequest(p).perform();
      } catch (Exception e) {
        source.sendChatMessage("- &eError setting attribute: " + e.toString());
        Server.log(e);
      }
      return null;
    }
  }

  public static String getAttributeFor(String name, String k, ActionSender source) {
    Player player = getPlayer(name, source);
    if (player != null) {
      if (player.getAttribute(k) == null) return null;
      else return player.getAttribute(k).toString();
    } else {
      Player p = new Player(null, name);
      try {
        new LoadPersistenceRequest(p).perform();
        if (p.getAttribute(k) == null) return null;
        else return p.getAttribute(k).toString();
      } catch (Exception e) {
        source.sendChatMessage("- &eError getting attribute: " + e.toString());
        Server.log(e);
      }
      return null;
    }
  }

  public void ignore(Player p) {
    if (p.isOp()) {
      getActionSender().sendChatMessage("- &eYou can't ignore operators.");
    } else {
      String name = p.name;
      if (!ignorePlayers.contains(name)) {
        ignorePlayers.add(name);
        getActionSender()
            .sendChatMessage(
                "- &eNow ignoring " + p.parseName() + ". Use this " + "command again to stop");
      } else {
        ignorePlayers.remove(name);
        getActionSender().sendChatMessage("- &eNo longer ignoring " + p.getColoredName());
      }
    }
  }

  public boolean isIgnored(Player p) {
    return ignorePlayers.contains(p.name);
  }

  public void follow(final Player p) {
    if (p == null && followThread != null) follow.set(false);
    else if (p != null) {
      if (followThread != null) {
        follow.set(false);
        followThread.interrupt();
        try {
          followThread.join(1);
        } catch (InterruptedException ex) {
          return;
        }
      }
      follow.set(true);
      followThread =
          new Thread(
              () -> {
                while (follow.get()) {
                  Position pos = p.getPosition();
                  Rotation r = p.getRotation();
                  getActionSender().sendTeleport(pos, r);
                  try {
                    Thread.sleep(1000);
                  } catch (InterruptedException ex) {
                  }
                }
              });
      followThread.start();
    }
  }

  public String getNameChar() {
    if (isOp()) {
      if (team == 0) return "&4";
      else if (team == 1) return "&1";
      else return "&8";
    } else {
      if (team == 0) return "&c";
      else if (team == 1) return "&9";
      else return "&7";
    }
  }

  public boolean isVIP() {
    return (!GameSettings.getBoolean("Tournament")
            && getAttribute("VIP") != null
            && getAttribute("VIP").equals("true"))
        || isOp();
  }

  public boolean isOp() {
    return (getAttribute("IsOperator") != null && getAttribute("IsOperator").equals("true"));
  }

  public String parseName() {
    return getNameChar() + name + "&e";
  }

  public void makeInvisible() {
    for (Player p : World.getWorld().getPlayerList().getPlayers()) {
      if (this != p) p.getActionSender().sendRemoveEntity(instance);
    }
  }

  public void makeVisible() {
    for (Player p : World.getWorld().getPlayerList().getPlayers()) {
      if (this != p) p.getActionSender().sendExtSpawn(instance);
    }
  }

  public void autoJoinTeam() {
    TagGameMode ctf = World.getWorld().getGameMode();
    String team;
    if (ctf.redPlayers > ctf.bluePlayers) team = "blue";
    else if (ctf.bluePlayers > ctf.redPlayers) team = "red";
    else {
      if (Math.random() < 0.5) team = "red";
      else team = "blue";
    }
    joinTeam(team);
  }

  public void joinTeam(String team) {
    joinTeam(team, true);
  }

  public void joinTeam(String team, boolean sendMessage) {
    if (this.team == -1 && !team.equals("spec")) {
      getActionSender()
          .sendChatMessage(
              "- &aThis map was contributed by: " + World.getWorld().getLevel().getCreator());
    }
    if (isHidden && !team.equals("spec")) {
      Server.log(getName() + " is now unhidden");
      makeVisible();
      getActionSender().sendChatMessage("- &eYou are now visible");
      isHidden = false;
    }
    Level l = World.getWorld().getLevel();
    TagGameMode ctf = World.getWorld().getGameMode();
    if (ctf.voting) return;
    if (this.team == 0) ctf.redPlayers--;
    else if (this.team == 1) ctf.bluePlayers--;
    int diff = ctf.redPlayers - ctf.bluePlayers;
    boolean unbalanced = false;
    if (!GameSettings.getBoolean("Tournament")) {
      if (diff >= 1 && team.equals("red")) unbalanced = true;
      else if (diff <= -1 && team.equals("blue")) unbalanced = true;
    }
    for (Player p : World.getWorld().getPlayerList().getPlayers()) {
      if (p != this) {
        p.getActionSender().sendRemovePlayer(this);
      }
    }
    boolean bad = false;
    if (team.equals("red")) {
      if (this.team == -1) makeVisible();
      if (unbalanced && ctf.redPlayers > ctf.bluePlayers) {
        ctf.bluePlayers++;
        this.team = 1;
        team = "blue";
        getActionSender().sendChatMessage("- Red team is full.");
      } else {
        ctf.redPlayers++;
        this.team = 0;
      }
    } else if (team.equals("blue")) {
      if (this.team == -1) makeVisible();
      if (unbalanced && ctf.bluePlayers > ctf.redPlayers) {
        ctf.redPlayers++;
        this.team = 0;
        team = "red";
        this.getActionSender().sendChatMessage("- Blue team is full.");
      } else {
        ctf.bluePlayers++;
        this.team = 1;
      }
    } else if (team.equals("spec")) {
      this.team = -1;
    } else {
      bad = true;
      getActionSender().sendChatMessage("- Unrecognized team!");
    }
    if (isVisible()) {
      for (Player p : World.getWorld().getPlayerList().getPlayers()) {
        p.getActionSender().sendAddPlayer(this, p == this);
      }
    }
    if (!bad) {
      if (sendMessage)
        World.getWorld().broadcast("- " + parseName() + " joined the " + team + " team");
      Position position = getTeamSpawn();
      getActionSender().sendTeleport(position, getTeamSpawnRotation());
      setPosition(position);
      session.getActionSender().sendHackControl(this.team == -1);
    }
    if (isNewPlayer) {
      setAttribute("rules", "true");
      isNewPlayer = false;
    }
  }

  public void incStat(String a) {
    if (getAttribute(a) == null) {
      setAttribute(a, 0);
    }
    setAttribute(a, (Integer) getAttribute(a) + 1);
  }

  public void addPoints(int n) {
    if (getAttribute("points") == null) {
      setAttribute("points", 0);
    }
    currentRoundPoints += n;
    setAttribute("points", (Integer) getAttribute("points") + n);
  }

  public int getPoints() {
    return (Integer) getAttribute("points");
  }

  public void setPoints(int n) {
    if (getAttribute("points") == null) {
      setAttribute("points", 0);
    }
    setAttribute("points", n);
  }

  public void kickForHacking() {
    getActionSender().sendLoginFailure("You were kicked for hacking!");
    getSession().close();
    World.getWorld().broadcast("- &e" + getName() + " was kicked for hacking!");
  }

  public void sendToTeamSpawn() {
    getActionSender().sendTeleport(getTeamSpawn(),new Rotation(team == 0 ? 64 : 192, 0));
  }

  public Position getTeamSpawn() {
    switch (team) {
      case 0:
        return World.getWorld().getLevel().redSpawnPosition;
      case 1:
        return World.getWorld().getLevel().blueSpawnPosition;
      case -1:
        return Math.random() < 0.5
            ? World.getWorld().getLevel().redSpawnPosition
            : World.getWorld().getLevel().blueSpawnPosition;
      default:
        return null;
    }
  }

  public Rotation getTeamSpawnRotation() {
    switch (team) {
      case 0:
        return World.getWorld().getLevel().redSpawnRotation;
      case 1:
        return World.getWorld().getLevel().blueSpawnRotation;
      case -1:
        return Math.random() < 0.5
            ? World.getWorld().getLevel().redSpawnRotation
            : World.getWorld().getLevel().blueSpawnRotation;
      default:
        return null;
    }
  }

  public String getSkinUrl() {
    switch (team) {
      case 0:
        return "http://buildism.net/mc/server/skin_red.png";
      case 1:
        return "http://buildism.net/mc/server/skin_blue.png";
      default:
        return null;
    }
  }

  /**
   * Sets an attribute of this player.
   *
   * @param name The name of the attribute.
   * @param value The value of the attribute.
   * @return The old value of the attribute, or <code>null</code> if there was no previous attribute
   *     with that name.
   */
  public Object setAttribute(String name, Object value) {
    return attributes.put(name, value);
  }

  /**
   * Gets an attribute.
   *
   * @param name The name of the attribute.
   * @return The attribute, or <code>null</code> if there is not an attribute with that name.
   */
  public Object getAttribute(String name) {
    return attributes.get(name);
  }

  /**
   * Checks if an attribute is set.
   *
   * @param name The name of the attribute.
   * @return <code>true</code> if set, <code>false</code> if not.
   */
  public boolean isAttributeSet(String name) {
    return attributes.containsKey(name);
  }

  /**
   * Removes an attribute.
   *
   * @param name The name of the attribute.
   * @return The old value of the attribute, or <code>null</code> if an attribute with that name did
   *     not exist.
   */
  public Object removeAttribute(String name) {
    return attributes.remove(name);
  }

  @Override
  public String getName() {
    return name;
  }

  public String getColoredName() {
    return getNameChar() + name;
  }

  public String getListName() {
    String listName = (getColoredName()+"    &f"+currentRoundPoints);
    return listName.substring(0, Math.min(64, listName.length()));
  }

  public String getTeamName() {
    if (team == 0) {
      return "&cRed";
    } else if (team == 1) {
      return "&9Blue";
    } else {
      return "&7Spectators";
    }
  }

  /**
   * Gets the player's session.
   *
   * @return The session.
   */
  public MinecraftSession getSession() {
    return session;
  }

  /**
   * Gets this player's action sender.
   *
   * @return The action sender.
   */
  public ActionSender getActionSender() {
    if (session != null) return session.getActionSender();
    else return actionSender;
  }

  public void setActionSender(ActionSender actionSender) {
    this.actionSender = actionSender;
  }

  /**
   * Gets the attributes map.
   *
   * @return The attributes map.
   */
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public void step(int ticks) {
    World world = World.getWorld();
    if (world.getPlayerList().size()
        >= Configuration.getConfiguration().getMaximumPlayers()
        && System.currentTimeMillis() - moveTime > 5 * 60 * 1000
        && moveTime != 0) {
      world.broadcast("- " + parseName() + " was kicked for being AFK");
      getActionSender().sendLoginFailure("You were kicked for being AFK");
      getSession().close();
      moveTime = System.currentTimeMillis();
    }

    if (headBlockType != 0) {
      Position blockPos = getPosition().toBlockPos();
      Position newPosition = new Position(blockPos.getX(), blockPos.getY(), blockPos.getZ() + 3);
      if (!newPosition.equals(headBlockPosition)) {
        if (headBlockPosition != null) {
          world.getLevel().setBlock(headBlockPosition, 0);
        }
        if (world.getLevel().getBlock(newPosition) == 0) {
          headBlockPosition = newPosition;
          world.getLevel().setBlock(headBlockPosition, headBlockType);
        } else {
          headBlockPosition = null;
        }
      }
    } else if (headBlockPosition != null) {
      world.getLevel().setBlock(headBlockPosition, 0);
    }

    if (isReloading && ticks % GameSettings.getInt("ReloadStep") == 0) {
      if (ammo == GameSettings.getInt("Ammo") || reloadStep == GameSettings.getInt("Ammo")) {
        isReloading = false;
      } else {
        setAmmo(ammo + 1);
        reloadStep++;
      }
    }

    Position blockPosition = getPosition().toBlockPos();
    if (health == 0 &&
        (blockPosition.equals(World.getWorld().getLevel().redSpawnPosition.toBlockPos())
            || blockPosition.equals(World.getWorld().getLevel().blueSpawnPosition.toBlockPos()))) {
      setHealth(GameSettings.getInt("Health"));
      setAmmo(GameSettings.getInt("Ammo"));
      makeVisible();
    }

    ui.step(ticks);
  }
}