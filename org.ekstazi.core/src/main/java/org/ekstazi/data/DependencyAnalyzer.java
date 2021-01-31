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

package org.ekstazi.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.Map.Entry;

import org.ekstazi.Config;
import org.ekstazi.asm.ClassReader;
import org.ekstazi.changelevel.ChangeTypes;
import org.ekstazi.changelevel.FineTunedBytecodeCleaner;
import org.ekstazi.hash.Hasher;
import org.ekstazi.log.Log;
import org.ekstazi.monitor.CoverageMonitor;
import org.ekstazi.research.Research;
import org.ekstazi.util.FileUtil;
import org.ekstazi.util.LRUMap;

import static org.ekstazi.smethods.MethodLevelSelection.getChangedMethods;
import static org.ekstazi.smethods.MethodLevelStaticDepsBuilder.*;

/**
 * Analyzes regression data to check if resource has been modified.
 */
public final class DependencyAnalyzer {

    /** "Method" name when saving coverage per test class */
    public static final String CLASS_EXT = "clz";

    /** "Method" name when storing coverage for arbitrary run */
    public static final String COV_EXT = "cov";

    /** Cache: file->modified (note that we limit the size); cannot switch to Set */
    private final Map<String, Boolean> mUrlExternalForm2Modified;

    /** Cache of test->run mappings; useful if there are Parameterized tests (note that we limit the size) */
    private final Map<String, Boolean> mFullTestName2Rerun;

    /** IO Storer */
    private final Storer mStorer;

    /** Hasher */
    private final Hasher mHasher;

    /** A test (full name) that starts with any element in array is always run */
    private final String[] mExcludes;

    /** A test (full name) that starts with any element in array is run with Tool */
    private final String[] mIncludes;

    /** root.dir */
    private final String mRootDir;
    
    /** dependencies.append */
    private final boolean mDependenciesAppend;

    /** a cache to store if the ChangeTypes changes */
    protected static HashMap<String, Boolean> fileChangedCache = new HashMap<>();

    protected static Set<String> changedMethods;

    /**
     * Constructor.
     */
    public DependencyAnalyzer(int cacheSizes, Hasher hasher, Storer storer, String[] excludes, String[] includes) {
        this.mStorer = storer;
        this.mHasher = hasher;
        this.mExcludes = excludes;
        this.mIncludes = includes;

        this.mRootDir = Config.ROOT_DIR_V;
        this.mDependenciesAppend = Config.DEPENDENCIES_APPEND_V;

        this.mUrlExternalForm2Modified = new LRUMap<String, Boolean>(cacheSizes);

        this.mFullTestName2Rerun = new LRUMap<String, Boolean>(cacheSizes);

    }

    public synchronized void beginCoverage(String name) {
        beginCoverage(name, COV_EXT, false);
    }
    
    public synchronized void endCoverage(String name) {
        endCoverage(name, COV_EXT);
    }

    public synchronized boolean isAffected(String name) {
        String fullMethodName = name + "." + COV_EXT;
        Set<RegData> regData = mStorer.load(mRootDir, name, COV_EXT);
        boolean isAffected;
        if (Config.FINERTS_ON_V) {
            isAffected = isAffected(name.replace(".", "/"), regData);
        }else {
            isAffected = isAffected(regData);
        }
        recordTestAffectedOutcome(fullMethodName, isAffected);
        return isAffected;
    }

    /**
     * This method should be invoked to indicate that coverage measurement
     * should start. In JUnit it is expected that this method will be invoked to
     * measure coverage for a test class.
     * 
     * @param className
     *            Tag used to identify this coverage measure.
     */
    public synchronized void beginClassCoverage(String className) {
        if (isOnMustRunList(className)) {
            return;
        }
        // Note that we do not record affected outcome is already recorded
        // when checked if class is affected.
        beginCoverage(className, CLASS_EXT, false);
    }

    /**
     * This method should be invoked to indicated that coverage measurement
     * should end. In JUnit it is expected that this method will be invoked to
     * measure coverage for a test class.
     * 
     * @param className
     *            Tag used to identify this coverage measure.
     */
    public synchronized void endClassCoverage(String className) {
        if (isOnMustRunList(className)) {
            return;
        }
        endCoverage(className, CLASS_EXT);
    }
    
    /**
     * Checks if class is affected since the last run.
     * 
     * @param className
     *            Name of the class.
     * @return True if class if affected, false otherwise.
     * 
     * TODO: this method and starting coverage do some duplicate work
     */
    public synchronized boolean isClassAffected(String className) {
        if (isOnMustRunList(className)) {
            return true;
        }
        boolean isAffected = true;
        String fullMethodName = className + "." + CLASS_EXT;
        Set<RegData> regData = mStorer.load(mRootDir, className, CLASS_EXT);
        if (Config.FINERTS_ON_V) {
            isAffected = isAffected(className.replace(".", "/"), regData);
        }else {
            isAffected = isAffected(regData);
        }
        recordTestAffectedOutcome(fullMethodName, isAffected);
        return isAffected;
    }

    // INTERNAL

    /**
     * We identify cases that are currently (almost) impossible to
     * support purely from Java.
     *
     * org.apache.log4j.net.SocketServerTestCase (from Log4j project)
     * test is run/specified in build.xml.  The test runs a Java
     * process and test in parallel (two VMs).  In addition Java
     * process takes as input the number of tests that would be
     * executed.  Of course this would fail in the first place because
     * of the number of tests.  However, that is not the only problem;
     * Java process is a server that waits for connection, so if no
     * test is executed this process would just be alive forever.
     * See http://svn.apache.org/repos/asf/logging/log4j/trunk
     * (revision 1344108) and file tests/build.xml for details.
     */
    @Research
    private boolean isOnMustRunList(String className) {
        return className.equals("org.apache.log4j.net.SocketServerTestCase");
    }

    private boolean beginCoverage(String className, String methodName, boolean isRecordAffectedOutcome) {
        // Fully qualified method name.
        String fullMethodName = className + "." + methodName;

        // Clean previously collected coverage.
        CoverageMonitor.clean();

        // Check if test is included (note that we do not record info).
        if (!isIncluded(fullMethodName)) {
            return true;
        }
        
        // Check if test should be always run.
        if (isExcluded(fullMethodName)) {
            if (isRecordAffectedOutcome) {
                recordTestAffectedOutcome(fullMethodName, true);
            }
            return true;
        }

        // Check if test has been seen; this can happen when a test is in
        // Parameterized class or if the same test is invoked twice (which is
        // present in some projects: as part of a test suite and separate).
        // We force the execution as the execution may differ and we union
        // the coverage (load the old one and new one will be appended).
        if (mFullTestName2Rerun.containsKey(fullMethodName)) {
            Set<RegData> regData = mStorer.load(mRootDir, className, methodName);
            CoverageMonitor.addURLs(extractExternalForms(regData));
            return mFullTestName2Rerun.get(fullMethodName);
        }

        Set<RegData> regData = mStorer.load(mRootDir, className, methodName);
        boolean isAffected;
        if (Config.FINERTS_ON_V) {
            isAffected = isAffected(className.replace(".", "/"), regData);
        }else {
            isAffected = isAffected(regData);
        }
        if (isRecordAffectedOutcome) {
            recordTestAffectedOutcome(fullMethodName, isAffected);
        }

        // If in append mode add urls; we used this append mode when we noticed that
        // some runs give non-deterministic coverage, so we wanted to run the same
        // test several times and do union of coverage.
        if (mDependenciesAppend) {
            CoverageMonitor.addURLs(extractExternalForms(regData));
            // Force run
            isAffected = true;
        }

        // Collect tests that have been affected.
        mFullTestName2Rerun.put(fullMethodName, isAffected);
        
        return isAffected;
    }
    
    private boolean isIncluded(String fullMethodName) {
        boolean isIncluded = false;
        if (mIncludes != null) {
            for (String s : mIncludes) {
                if (fullMethodName.startsWith(s)) {
                    isIncluded = true;
                    break;
                }
            }
        } else {
            isIncluded = true;
        }
        return isIncluded;
    }

    /**
     * Checks if user requested that the given name is always run. True if this
     * name has to be always run, false otherwise.
     */
    private boolean isExcluded(String fullMethodName) {
        if (mExcludes != null) {
            for (String s : mExcludes) {
                if (fullMethodName.startsWith(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String[] extractExternalForms(Set<RegData> regData) {
        String[] externalForms = new String[regData.size()];
        int i = 0;
        for (RegData el : regData) {
            externalForms[i] = el.getURLExternalForm();
            i++;
        }
        return externalForms;
    }
    
    private void endCoverage(String className, String methodName) {
        Map<String, String> hashes = mHasher.hashExternalForms(CoverageMonitor.getURLs());
        Set<RegData> regData = new TreeSet<RegData>(new RegData.RegComparator());
        for (Entry<String, String> entry : hashes.entrySet()) {
            regData.add(new RegData(entry.getKey(), entry.getValue()));
        }
        mStorer.save(mRootDir, className, methodName, regData);
        // Clean monitor after the test finished the execution
        CoverageMonitor.clean();
    }

    protected boolean isAffected(String dirName, String className, String methodName) {
        if (Config.FINERTS_ON_V && Config.MRTS_ON_V){
            if (changedMethods == null){
                try {
//                    long start = System.currentTimeMillis();
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
//                    long end = System.currentTimeMillis();
//                    System.out.println("[time for method level dependency]: " + (end - start)/1000.0);
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

    /**
     * Returns true if test is affected. Test is affected if hash of any
     * resource does not match old hash.
     */
    private boolean isAffected(Set<RegData> regData) {
        return regData == null || regData.size() == 0 || hasHashChanged(regData);
    }

    /**
     * Hashes files and compares with the old hashes. If any hash is different,
     * returns true; false otherwise.
     */
    private boolean hasHashChanged(Set<RegData> regData) {
            for (RegData el : regData) {
                if (hasHashChanged(mHasher, el)) {
                    Log.d("CHANGED", el.getURLExternalForm());
                    return true;
                }
            }
            return false;
    }

    /**
     * Hashes file and compares with the old hash. If hashes are different,
     * return true; false otherwise
     *
     * If the hashes differ, then compare the ChangeTypes to avoid corner cases
     */
    private boolean hasHashChanged(Hasher hasher, RegData regDatum) {
        String urlExternalForm = regDatum.getURLExternalForm();
        Boolean modified = mUrlExternalForm2Modified.get(urlExternalForm);
        if (modified != null) {
            return modified;
        }
        // Check hash
        String newHash = hasher.hashURL(urlExternalForm);
        modified = !newHash.equals(regDatum.getHash());
        // TODO:
        if (Config.FINERTS_ON_V && modified && urlExternalForm.contains("target")) {
            String fileName = FileUtil.urlToObjFilePath(urlExternalForm);
            Boolean changed = fileChangedCache.get(fileName);
            if (changed != null) {
//                System.out.println("dependencyAnalyzer : " + changed + " " + fileName);
                return changed;
            }
            ChangeTypes curChangeTypes;
            try {
                ChangeTypes preChangeTypes = ChangeTypes.fromFile(fileName);
                File curClassFile = new File(urlExternalForm.substring(urlExternalForm.indexOf("/")));
                if (!curClassFile.exists()) {
                    modified = true;
                } else {
                    curChangeTypes = FineTunedBytecodeCleaner.removeDebugInfo(FileUtil.readFile(curClassFile));
                    modified = preChangeTypes == null || !preChangeTypes.equals(curChangeTypes);
                }
                fileChangedCache.put(fileName, modified);
                mUrlExternalForm2Modified.put(urlExternalForm, modified);
                return modified;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        mUrlExternalForm2Modified.put(urlExternalForm, modified);
        return modified;
    }

    @Research
    private void recordTestAffectedOutcome(String fullMethodName, boolean isAffected) {
        if (!Config.X_LOG_RUNS_V) {
            return;
        }
        try {
            File f = new File(Config.RUN_INFO_V);
            f.getParentFile().mkdirs();
            PrintWriter pw = new PrintWriter(new FileOutputStream(f, true));
            pw.println(mRootDir + "." + fullMethodName + " " + (isAffected ? "RUN" : "SKIP"));
            pw.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
