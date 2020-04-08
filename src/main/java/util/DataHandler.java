package main.java.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DataHandler {
    public static Long[] longArrayFromString(String in) {
        String[] inSplit = in.substring(1).substring(0, in.lastIndexOf("]") - 1).split(",");
        Long[] returnArray = new Long[inSplit.length];
        for (int i = 0; i < inSplit.length; i++) {
            returnArray[i] = Long.parseLong(inSplit[i].trim());
        }
        return returnArray;
    }
    public static String[] stringArrayFromString(String in) {
        String[] inSplit = in.substring(1).substring(0, in.lastIndexOf("]") - 1).split(",");
        String[] returnArray = new String[inSplit.length];
        for (int i = 0; i < inSplit.length; i++) {
            returnArray[i] = inSplit[i].trim();
        }
        return returnArray;
    }

    public static void serialize(Object target, String filePath) {
        try (FileOutputStream fos = new FileOutputStream(filePath); ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(target);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }


    public static List deserialize(String filePath) {
        ArrayList returnList = new ArrayList();
        try (FileInputStream fis = new FileInputStream(filePath); ObjectInputStream ois = new ObjectInputStream(fis)) {
            returnList = (ArrayList) ois.readObject();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (ClassNotFoundException c) {
            System.out.println("Class not found");
            c.printStackTrace();
        }
        return returnList;
    }
}
