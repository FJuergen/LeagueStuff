package main.java.core;


import main.java.util.DBManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {

    static final DBManager db = new DBManager();

    public static void main(String[] args) throws InterruptedException {
        Map<Integer, String> champions = LeagueAPI.getChampionIDList();
        HashMap<Integer, Double> winrates = db.getChampWinrate(new ArrayList<>(champions.keySet()));
        winrates.forEach((key, value) -> System.out.println(champions.get(key) + ": " + value));
    }
}
