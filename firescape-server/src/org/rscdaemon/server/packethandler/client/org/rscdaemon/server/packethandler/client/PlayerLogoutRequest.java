package org.rscdaemon.server.packethandler.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import org.apache.mina.common.IoSession;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;

public class PlayerLogoutRequest implements PacketHandler {
  /**
   * World instance
   */
  public static final World world = World.getWorld();

  // has logged
  public void handlePacket(Packet p, IoSession session) throws Exception {
    Player player = (Player) session.getAttachment();

    if (player.canLogout()) {
      // String user = player.getUsername().replaceAll(" ", "_").toLowerCase();
      if (!player.bad_login) {

        File f = new File("players/" + player.getUsername().replaceAll(" ", "_").toLowerCase() + ".cfg");
        System.out.println("blablalbalbablal" + player.getUsername().replaceAll(" ", " "));
        Properties pr = new Properties();

        FileInputStream fis = new FileInputStream(f);
        pr.load(fis);
        fis.close();

        FileOutputStream fos = new FileOutputStream(f);
        pr.setProperty("loggedin", "false");
        pr.store(fos, "Character Data.");
        fos.close();

        for (Player pla : world.getPlayers()) {
          if (pla.isFriendsWith(player.getUsername())) {
            pla.getActionSender().sendFriendUpdate(player.getUsernameHash(), 0);
          }
        }
      }
      player.destroy(true);

    } else {
      player.getActionSender().sendCantLogout();
    }
  }
}
