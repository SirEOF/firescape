package org.rscdaemon.server.packethandler.client;

import org.apache.mina.common.IoSession;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;

public class ChatHandler implements PacketHandler {
  /**
   * World instance
   */
  public static final World world = World.getWorld();

  public void handlePacket(Packet p, IoSession session) throws Exception {
    Player sender = (Player) session.getAttachment();
    if (sender.isMuted()) {
      sender.getActionSender().sendMessage("You are @red@MUTED@whi@. Nobody will see your messages.");
      return;
    }
    sender.addMessageToChatQueue(p.getData());
  }

}