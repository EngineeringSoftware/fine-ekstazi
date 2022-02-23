/*
 * Copyright 2014-present Milos Gligoric
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ekstazi.check;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ekstazi.Config;
import org.ekstazi.changelevel.ChangeTypes;
import org.ekstazi.changelevel.FineTunedBytecodeCleaner;
import org.ekstazi.changelevel.Macros;
import org.ekstazi.data.RegData;
import org.ekstazi.data.Storer;
import org.ekstazi.hash.Hasher;
import org.ekstazi.util.FileUtil;

import static org.ekstazi.smethods.MethodLevelStaticDepsBuilder.*;

abstract class AbstractCheck {

    /** Storer */
    protected final Storer mStorer;

    /** Hasher */
    protected final Hasher mHasher;

    public static List<String> hotfiles;

    public static HashMap<String, Boolean> fileChangedCache = new HashMap<>();

    public static Set<String> newClassesPaths = new HashSet<>();

    public static boolean initGraph = false;

    public static Set<String> classesHavingChangedMethods = new HashSet<>();

    public static Set<String> changedMethods = new HashSet<>();

    public static boolean initClassesPath = false;
    /**
     * Constructor.
     */
    public AbstractCheck(Storer storer, Hasher hasher) {
        this.mStorer = storer;
        this.mHasher = hasher;
    }

    public abstract String includeAll(String fileName, String fileDir);

    public abstract void includeAffected(Set<String> affectedClasses);

    protected boolean isAffected(String dirName, String className, String methodName) {
        if (Config.HOTFILE_ON_V){
            for (RegData el : mStorer.load(dirName, className, methodName)){
                String urlExternalForm = el.getURLExternalForm();
                // Check hash.
                String newHash = mHasher.hashURL(urlExternalForm);
                boolean anyDiff = !newHash.equals(el.getHash());
                if (anyDiff) {
                    if (hotfiles == null){
                        hotfiles = HotFileHelper.getHotFiles(Config.HOTFILE_TYPE_V, Config.HOTFILE_PERCENT_V);
                    }
                    if (Config.HOTFILE_TYPE_V.equals(HotFileHelper.CHANGE_FRE_HOTFILE)){
                        String clzName = urlExternalForm.substring(0, urlExternalForm.length() - ".class".length());
                        if (urlExternalForm.contains("$")){
                            clzName = clzName.substring(0, clzName.indexOf("$"));
                        }
                        String[] splittedClassNames = clzName.split("/");
                        if (!hotfiles.contains(splittedClassNames[splittedClassNames.length - 1])){
                            String testClass = className.replace(".", "/");
                            fileChangedCache.put(testClass, true);
                            return true;
                        }
                    }else{
                        if (!hotfiles.contains(urlExternalForm)){
                            String testClass = className.replace(".", "/");
                            fileChangedCache.put(testClass, true);
                            return true;
                        }
                    }
                }
            }
        }
        if (Config.FINERTS_ON_V && Config.MRTS_ON_V){
            String internalClassName = className.replace(".", "/");
            boolean affected = isAffected(internalClassName, mStorer.load(dirName, className, methodName));
            return affected;
        }else {
            return isAffected(mStorer.load(dirName, className, methodName));
        }
    }

    private void verify(Map<String, Set<String>> test2methods, Map<String, Set<String>> test2methodsPrime){
        for (String test : test2methods.keySet()) {
            if (!test2methodsPrime.containsKey(test)){
                System.out.println("test: " + test);
                System.out.println("test2methods and test2methodsPrime should contain same test");
                // throw new RuntimeException("test2methods and test2methodsPrime should contain same test");
            }
            if (test2methods.get(test).size() != test2methodsPrime.get(test).size()) {
                System.out.println("[test2methods]: " + test + " " + test2methods.get(test).size() + " " + test2methodsPrime.get(test).size());
                // Set<String> diffSet = (TreeSet<String>) test2methodsPrime.get(test);
                // diffSet.removeAll(test2methods.get(test));
                // System.out.println("[diffSet]: " + diffSet);
                // throw new RuntimeException("[test2methods]: " + test + " " + test2methods.get(test).size() + " " + test2methodsPrime.get(test).size());   
            }
        }
    }

    protected boolean isAffected(String testClass, Set<RegData> regData){
        if (regData == null || regData.size() == 0){
            return true;
        }

        if (fileChangedCache.containsKey(testClass)){
            return fileChangedCache.get(testClass);
        }

        Set<String> clModifiedClasses = new HashSet<>();

        for (RegData el : regData) {
            if (hasHashChanged(mHasher, el)) {
                if (!initGraph){
                    try {
                        // find the methods that each method calls
                        findMethodsinvoked(newClassesPaths);                                     
                        initGraph = true;
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }
                }
                String urlExternalForm = el.getURLExternalForm();
                int i = 0;
                if (urlExternalForm.contains("target/classes/")){
                    i = urlExternalForm.indexOf("target/classes/") + "target/classes/".length();
                }else if (urlExternalForm.contains("target/test-classes/")){
                    i = urlExternalForm.indexOf("target/test-classes/") + "target/test-classes/".length();
                }
                String internalName = urlExternalForm.substring(i, urlExternalForm.length()-".class".length()); 
                clModifiedClasses.add(internalName);        
            }
            
        }

        if (clModifiedClasses.size() == 0){
            fileChangedCache.put(testClass, false);
            return false;
        }

        Set<String> mlUsedClasses = new HashSet<>();
        Set<String> mlUsedMethods = getDeps(testClass);
        for (String mlUsedMethod: mlUsedMethods){
            mlUsedClasses.add(mlUsedMethod.split("#")[0]);
        }
        if (mlUsedClasses.containsAll(clModifiedClasses)){
            // method level
            for (String clModifiedClass : clModifiedClasses){
                for (String method : changedMethods){
                    if (method.startsWith(clModifiedClass) && mlUsedMethods.contains(method)){
                        fileChangedCache.put(testClass, true);
                        return true;
                    }
                }
            }
            fileChangedCache.put(testClass, false);
            return false;
        }else{
            // reflection
            fileChangedCache.put(testClass, true);
            return true;
        }
    }

    protected boolean isAffected(Set<RegData> regData) {
        return regData == null || regData.size() == 0 || hasHashChanged(regData);
    }

    /**
     * Check if any element of the given set has changed.
     */
    private boolean hasHashChanged(Set<RegData> regData) {
        for (RegData el : regData) {
            if (hasHashChanged(mHasher, el)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the given datum has changed using the given hasher.
     */
    protected final boolean hasHashChanged(Hasher hasher, RegData regDatum) {
        String urlExternalForm = regDatum.getURLExternalForm();
        // Check hash.
        String newHash = hasher.hashURL(urlExternalForm);
        boolean anyDiff = !newHash.equals(regDatum.getHash());
        // TODO: If checksum of ekstazi differs, compare ChangeTypes
        if (Config.FINERTS_ON_V && anyDiff && urlExternalForm.contains("target")) {
            if (fileChangedCache.containsKey(urlExternalForm)){
                return fileChangedCache.get(urlExternalForm);
            }

            if (!initClassesPath){
                // initalize newClassesPaths
                newClassesPaths = FileUtil.getClassPaths();
                ChangeTypes.initHierarchyGraph(newClassesPaths);
                initClassesPath = true;
            }
            boolean changed = false;
            ChangeTypes curChangeTypes;
            try {
                String fileName = FileUtil.urlToObjFilePath(urlExternalForm);
                ChangeTypes preChangeTypes = ChangeTypes.fromFile(fileName);
                File curClassFile = new File(urlExternalForm.substring(urlExternalForm.indexOf("/")));
                if (!curClassFile.exists()) {
                    changed = true;
                } else {
                    curChangeTypes = FineTunedBytecodeCleaner.removeDebugInfo(FileUtil.readFile(
                            new File(urlExternalForm.substring(urlExternalForm.indexOf("/")))));
                    changed = preChangeTypes == null || !preChangeTypes.equals(curChangeTypes);
                    if(Config.MRTS_ON_V){
                        if (!classesHavingChangedMethods.contains(fileName)){
                            classesHavingChangedMethods.add(fileName);
                            Set<String> curChangedMethods = getChangedMethods(preChangeTypes, curChangeTypes);
                            changedMethods.addAll(curChangedMethods);
                        }
                    }
                }
                fileChangedCache.put(urlExternalForm, changed);
                return changed;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return anyDiff;
    }
}
