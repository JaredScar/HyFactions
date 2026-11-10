package com.jaredscarito.factions.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Faction {
    public String id;
    public String name;
    public String description = "";
    public String tag = "";
    public String title = "";
    public String announcement = "";
    public String kind = "";
    public String color = "#FFAA00";
    public double bank;
    public double powerBoost;
    public boolean open;
    public boolean peaceful;
    public boolean permanent;
    public boolean locked;
    public Boolean enemyEnter;
    public Home home;
    public Map<String, Member> members = new LinkedHashMap<>();
    public Map<String, String> relations = new LinkedHashMap<>();
    public List<String> allyRequests = new ArrayList<>();
    public List<String> truceRequests = new ArrayList<>();
    public List<String> bans = new ArrayList<>();
    public Map<String, String> banNames = new LinkedHashMap<>();
    public List<Warp> warps = new ArrayList<>();
}
