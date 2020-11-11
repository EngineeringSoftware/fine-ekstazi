package org.ekstazi.changelevel;

import java.util.HashMap;
import java.util.Set;

public class Macros {
    static HashMap<String, String> projectList = new HashMap<>();
    static{
        projectList.put("apache/commons-beanutils", "37e9ee0a");
        projectList.put("apache/commons-codec", "24059898");
        projectList.put("apache/commons-compress", "dfa9ed37");
        projectList.put("apache/commons-pool", "b6775ade");
        projectList.put("alibaba/fastjson", "4c516b12");
    }
    static String projectFolder = "ekstazi_projects";
    static String projectFolderPath = System.getProperty("user.home") + "/" + projectFolder;
    static String resultFolder = "result";
    static String resultFolderPath = projectFolderPath + "/" + resultFolder;
    static int numSHA = 50;
    static String SKIPS = " -Djacoco.skip -Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip " +
            "-Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip";

}