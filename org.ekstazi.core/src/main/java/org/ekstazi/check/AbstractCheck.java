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
import org.ekstazi.data.DependencyAnalyzer;
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

    protected static List<String> hotfiles;
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
            if (hotfiles == null){
                hotfiles = new ArrayList<>();
                Path path = Paths.get(Macros.HOTFILE_PATH);
                if (Files.exists(path)){
                    try (Stream<String> lines = Files.lines(path)) {
                        hotfiles = lines.collect(Collectors.toList());
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
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

        if (DependencyAnalyzer.methodFileChagnedCache.containsKey(testClass)){
            return DependencyAnalyzer.methodFileChagnedCache.get(testClass);
        }

        Set<String> clModifiedClasses = new HashSet<>();
        for (RegData el : regData) {
            if (hasHashChanged(mHasher, el)) {
                if (!DependencyAnalyzer.initGraph){
                    try {
                        // find the methods that each method calls
                        findMethodsinvoked(DependencyAnalyzer.newClassesPaths);                                     
                        DependencyAnalyzer.initGraph = true;
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }
                }
                String urlExternalForm = el.getURLExternalForm();
                int i = urlExternalForm.indexOf("target/classes/");
                if (i == -1)
                    i = urlExternalForm.indexOf("target/test-classes/") + "target/test-classes/".length();
                else
                    i = i + + "target/classes/".length();
                String internalName = urlExternalForm.substring(i, urlExternalForm.length()-6);

                if (Config.HOTFILE_ON_V){
                    if (hotfiles == null){
                        hotfiles = new ArrayList<>();
                        Path path = Paths.get(Macros.HOTFILE_PATH);
                        if (Files.exists(path)){
                            try (Stream<String> lines = Files.lines(path)) {
                                hotfiles = lines.collect(Collectors.toList());
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    if (!hotfiles.contains(internalName)){
                        DependencyAnalyzer.methodFileChagnedCache.put(testClass, true);
                        return true;
                    }else{
                        clModifiedClasses.add(internalName);
                    }
                }else{
                    clModifiedClasses.add(internalName);
                }
            }
        }

        if (clModifiedClasses.size() == 0){
            DependencyAnalyzer.methodFileChagnedCache.put(testClass, false);
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
                for (String method : DependencyAnalyzer.changedMethods){
                    if (method.startsWith(clModifiedClass) && mlUsedMethods.contains(method)){
                        DependencyAnalyzer.methodFileChagnedCache.put(testClass, true);
                        return true;
                    }
                }
            }
            DependencyAnalyzer.methodFileChagnedCache.put(testClass, false);
            return false;
        }else{
            // reflection
            DependencyAnalyzer.methodFileChagnedCache.put(testClass, true);
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
            if (DependencyAnalyzer.newClassesPaths.size() == 0){
                // initalize newClassesPaths
                DependencyAnalyzer.newClassesPaths = FileUtil.getClassPaths();
                ChangeTypes.initHierarchyGraph(DependencyAnalyzer.newClassesPaths);
            }
            String fileName = FileUtil.urlToObjFilePath(urlExternalForm);
            Boolean changed = DependencyAnalyzer.fileChangedCache.get(fileName);
            if (changed != null){
                return changed;
            }
            ChangeTypes curChangeTypes;
            try {
                ChangeTypes preChangeTypes = ChangeTypes.fromFile(fileName);
                File curClassFile = new File(urlExternalForm.substring(urlExternalForm.indexOf("/")));
                if (!curClassFile.exists()) {
                    changed = true;
                } else {
                    curChangeTypes = FineTunedBytecodeCleaner.removeDebugInfo(FileUtil.readFile(
                            new File(urlExternalForm.substring(urlExternalForm.indexOf("/")))));
                    changed = preChangeTypes == null || !preChangeTypes.equals(curChangeTypes);
                    if(Config.MRTS_ON_V){
                        if (!DependencyAnalyzer.classesHavingChangedMethods.contains(fileName)){
                            DependencyAnalyzer.classesHavingChangedMethods.add(fileName);
                            Set<String> curChangedMethods = getChangedMethods(preChangeTypes, curChangeTypes);
                            DependencyAnalyzer.changedMethods.addAll(curChangedMethods);
                        }
                    }
                }
                DependencyAnalyzer.fileChangedCache.put(fileName, changed);
                return changed;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return anyDiff;
    }
}
