package org.ekstazi.check;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.ekstazi.Config;

import java.nio.file.Paths;
import static java.util.Collections.reverseOrder;

public class HotFileHelper {

    public static final String DEP_HOTFILE = "dep";
    public static final String CHANGE_FRE_HOTFILE = "freq";
    public static final String SIZE_HOTFILE = "size";
    public static List<String> hotFiles;

    public static List<String> getHotFiles(String hotFileType, String percentage) {
        List<String> hotFiles = new ArrayList<>();
        if (hotFileType.equals(DEP_HOTFILE)) {
            File depsDir = new File(Config.ROOT_DIR_V);
            if (!depsDir.exists()){
                return hotFiles;
            }
            HashMap<String, Long> fileToDeps = new HashMap<>();
            try {
                // Find affected test classes.
                List<File> sortedFiles = AffectedChecker.getSortedFiles(depsDir);
                for (File file : sortedFiles){
                    if (file.isDirectory()){
                        continue;
                    }
                    List<String> lines = Files.readAllLines(file.toPath());
                    for (String line : lines){
                        if (!line.startsWith("file")){
                            continue;
                        }
                        String depClassName = line.split(" ")[0];
                        if (fileToDeps.containsKey(depClassName)){
                            fileToDeps.put(depClassName, fileToDeps.get(depClassName) + 1);
                        } else {
                            fileToDeps.put(depClassName, 1L);
                        } 
                    }
                }
                return sortAndExtract(fileToDeps, percentage);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else if (hotFileType.equals(CHANGE_FRE_HOTFILE)) {
            HashMap<String, Long> fileToFreq = new HashMap<>();
            int numOfCommits = 50;
            String command = "git log -" + String.valueOf(numOfCommits) + " --name-only --format=\"\"";
            Runtime r = Runtime.getRuntime();
            String[] commands = {"bash", "-c", command};
            try {
                Process p = r.exec(commands);
        
                p.waitFor();
                BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = "";
        
                while ((line = b.readLine()) != null) {
                    if (line.length() > 0 && line.endsWith(".java")) {
                        String[] lineArray = line.split("/");
                        String fileName = lineArray[lineArray.length - 1];
                        fileName = fileName.substring(0, fileName.length() - ".java".length());
                        if (fileToFreq.containsKey(fileName)) {
                            fileToFreq.put(fileName, fileToFreq.get(fileName) + 1);
                        } else {
                            fileToFreq.put(fileName, 1L);
                        }
                    }
                }
                b.close();
                return sortAndExtract(fileToFreq, percentage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (hotFileType.equals(SIZE_HOTFILE)) {
            HashMap<String, Long> fileToSize = new HashMap<>();
            // check all the classes
            try {
                Files.walk(Paths.get("."))
                        .sequential()
                        .filter(x -> !x.toFile().isDirectory())
                        .filter(x -> x.toFile().getAbsolutePath().endsWith(".class"))
                        .forEach(p -> {
                            File classFile = p.toFile();
                            if (classFile.isFile()) {
                                fileToSize.put("file:" + classFile.toPath().normalize().toAbsolutePath().toString(), classFile.length());
                            }
                        });
                return sortAndExtract(fileToSize, percentage);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return hotFiles;
    }

    public static List<String> sortAndExtract(Map<String, Long> fileToType, String percentage){
        List<String> hotFiles = new ArrayList<>();
        // sort the files by size
        HashMap<String, Long> sortedFileToSize = fileToType.entrySet().stream()
                .sorted(reverseOrder(Entry.comparingByValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
        // get the top percentage of the files
        double percentageDouble = Double.parseDouble(percentage)/100.0;
        int topSize = (int) (sortedFileToSize.size() * percentageDouble);
        for (int i = 0; i < topSize; i++) {
            hotFiles.add(sortedFileToSize.keySet().toArray()[i].toString());
        }    
        return hotFiles;
    }

    public static void main(String[] args) {
        hotFiles = getHotFiles(SIZE_HOTFILE, "50");
        hotFiles.forEach(System.out::println);
    }
}