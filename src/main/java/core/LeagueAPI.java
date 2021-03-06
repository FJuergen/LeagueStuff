package main.java.core;

import com.google.gson.*;
import main.java.util.DBManager;
import main.java.util.DataHandler;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.UnresolvedAddressException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class LeagueAPI {
    private static final int API_REQUEST_DELTA_TIME = 1300;
    private static final String API_BASE = "https://euw1.api.riotgames.com";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static String APIKEY = "";
    private static Instant nextRequest = Instant.now();
    private static DBManager db = Main.db;
    private static List<String> unchecked = new ArrayList<>();
    private static List<String> checked = new ArrayList<>();
    private static List<Long> games = new ArrayList<>();
    private static List<Long> arams = new ArrayList<>();
    private static List<Long> checkedGames = new ArrayList<>();
    private static final String serializedFiles = "input/";

    static {
        try {
            APIKEY = Files.readAllLines(Paths.get(serializedFiles + "apikey.txt")).get(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        Instant saveFiles = Instant.now().plus(10, ChronoUnit.MINUTES);
        System.out.println("Started at " + Instant.now().toString());
        games.addAll(DataHandler.deserialize(serializedFiles + "games"));
        System.out.println("Loaded unchecked games");
        unchecked.addAll(DataHandler.deserialize(serializedFiles + "players"));
        System.out.println("Loaded unchecked players");
        List<Long>[] DBGames = db.getPrevGames();
        checkedGames.addAll(DBGames[0]);
        unchecked.addAll(db.getOutOfDatePlayer());
        unchecked.add("OMRdqGIUkwRbpMs_neMzILYiyrraV3ZH0ojn0dw1LMgW8A");
        while (unchecked.size() > 0 || games.size() > 0) {
            System.out.println("checked Players:" + checked.size() + ", unchecked players:" + unchecked.size() + " checked Games:" + checkedGames.size() + " unchecked Games:" + games.size());
            if (games.size() > 0) {
                if (checkedGames.contains(games.get(0))) {
                    games.remove(0);
                } else checkGame(games.get(0));

            }
            if (unchecked.size() > 0) {
                if (!db.checkForPlayer(unchecked.get(0))) {
                    checkPlayer(unchecked.get(0));
                } else unchecked.remove(0);
            }

            if (saveFiles.isBefore(Instant.now())) {
                DataHandler.serialize(games, serializedFiles + "games");
                DataHandler.serialize(unchecked, serializedFiles + "players");
                System.out.println("Saved " + games.size() + " Games and " + unchecked.size() + " Players at " + Instant.now().toString());
                saveFiles = Instant.now().plus(10, ChronoUnit.MINUTES);
            }
        }


    }

    private static void checkPlayer(String playerID) {
        JsonObject player;
        try {
            player = getJsonObject(API_BASE + "/lol/summoner/v4/summoners/by-account/" + playerID + "?api_key=" + APIKEY);
            try {
                if (!db.checkForPlayer(playerID))
                    db.addPlayer(player.get("accountId").getAsString(), player.get("name").getAsString(), Instant.now().toEpochMilli(),
                            player.get("id").getAsString(),player.get("puuid").getAsString(),player.get("summonerLevel").getAsInt(),
                            player.get("profileIconId").getAsInt());
                else db.setCheckedPlayer(player.get("accountId").getAsString());
            } catch (NullPointerException e) {
                System.err.println("Player not found! :" + playerID);
                unchecked.remove(playerID);
                return;
            }
            JsonObject matchList = getJsonObject(API_BASE + "/lol/match/v4/matchlists/by-account/" + playerID + "/?endIndex=20&api_key=" + APIKEY);
            try {
                StreamSupport.stream(matchList.get("matches").getAsJsonArray().spliterator(), false)
                        .forEach(node -> games.add(node.getAsJsonObject().get("gameId").getAsLong()));
            } catch (NullPointerException e) {
                System.err.println("Matchlist not found!:" + playerID);
                unchecked.remove(playerID);
                e.printStackTrace();
                return;
            }
            checked.add(playerID);
            unchecked.remove(playerID);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public static Map<Integer, String> getChampionIDList() throws InterruptedException {
        JsonObject champions = getJsonObject("http://ddragon.leagueoflegends.com/cdn/10.7.1/data/en_US/champion.json");
        assert champions != null;
        return champions.get("data").getAsJsonObject().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getValue().getAsJsonObject().get("id").getAsInt(), Map.Entry::getKey));
    }


    private static void checkGame(long gameID) {
        try {
            JsonObject game = getJsonObject(API_BASE + "/lol/match/v4/matches/" + gameID + "?api_key=" + APIKEY);
            List<String> players = new ArrayList<>();
            List<Long> champions = new ArrayList<>();
            try {
                StreamSupport.stream(game.get("participantIdentities").getAsJsonArray().spliterator(), false).forEach(entry -> players.add(entry.getAsJsonObject().getAsJsonObject().get("player").getAsJsonObject().get("accountId").getAsString()));
                StreamSupport.stream(game.get("participants").getAsJsonArray().spliterator(), false).forEach(entry -> champions.add(entry.getAsJsonObject().getAsJsonObject().get("championId").getAsLong()));
            } catch (NullPointerException e) {
                e.printStackTrace();
                System.err.println("SOMETHING WENT HORRIBLY WRONG WITH GAME:" + gameID);
                /*if(game.get("status").getAsJsonObject().get("status_code").getAsInt() == 403){
                    System.exit(0);
                }*/
                games.remove(gameID);
                return;
            }
            String[] playerArray = new String[players.size()];
            long[] championArray = new long[champions.size()];

            for (int i = 0; i < players.size(); i++) playerArray[i] = players.get(i);
            for (int i = 0; i < champions.size(); i++) championArray[i] = champions.get(i);
            int winningTeam = game.get("teams").getAsJsonArray().get(0).getAsJsonObject().get("win").getAsString().equals("Win") ? 0 : 1;
            if (!db.checkForGame(gameID))
                db.addGame(game.get("gameId").getAsLong(), playerArray, winningTeam, game.get("gameMode").getAsString(), game.get("queueId").getAsString(), true, game.get("gameVersion").getAsString(), championArray);
            for (String player : players) {
                if (!checked.contains(player)) unchecked.add(player);
            }
            if (games.contains(gameID)) games.remove(gameID);
            if (arams.contains(gameID)) arams.remove(gameID);
            checkedGames.add(gameID);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private synchronized static JsonObject getJsonObject(String url) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response;
        if (nextRequest.isAfter(Instant.now())) {
            Thread.sleep(nextRequest.toEpochMilli() - Instant.now().toEpochMilli());
        }
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            nextRequest = Instant.now().plusMillis(API_REQUEST_DELTA_TIME);
            return new JsonParser().parse(response.body()).getAsJsonObject();
        } catch (ConnectException e) {
            System.err.println("Connection reset maybe check something");
            getJsonObject(url);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.err.println(url);
        } catch (UnresolvedAddressException e) {
            System.out.println("adress could not be resolved:" + url);
        }
        return null;
    }

}
