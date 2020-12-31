package org.ekstazi.changelevel;

import java.util.HashMap;
import java.util.Set;

public class Macros {
    static HashMap<String, String> projectList = new HashMap<>();
    static{
//        projectList.put("apache/commons-beanutils", "893b769d");
//        projectList.put("apache/commons-codec", "24059898");
//        projectList.put("apache/commons-compress", "dfa9ed37");
//        projectList.put("apache/commons-pool", "b6775ade");
        projectList.put("alibaba/fastjson", "4c516b12");
//        projectList.put("apache/commons-email", "4f45556f");
//        projectList.put("apache/commons-math", "649b134f");
    }
    static String home = System.getProperty("user.home");
    static String projectFolder = "projects/ctaxonomy";
    static String projectFolderPath = home + "/" + projectFolder;
    static String resultFolder = "results";
    static String resultFolderPath = projectFolderPath + "/" + resultFolder;
    static int numSHA = 50;
    static String SKIPS = " -Djacoco.skip -Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip " +
            "-Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip";

}