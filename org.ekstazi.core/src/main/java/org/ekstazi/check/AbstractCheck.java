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
import java.util.*;
import java.util.stream.Collectors;

import org.ekstazi.Config;
import org.ekstazi.asm.ClassReader;
import org.ekstazi.changelevel.ChangeTypes;
import org.ekstazi.changelevel.FineTunedBytecodeCleaner;
import org.ekstazi.data.RegData;
import org.ekstazi.data.Storer;
import org.ekstazi.hash.Hasher;
import org.ekstazi.util.FileUtil;

import static org.ekstazi.smethods.MethodLevelSelection.getChangedMethods;
import static org.ekstazi.smethods.MethodLevelStaticDepsBuilder.*;
import static org.ekstazi.smethods.MethodLevelStaticDepsBuilder.test2methods;

abstract class AbstractCheck {

    /** Storer */
    protected final Storer mStorer;

    /** Hasher */
    protected final Hasher mHasher;

    protected static HashMap<String, Boolean> fileChangedCache = new HashMap<>();

    protected static Set<String> changedMethods;
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
        if (Config.FINERTS_ON_V){
            if (changedMethods == null){
                try {
                    List<ClassReader> classReaderList = getClassReaders(".");

                    // find the methods that each method calls
                    findMethodsinvoked(classReaderList);

                    // suppose that test classes have Test in their class name
                    Set<String> testClasses = new HashSet<>();
                    for (ClassReader c : classReaderList){
                        if (c.getClassName().contains("Test")){
                            testClasses.add(c.getClassName().split("\\$")[0]);
                        }
                    }
                    test2methods = getDeps(methodName2MethodNames, testClasses);
                    changedMethods = getChangedMethods(testClasses);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }
            }
            String internalClassName = className.replace(".", "/");
            return isAffected(internalClassName, mStorer.load(dirName, className, methodName));
        }else {
            return isAffected(mStorer.load(dirName, className, methodName));
        }
    }

    protected boolean isAffected(String testClass, Set<RegData> regData){
        if (regData == null || regData.size() == 0){
            return true;
        }
        Set<String> clModifiedClasses = new HashSet<>();
        for (RegData el : regData) {
            if (hasHashChanged(mHasher, el)) {
                String urlExternalForm = el.getURLExternalForm();
                int i = urlExternalForm.indexOf("target/classes/");
                if (i == -1)
                    i = urlExternalForm.indexOf("target/test-classes/") + "target/test-classes/".length();
                else
                    i = i + + "target/classes/".length();
                String internalName = urlExternalForm.substring(i, urlExternalForm.length()-6);
                clModifiedClasses.add(internalName);
            }
        }

        if (clModifiedClasses.size() == 0){
            return false;
        }

        Set<String> mlUsedClasses = new HashSet<>();
        Set<String> mlUsedMethods = test2methods.getOrDefault(testClass, new TreeSet<>());
        for (String mulUsedMethod: mlUsedMethods){
            mlUsedClasses.add(mulUsedMethod.split("#")[0]);
        }
        if (mlUsedClasses.containsAll(clModifiedClasses)){
            // method level
            for (String clModifiedClass : clModifiedClasses){
                // todo
                for (String method : changedMethods){
                    if (method.startsWith(clModifiedClass) && mlUsedMethods.contains(method)){
                        return true;
                    }
                }
            }
            return false;
        }else{
            // reflection
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
        // TODO: if checksum of ekstazi differs, compare ChangeTypes
        if (Config.FINERTS_ON_V && anyDiff && urlExternalForm.contains("target")) {
            String fileName = FileUtil.urlToObjFilePath(urlExternalForm);
            Boolean changed = fileChangedCache.get(fileName);
//            System.out.println("AbstractCheck ChangeTypes.fileChanged: " + fileChanged);
            if (changed != null){
//                System.out.println("AbstractCheck: " + changed + " " + fileName);
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
                }
                fileChangedCache.put(fileName, changed);
                return changed;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return anyDiff;
    }

}
