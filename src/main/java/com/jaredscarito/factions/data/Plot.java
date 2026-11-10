package com.jaredscarito.factions.data;

import java.util.ArrayList;
import java.util.List;

public final class Plot {
    public String ownerId;
    public String ownerName;
    public boolean paid;
    public List<String> access = new ArrayList<>();
    public List<String> accessNames = new ArrayList<>();
}
