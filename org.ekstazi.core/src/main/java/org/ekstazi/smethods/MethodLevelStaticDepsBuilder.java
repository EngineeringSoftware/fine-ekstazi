package org.ekstazi.smethods;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.ekstazi.asm.ClassReader;
import org.ekstazi.changelevel.ChangeTypes;

import static org.ekstazi.smethods.Macros.*;
public class MethodLevelStaticDepsBuilder{
    // mvn exec:java -Dexec.mainClass=org.sekstazi.smethods.MethodLevelStaticDepsBuilder -Dmyproperty=/Users/liuyu/projects/finertsTest

    // for every class, get the methods it implements
    public static Map<String, Set<String>> class2ContainedMethodNames = new HashMap<>();
    // for every method, get the methods it invokes
    public static Map<String, Set<String>> methodName2MethodNames = new HashMap<>();
    // for every class, find its parents.
    public static Map<String, Set<String>> hierarchy_parents = new HashMap<>();
    // for every class, find its children.
    public static Map<String, Set<String>> hierarchy_children = new HashMap<>();

    public static void saveMap(Map<String, Set<String>> mapToStore, String fileName) throws Exception {
        File directory = new File(TEST_PROJECT_PATH + "/" + EKSTAZI_ROOT_DIR_NAME);
        if (!directory.exists()) {
            directory.mkdir();
        }

        File txtFile = new File(directory, fileName);
        PrintWriter pw = new PrintWriter(txtFile);

        for (Map.Entry<String, Set<String>> en : mapToStore.entrySet()) {
            String methodName = en.getKey();
            //invokedMethods saved in csv format
            String invokedMethods = String.join(",", mapToStore.get(methodName));
            pw.println(methodName + " " + invokedMethods);
        }
        pw.flush();
        pw.close();
    }

    public static Map<String, Set<String>> readMap(String filename) throws Exception {
        Map<String, Set<String>> map = new HashMap<>();
        File directory = new File(TEST_PROJECT_PATH + "/" + EKSTAZI_ROOT_DIR_NAME);
        File file = new File(directory, filename);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
            String[] tokens = line.split(" ");
            String methodName = tokens[0];
            Set<String> invokedMethods = new HashSet<>();
            for (String method : tokens[1].split(",")) {
                invokedMethods.add(method);
            }
            map.put(methodName, invokedMethods);
        }
        br.close();
        return map;    
    }

    public static void saveSet(Set<String> setToStore, String fileName) throws Exception {
        // File directory = new File(".ekstazi");
        File directory = new File(TEST_PROJECT_PATH + "/" + EKSTAZI_ROOT_DIR_NAME);
        if (!directory.exists()) {
            directory.mkdir();
        }

        File txtFile = new File(directory, fileName);
        PrintWriter pw = new PrintWriter(txtFile);

        for (String s : setToStore) {
            pw.println(s);
        }
        pw.flush();
        pw.close();
    }

    // simple DFS
    public static void getDepsDFS(String methodName, Set<String> visitedMethods){
        if (methodName2MethodNames.containsKey(methodName)){
            for (String method : methodName2MethodNames.get(methodName)){
                if (!visitedMethods.contains(method)){
                    visitedMethods.add(method);
                    getDepsDFS(method, visitedMethods);
                }
            }
        }
    }

    public static Set<String> getDeps(String testClass){
        Set<String> visited = new HashSet<>();
        for (String method : methodName2MethodNames.keySet()){
            if (method.startsWith(testClass+"#")){
                visited.add(method);
                getDepsDFS(method, visited);
            }
        }
        return visited;
    }

    public static Map<String, Set<String>> getDeps(Set<String> testClasses){
        long startTime = System.currentTimeMillis();
        Map<String, Set<String>> test2methods = new ConcurrentSkipListMap<>();
        ExecutorService service = null;
        try {
            service = Executors.newFixedThreadPool(16);
            for (final String testClass : testClasses)
            {
                service.submit(() -> {
                   Set<String> visited = new ConcurrentSkipListSet<>();
                    for (String method : methodName2MethodNames.keySet()){
                        if (method.startsWith(testClass+"#")){
                            visited.add(method);
                            getDepsDFS(method, visited);
                        }
                    }
                    test2methods.put(testClass, visited);
                });
            }
            service.shutdown();
            service.awaitTermination(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        long endTime = System.currentTimeMillis();
        System.out.println("FineEkstaziTC: " + (endTime - startTime));
        return test2methods;
    }

    public static void findMethodsinvoked(Set<String> classPaths){
        for (String classPath : classPaths){
            try {
                ClassReader classReader = new ClassReader(new FileInputStream(new File(classPath)));
                ClassToMethodsCollectorCV classToMethodsVisitor = new ClassToMethodsCollectorCV(class2ContainedMethodNames, hierarchy_parents, hierarchy_children);
                classReader.accept(classToMethodsVisitor, ClassReader.SKIP_DEBUG);    
            } catch (IOException e) {
                System.out.println("Cannot parse file: " + classPath);
                throw new RuntimeException(e);
            }   
        }
        for (String classPath : classPaths){
            try {
                ClassReader classReader = new ClassReader(new FileInputStream(new File(classPath)));
                //TODO: not keep methodName2MethodNames, hierarchies as fields
                MethodCallCollectorCV methodClassVisitor = new MethodCallCollectorCV(methodName2MethodNames, hierarchy_parents, hierarchy_children, class2ContainedMethodNames);
                classReader.accept(methodClassVisitor, ClassReader.SKIP_DEBUG);
            } catch(IOException e) {
                System.out.println("Cannot parse file: " + classPath);
                throw new RuntimeException(e);
            }
        }

        // deal with test class in a special way, all the @test method in hierarchy should be considered
        for (String superClass : hierarchy_children.keySet()) {
            if (superClass.contains("Test")) {
                for (String subClass : hierarchy_children.getOrDefault(superClass, new HashSet<>())) {
                    for (String methodSig : class2ContainedMethodNames.getOrDefault(superClass, new HashSet<>())) {
                        String subClassKey = subClass + "#" + methodSig;
                        String superClassKey = superClass + "#" + methodSig;
                        methodName2MethodNames.computeIfAbsent(subClassKey, k -> new TreeSet<>()).add(superClassKey);
                    }
                }
            }
        }
    }

    public static Set<String> getChangedMethods(ChangeTypes preChangeTypes, ChangeTypes curChangeTypes){
        Set<String> res = new HashSet<>();
        if (preChangeTypes == null){
            // does not exist before
            if (curChangeTypes.curClass.contains("Test")) {
                Set<String> methods = new HashSet<>();
                curChangeTypes.instanceMethodMap.keySet().forEach(m -> methods.add(curChangeTypes.curClass + "#" +
                        m.substring(0, m.indexOf(")") + 1)));
                curChangeTypes.staticMethodMap.keySet().forEach(m -> methods.add(curChangeTypes.curClass + "#" +
                        m.substring(0, m.indexOf(")") + 1)));
                curChangeTypes.constructorsMap.keySet().forEach(m -> methods.add(curChangeTypes.curClass + "#" +
                        m.substring(0, m.indexOf(")") + 1)));
                res.addAll(methods);
            }
        }else {
            if (!preChangeTypes.equals(curChangeTypes)) {
                res.addAll(getChangedMethodsPerChangeType(preChangeTypes.instanceMethodMap,
                        curChangeTypes.instanceMethodMap, curChangeTypes.curClass));
                res.addAll(getChangedMethodsPerChangeType(preChangeTypes.staticMethodMap,
                        curChangeTypes.staticMethodMap, curChangeTypes.curClass));
                res.addAll(getChangedMethodsPerChangeType(preChangeTypes.constructorsMap,
                        curChangeTypes.constructorsMap, curChangeTypes.curClass));
            }
        }    
        return res;
    }

        static Set<String> getChangedMethodsPerChangeType(TreeMap<String, String> oldMethodsPara, TreeMap<String, String> newMethodsPara,
                                                      String className){
        Set<String> res = new HashSet<>();
        TreeMap<String, String> oldMethods = new TreeMap<>(oldMethodsPara);
        TreeMap<String, String> newMethods = new TreeMap<>(newMethodsPara);
        // consider adding test class
        Set<String> methodSig = new HashSet<>(oldMethods.keySet());
        methodSig.addAll(newMethods.keySet());
        for (String sig : methodSig){
            if (oldMethods.containsKey(sig) && newMethods.containsKey(sig)){
                if (oldMethods.get(sig).equals(newMethods.get(sig))) {
                    oldMethods.remove(sig);
                    newMethods.remove(sig);
                }else{
                    res.add(className + "#" + sig.substring(0, sig.indexOf(")")+1));
                }
            } else if (oldMethods.containsKey(sig) && newMethods.containsValue(oldMethods.get(sig))){
                newMethods.values().remove(oldMethods.get(sig));
                oldMethods.remove(sig);
            } else if (newMethods.containsKey(sig) && oldMethods.containsValue(newMethods.get(sig))){
                oldMethods.values().remove(newMethods.get(sig));
                newMethods.remove(sig);
            }
        }
        // className is Test
        String outerClassName = className.split("\\$")[0];
        if (outerClassName.contains("Test")){
            for (String sig : newMethods.keySet()){
                res.add(className + "#" + sig.substring(0, sig.indexOf(")")+1));
            }
        }
        return res;
    }
}