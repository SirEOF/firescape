package org.firescape.server.entityhandling.defs.extras;

import org.firescape.server.entityhandling.defs.EntityDef;

public class ItemDropDef {

  public int id;
  public int amount;
  public int weight;

  public int getID() {
    return id;
  }

  public int getAmount() {
    return amount;
  }

  public int getWeight() {
    return weight;
  }
}