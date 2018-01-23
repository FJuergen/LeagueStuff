package main.java.util;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class DBManager {
    private static final String connectionURL = "jdbc:derby:leagueDB";
    private Connection connection = null;


    public static void main(String[] args) {
    }

    public DBManager() {
        System.setProperty("derby.system.home", "E:\\db-derby-10.13.1.1-bin\\bin");
        try {
            connection = DriverManager.getConnection(connectionURL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addPlayer(long playerID, String name, long checked) {
        try {
            PreparedStatement addPlayer = connection.prepareStatement("INSERT INTO player(playerid,name,checked) VALUES(?,?,?)");
            addPlayer.setLong(1, playerID);
            addPlayer.setString(2, name);
            addPlayer.setLong(3, checked);
            addPlayer.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addGame(long gameID, long[] players, int winningTeam, String map, String gamemode, boolean checked, String patch, long[] champions) {
        try {
            PreparedStatement addGame = connection.prepareStatement("INSERT INTO games(gameid,players,winningteam,map,gamemode,checked,patch,champions) VALUES(?,?,?,?,?,?,?,?)");
            addGame.setLong(1, gameID);
            addGame.setString(2, Arrays.toString(players));
            addGame.setInt(3, winningTeam);
            addGame.setString(4, map);
            addGame.setString(5, gamemode);
            addGame.setBoolean(6, checked);
            addGame.setString(7, patch);
            addGame.setString(8, Arrays.toString(champions));
            addGame.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean checkForPlayer(long playerID) {
        try {
            PreparedStatement check = connection.prepareStatement("SELECT * FROM player WHERE PLAYERID = ?");
            check.setLong(1, playerID);
            return check.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean checkForGame(long gameID) {
        try {
            PreparedStatement check = connection.prepareStatement("SELECT * FROM games WHERE gameid = ?");
            check.setLong(1, gameID);
            return check.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setCheckedPlayer(long playerID) {
        try {
            PreparedStatement checkPlayer = connection.prepareStatement("UPDATE player SET checked = ? WHERE playerid = ?");
            checkPlayer.setLong(1, Instant.now().toEpochMilli());
            checkPlayer.setLong(2, playerID);
            checkPlayer.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Long> getOutOfDatePlayer() {
        List<Long> returnList = new ArrayList<>();
        try {
            PreparedStatement getPlayers = connection.prepareStatement("SELECT playerid FROM player WHERE checked<?");
            getPlayers.setLong(1, Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli());
            ResultSet resultSet = getPlayers.executeQuery();
            while (resultSet.next()) {
                returnList.add(resultSet.getLong(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return returnList;
    }

    public List<Long>[] getPrevGames(int gamemode) {
        List<Long>[] gameInfo = new List[2];
        gameInfo[0] = new ArrayList<>();
        gameInfo[1] = new ArrayList<>();

        try {
            PreparedStatement getGames = connection.prepareStatement("SELECT gameid,players FROM games WHERE gamemode = ?");
            getGames.setInt(1, gamemode);
            ResultSet result = getGames.executeQuery();
            while (result.next()) {
                gameInfo[0].add(result.getLong(1));
                Long[] test = DataHandler.longArrayFromString(result.getString(2));
                Collections.addAll(gameInfo[1], test);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return gameInfo;
    }

    public List<Long>[] getPrevGames() {
        List<Long>[] gameInfo = new List[2];
        gameInfo[0] = new ArrayList<>();
        gameInfo[1] = new ArrayList<>();

        try {
            PreparedStatement getGames = connection.prepareStatement("SELECT gameid,players FROM games");
            ResultSet result = getGames.executeQuery();
            while (result.next()) {
                gameInfo[0].add(result.getLong(1));
                Long[] test = DataHandler.longArrayFromString(result.getString(2));
                Collections.addAll(gameInfo[1], test);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return gameInfo;
    }

    public Instant playerLastChecked(long playerID) {
        try {
            PreparedStatement checkLast = connection.prepareStatement("SELECT checked FROM player WHERE playerid = ?");
            checkLast.setLong(1, playerID);
            ResultSet result = checkLast.executeQuery();
            if (result.next()) {
                return Instant.ofEpochMilli(result.getLong(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Instant.now();
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public HashMap<Integer, Double> getChampWinrate(List<Integer> championID) {
        HashMap<Integer, Double> winrate = new HashMap<>();
        championID.forEach(id -> winrate.put(id, 0D));
        HashMap<Integer, Long> wins = new HashMap<>();
        HashMap<Integer, Long> losses = new HashMap<>();
        try {
            PreparedStatement getWinrate = connection.prepareStatement("SELECT champions,winningteam FROM games");
            ResultSet result = getWinrate.executeQuery();
            while (result.next()) {
                int arrayCounter = 0;
                int[] champions = Arrays.stream(DataHandler.longArrayFromString(result.getString(1))).mapToInt(Long::intValue).toArray();
                for (int champion : champions) {
                    if ((arrayCounter < (champions.length / 2f) && result.getInt(2) == 0) || (arrayCounter >= (champions.length / 2f) && result.getInt(2) == 1)) {
                        if (wins.containsKey(champion)) {
                            long currentWins = wins.get(champion);
                            wins.put(champion, ++currentWins);
                        } else wins.put(champion, 1L);
                    } else {
                        if (losses.containsKey(champion)) {
                            long currentLosses = losses.get(champion);
                            losses.put(champion, ++currentLosses);
                        } else losses.put(champion, 1L);
                    }
                    arrayCounter++;
                }
            }
            winrate.forEach((key, value) -> winrate.put(key, (double) wins.get(key) / (wins.get(key) + losses.get(key))));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return winrate;
    }
}
