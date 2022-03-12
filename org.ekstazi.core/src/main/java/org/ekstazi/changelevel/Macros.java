package org.ekstazi.changelevel;

import java.util.List;
import java.util.ArrayList;

public class Macros {
    static List<String> projectList = new ArrayList<>();
    static {
        projectList.add("apache_commons-imaging");
        projectList.add("apache_commons-lang");
        projectList.add("apache_commons-collections");
        projectList.add("asterisk-java_asterisk-java");
        projectList.add("apache_commons-codec");
        projectList.add("apache_commons-configuration");
        projectList.add("apache_commons-compress");
        projectList.add("sonyxperiadev_gerrit-events");
        projectList.add("tabulapdf_tabula-java");
        projectList.add("alibaba_fastjson");
        projectList.add("apache_commons-math");
        projectList.add("apache_commons-net");
        projectList.add("apache_commons-beanutils");
        projectList.add("davidmoten_rxjava-extras");
        projectList.add("apache_commons-dbcp");
        projectList.add("apache_commons-io");
        projectList.add("brettwooldridge_HikariCP");
        projectList.add("bullhorn_sdk-rest");
        projectList.add("jenkinsci_email-ext-plugin");
        projectList.add("apache_commons-pool");
        projectList.add("logic-ng_LogicNG");
        projectList.add("finmath_finmath-lib");
        projectList.add("lmdbjava_lmdbjava");
    }
    static String home = System.getProperty("user.home");
    static String projectFolder = "projects/ctaxonomy";
    static String projectFolderPath = home + "/" + projectFolder;
    static String resultFolder = "results";
    static String resultFolderPath = projectFolderPath + "/" + resultFolder;
    static int numSHA = 50;
    static String SKIPS = " -Djacoco.skip -Dcheckstyle.skip -Drat.skip -Denforcer.skip -Danimal.sniffer.skip " +
            "-Dmaven.javadoc.skip -Dfindbugs.skip -Dwarbucks.skip -Dmodernizer.skip -Dimpsort.skip -Dpmd.skip -Dxjc.skip";
    public static String HOTFILE_PATH = "hotfiles.txt";

}