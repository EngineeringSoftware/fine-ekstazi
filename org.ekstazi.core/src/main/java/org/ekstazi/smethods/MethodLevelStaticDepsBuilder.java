package org.ekstazi.smethods;

import org.ekstazi.asm.ClassReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.ekstazi.smethods.MethodLevelSelection.getInvokedConstructorsMap;
import static org.ekstazi.smethods.MethodLevelSelection.getChangedMethods;
import static org.ekstazi.smethods.Macros.*;
import org.ekstazi.changelevel.ChangeTypes;
import org.ekstazi.changelevel.FineTunedBytecodeCleaner;
import org.ekstazi.util.FileUtil;
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
    // for every test class, find what method it depends on
    public static Map<String, Set<String>> test2methods = new HashMap<>();

    public static void main(String... args) throws Exception {
        // We need at least the argument that points to the root
        // directory where the search for .class files will start.
        if (args.length < 1) {
            throw new RuntimeException("Incorrect arguments");
        }
        String pathToStartDir = args[0];
       
        TEST_PROJECT_PATH = args[0];
        // path to previous SHA's classes directory
        String classesDir = args[1];
        // parse test classes to ChangeTypes
        // the following lines are added for testing purpose
        String serPath = TEST_PROJECT_PATH + "/" + EKSTAZI_ROOT_DIR_NAME + "/" + CHANGE_TYPES_DIR_NAME;
        List<Path> classFilePaths = Files.walk(Paths.get(classesDir)).filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".class")).collect(Collectors.toList());
        for (Path classFilePath : classFilePaths){
            //System.out.println("[log] classPath: " + urlExternalForm.substring(urlExternalForm.indexOf("/")));
            ChangeTypes curChangeTypes = FineTunedBytecodeCleaner.removeDebugInfo(FileUtil.readFile(classFilePath.toFile()));
            //System.out.println("[log] curClassName: " + curChangeTypes.curClass);
            // System.out.println(serPath + "/" + classFilePath.toFile().getName().replace(".class", ".ser"));
            String absolutePath = classFilePath.toAbsolutePath().toString();
            String serFileName = "";
            if (absolutePath.contains("target/classes/")){
                int i = absolutePath.indexOf("target/classes/");
                serFileName = absolutePath.substring(i + "target/classes/".length()).replace("/", ".").replace(".class", ".ser");
            }else if(absolutePath.contains("target/test-classes/")){
                int i = absolutePath.indexOf("target/test-classes/");
                serFileName = absolutePath.substring(i + "target/test-classes/".length()).replace("/", ".").replace(".class", ".ser");
            }
            ChangeTypes.toFile(serPath + "/" + serFileName, curChangeTypes);
        }

        List<ClassReader> classReaderList = getClassReaders(pathToStartDir);

        // find the methods that each method calls
        findMethodsinvoked(classReaderList);

        // suppose that test classes have Test in their class name
        Set<String> testClasses = new HashSet<>();
        for (String method : methodName2MethodNames.keySet()){
            String className = method.split("#|\\$")[0];
            if (className.contains("Test")){
                testClasses.add(className);
            }
        }

        Set<String> changedMethods = getChangedMethods(testClasses);
        saveSet(changedMethods, "changedMethods.txt");
        // collect invoked constructors for each method
        Map<String, Set<String>> method2invokedConstructors = getInvokedConstructorsMap(methodName2MethodNames);

        test2methods = getDeps(methodName2MethodNames, testClasses);

        saveMap(method2invokedConstructors, "m2constructors.txt");
        saveMap(methodName2MethodNames, "graph.txt");
        saveMap(hierarchy_parents, "hierarchy_parents.txt");
        saveMap(hierarchy_children, "hierarchy_children.txt");
        saveMap(class2ContainedMethodNames, "class2methods.txt");
        // save into a txt file ".ekstazi/methods.txt"
        saveMap(test2methods, "methods.txt");
    
    }

    //TODO: keeping all the classreaders would crash the memory
    public static List<ClassReader> getClassReaders(String directory) throws IOException {
        return Files.walk(Paths.get(directory))
                .sequential()
                .filter(x -> !x.toFile().isDirectory())
                .filter(x -> x.toFile().getAbsolutePath().endsWith(".class"))
                .map(new Function<Path, ClassReader>() {
                    @Override
                    public ClassReader apply(Path t) {
                        try {
                            return new ClassReader(new FileInputStream(t.toFile()));
                        } catch(IOException e) {
                            System.out.println("Cannot parse file: "+t);
                            return null;
                        }
                    }
                })
                .filter(x -> x != null)
                .collect(Collectors.toList());
    }

    public static void findMethodsinvoked(List<ClassReader> classReaderList){
        for (ClassReader classReader : classReaderList){
            ClassToMethodsCollectorCV visitor = new ClassToMethodsCollectorCV(class2ContainedMethodNames , hierarchy_parents, hierarchy_children);
            classReader.accept(visitor, ClassReader.SKIP_DEBUG);
        }
        for (ClassReader classReader : classReaderList){
//            Set<String> classesInConstantPool = ConstantPoolParser.getClassNames(ByteBuffer.wrap(classReader.b));
            //TODO: not keep methodName2MethodNames, hierarchies as fields
            MethodCallCollectorCV visitor = new MethodCallCollectorCV(methodName2MethodNames, hierarchy_parents, hierarchy_children, class2ContainedMethodNames);
            classReader.accept(visitor, ClassReader.SKIP_DEBUG);
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

    public static void saveMap(Map<String, Set<String>> mapToStore, String fileName) throws Exception {
        // File directory = new File(".ekstazi");
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
    public static void getDepsHelper(String methodName, Map<String, Set<String>> methodName2MethodNames, Set<String> visitedMethods){
        if (methodName2MethodNames.containsKey(methodName)){
            for (String method : methodName2MethodNames.get(methodName)){
                if (!visitedMethods.contains(method)){
                    visitedMethods.add(method);
                    getDepsHelper(method, methodName2MethodNames, visitedMethods);
                }
            }
        }
    }

    public static Map<String, Set<String>> getDeps(Map<String, Set<String>> methodName2MethodNames, Set<String> testClasses){
        Map<String, Set<String>> test2methods = new HashMap<>();
        for (String testClass : testClasses){
            // DFS
            Set<String> methodDeps = new HashSet<>();
            HashSet<String> visited = new HashSet<>();
            for (String method : methodName2MethodNames.keySet()){
                if (method.startsWith(testClass+"#")){
                    visited.add(method);
                    getDepsHelper(method, methodName2MethodNames, visited);
                    methodDeps.addAll(visited);
                }
            }
            testClass = testClass.split("\\$")[0];
            Set<String> existedDeps = test2methods.getOrDefault(testClass, new HashSet<>());
            existedDeps.addAll(methodDeps);
            test2methods.put(testClass, existedDeps);
        }
        return test2methods;
    }


    // public static Set<String> getDepsHelper(String methodName, Map<String, Set<String>> methodName2MethodNames, Set<String> visitedMethods, Map<String, Set<String>> depsCache){
    //     HashSet<String> deps = new HashSet<>();
    //     if (depsCache.containsKey(methodName)){
    //         deps = (HashSet<String>) depsCache.get(methodName);
    //         visitedMethods.addAll(deps);
    //     }else{
    //         if (methodName2MethodNames.containsKey(methodName)){
    //             for (String method : methodName2MethodNames.get(methodName)){
    //                 if (!visitedMethods.contains(method)){
    //                     visitedMethods.add(method);
    //                     deps.addAll(getDepsHelper(method, methodName2MethodNames, visitedMethods, depsCache));
    //                 }
    //             }
    //         }
    //     }
    //     depsCache.put(methodName, deps);
    //     return deps;
    // }

    // public static Map<String, Set<String>> getDeps(Map<String, Set<String>> methodName2MethodNames, Set<String> testClasses){
    //     Map<String, Set<String>> test2methods = new HashMap<>();
    //     Map<String, Set<String>> depsCache = new HashMap<>();
    //     for (String testClass : testClasses){
    //         // DFS
    //         Set<String> methodDeps = new HashSet<>();
    //         HashSet<String> visited = new HashSet<>();
    //         for (String method : methodName2MethodNames.keySet()){
    //             if (method.startsWith(testClass+"#")){
    //                 visited.add(method);
    //                 getDepsHelper(method, methodName2MethodNames, visited, depsCache);
    //                 methodDeps.addAll(visited);
    //                 // methodDeps.addAll(getDepsHelper(methodName2MethodNames, method, visited, depsCache));
    //             }
    //         }
    //         testClass = testClass.split("\\$")[0];
    //         Set<String> existedDeps = test2methods.getOrDefault(testClass, new HashSet<>());
    //         existedDeps.addAll(methodDeps);
    //         test2methods.put(testClass, existedDeps);
    //     }
    //     return test2methods;
    // }

    // BFS without any optimization
    public static Map<String, Set<String>> getDepsBFS(Map<String, Set<String>> methodName2MethodNames, Set<String> testClasses){
        // filter the test2methods with regData
        Map<String, Set<String>> test2methods = new HashMap<>();
        for (String testClass : testClasses){
            Set<String> visitedMethods = new TreeSet<>();
            //BFS
            ArrayDeque<String> queue = new ArrayDeque<>();
            //initialization
            for (String method : methodName2MethodNames.keySet()){
                if (method.startsWith(testClass+"#")){
                    queue.add(method);
                    visitedMethods.add(method);
                }
            }

            while (!queue.isEmpty()){
                String currentMethod = queue.pollFirst();
                for (String caller : methodName2MethodNames.getOrDefault(currentMethod, new HashSet<>())){
                    if (!visitedMethods.contains(caller)) {
                        // TODO: following three lines 
                        if (testClass.split("\\$")[0].equals("org/apache/commons/codec/language/bm/RuleTest") && caller.equals("org/apache/commons/codec/language/bm/Languages#NO_LANGUAGES")){
                            System.out.println(currentMethod + " caller: " + caller);
                        }
                        queue.add(caller);
                        visitedMethods.add(caller);
                    }
                }
            }
            testClass = testClass.split("\\$")[0];
            Set<String> existedDeps = test2methods.getOrDefault(testClass, new TreeSet<>());
            existedDeps.addAll(visitedMethods);
            test2methods.put(testClass, existedDeps);
        }
        return test2methods;
    }
    
    // // multi-threaded version
    // public static Set<String> getDepsHelper(Map<String, Set<String>> methodName2MethodNames, String testClass) {
    //     Set<String> visitedMethods = new TreeSet<>();
    //     //BFS
    //     ArrayDeque<String> queue = new ArrayDeque<>();

    //     //initialization
    //     for (String method : methodName2MethodNames.keySet()){
    //         if (method.startsWith(testClass+"#")){
    //             queue.add(method);
    //             visitedMethods.add(method);
    //         }
    //     }

    //     while (!queue.isEmpty()){
    //         String currentMethod = queue.pollFirst();
    //         for (String invokedMethod : methodName2MethodNames.getOrDefault(currentMethod, new HashSet<>())){
    //             if (!visitedMethods.contains(invokedMethod)) {
    //                 queue.add(invokedMethod);
    //                 visitedMethods.add(invokedMethod);
    //             }
    //         }
    //     }
    //     return visitedMethods;
    // }

    // public static Map<String, Set<String>> getDeps(Map<String, Set<String>> methodName2MethodNames, Set<String> testClasses) {
    //     Map<String, Set<String>> test2methods = new ConcurrentSkipListMap<>();
    //     ExecutorService service = null;
    //     try {
    //         service = Executors.newFixedThreadPool(16);
    //         for (final String testClass : testClasses)
    //         {
    //             service.submit(() -> {
    //                 Set<String> invokedMethods = getDepsHelper(methodName2MethodNames, testClass);
    //                 test2methods.put(testClass, invokedMethods);
    //             });
    //         }
    //         service.shutdown();
    //         service.awaitTermination(5, TimeUnit.SECONDS);
    //     } catch (Exception e) {
    //         throw new RuntimeException(e);
    //     }
    //     return test2methods;
    // }
}