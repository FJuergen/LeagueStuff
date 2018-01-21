package main.java.core;

import com.google.gson.*;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import main.java.util.DBManager;
import main.java.util.DataHandler;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
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
    private static List<Long> unchecked = new ArrayList<>();
    private static List<Long> checked = new ArrayList<>();
    private static List<Long> rankedGames = new ArrayList<>();
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

    public static void main(String[] args) throws InterruptedException {

        Instant saveFiles = Instant.now().plus(10, ChronoUnit.MINUTES);

        rankedGames.addAll(DataHandler.deserialize(serializedFiles + "games"));
        System.out.println("Loaded unchecked games");
        unchecked.addAll(DataHandler.deserialize(serializedFiles + "players"));
        System.out.println("Loaded unchecked players");
        List<Long>[] DBGames = db.getPrevGames(420);
        checkedGames.addAll(DBGames[0]);
        unchecked.addAll(db.getOutOfDatePlayer());
        while (unchecked.size() > 0 || rankedGames.size() > 0) {
            System.out.println("checked Players:" + checked.size() + ", unchecked players:" + unchecked.size() + " checked Games:" + checkedGames.size() + " unchecked Games:" + rankedGames.size());
            if (rankedGames.size() > 0) {
                if (checkedGames.contains(rankedGames.get(0))) {
                    rankedGames.remove(0);
                } else checkGame(rankedGames.get(0));

            }
            if (unchecked.size() > 0) {
                if (!db.checkForPlayer(unchecked.get(0))) {
                    checkPlayer(unchecked.get(0));
                } else unchecked.remove(0);
            }

            if (saveFiles.isBefore(Instant.now())) {
                DataHandler.serialize(rankedGames, serializedFiles + "games");
                DataHandler.serialize(unchecked, serializedFiles + "players");
                System.out.println("Saved " + rankedGames.size() + " Games and " + unchecked.size() + " Players" + Instant.now().toString());
                saveFiles = Instant.now().plus(10, ChronoUnit.MINUTES);
            }
        }


    }

    private static void checkPlayer(long playerID) {
        JsonObject player;
        try {
            player = getJsonObject(API_BASE + "/lol/summoner/v3/summoners/by-account/" + playerID + "?api_key=" + APIKEY);
            try {
                if (!db.checkForPlayer(playerID))
                    db.addPlayer(player.get("accountId").getAsLong(), player.get("name").getAsString(), Instant.now().toEpochMilli());
                else db.setCheckedPlayer(player.get("accountId").getAsLong());
            } catch (NullPointerException e) {
                System.err.println("Player not found! :" + playerID);
                unchecked.remove(playerID);
                return;
            }
            JsonObject matchList = getJsonObject(API_BASE + "/lol/match/v3/matchlists/by-account/" + playerID + "/recent?api_key=" + APIKEY);
            try {
                StreamSupport.stream(matchList.get("matches").getAsJsonArray().spliterator(), false)
                        .filter(node -> node.getAsJsonObject().get("queue").getAsInt() == 420)
                        .forEach(node -> rankedGames.add(node.getAsJsonObject().get("gameId").getAsLong()));
                StreamSupport.stream(matchList.get("matches").getAsJsonArray().spliterator(), false)
                        .filter(node -> node.getAsJsonObject().get("queue").getAsInt() == 65)
                        .forEach(node -> arams.add(node.getAsJsonObject().get("gameId").getAsLong()));
            } catch (NullPointerException e) {
                System.err.println("Matchlist not found!:" + playerID);
                unchecked.remove(playerID);
                return;
            }
            checked.add(playerID);
            unchecked.remove(playerID);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public static Map<Integer, String> getChampionIDList() throws InterruptedException {
        JsonObject champions = getJsonObject(API_BASE + "/lol/static-data/v3/champions?api_key=" + APIKEY);
        assert champions != null;
        return champions.get("data").getAsJsonObject().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getValue().getAsJsonObject().get("id").getAsInt(), Map.Entry::getKey));
    }


    private static void checkGame(long gameID) {
        try {
            JsonObject game = getJsonObject(API_BASE + "/lol/match/v3/matches/" + gameID + "?api_key=" + APIKEY);
            List<Long> players = new ArrayList<>();
            List<Long> champions = new ArrayList<>();
            try {
                StreamSupport.stream(game.get("participantIdentities").getAsJsonArray().spliterator(), false).forEach(entry -> players.add(entry.getAsJsonObject().getAsJsonObject().get("player").getAsJsonObject().get("accountId").getAsLong()));
                StreamSupport.stream(game.get("participants").getAsJsonArray().spliterator(), false).forEach(entry -> champions.add(entry.getAsJsonObject().getAsJsonObject().get("championId").getAsLong()));
            } catch (NullPointerException e) {
                System.err.println("SOMETHING WENT HORRIBLY WRONG WITH GAME:" + gameID);
                rankedGames.remove(gameID);
                return;
            }
            long[] playerArray = new long[players.size()];
            long[] championArray = new long[champions.size()];

            for (int i = 0; i < players.size(); i++) playerArray[i] = players.get(i);
            for (int i = 0; i < champions.size(); i++) championArray[i] = champions.get(i);
            int winningTeam = game.get("teams").getAsJsonArray().get(0).getAsJsonObject().get("win").getAsString().equals("Win") ? 0 : 1;
            if (!db.checkForGame(gameID))
                db.addGame(game.get("gameId").getAsLong(), playerArray, winningTeam, game.get("gameMode").getAsString(), game.get("queueId").getAsString(), true, game.get("gameVersion").getAsString(), championArray);
            for (long player : players) {
                if (!checked.contains(player)) unchecked.add(player);
            }
            if (rankedGames.contains(gameID)) rankedGames.remove(gameID);
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
            response = client.send(request, HttpResponse.BodyHandler.asString());
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
