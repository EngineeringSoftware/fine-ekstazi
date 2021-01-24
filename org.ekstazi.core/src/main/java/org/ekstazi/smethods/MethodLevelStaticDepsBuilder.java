package org.ekstazi.smethods;

import org.ekstazi.asm.ClassReader;
import org.ekstazi.data.Storer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.ekstazi.smethods.MethodLevelSelection.getInvokedConstructorsMap;


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

        List<ClassReader> classReaderList = getClassReaders(pathToStartDir);

        // find the methods that each method calls
        findMethodsinvoked(classReaderList);

        // suppose that test classes have Test in their class name
        Set<String> testClasses = new HashSet<>();
        for (String method : methodName2MethodNames.keySet()){
            String className = method.split("#")[0];
            if (className.contains("Test")){
                testClasses.add(className);
            }
        }

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
            Set<String> classesInConstantPool = ConstantPoolParser.getClassNames(ByteBuffer.wrap(classReader.b));
            //TODO: not keep methodName2MethodNames, hierarchies as fields
            MethodCallCollectorCV visitor = new MethodCallCollectorCV(methodName2MethodNames, hierarchy_parents, hierarchy_children, class2ContainedMethodNames, classesInConstantPool);
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
        File directory = new File(".ekstazi");
        directory.mkdir();

        File txtFile = new File(directory, fileName);
        PrintWriter pw = new PrintWriter(txtFile);

        for (Map.Entry<String, Set<String>> en : mapToStore.entrySet()) {
            String methodName = en.getKey();
            //invokedMethods saved in csv format
            String invokedMethods = String.join(",", mapToStore.get(methodName));
            pw.println(methodName + " " + invokedMethods);
            pw.println();
        }
        pw.flush();
        pw.close();
    }

    public static void saveSet(Set<String> setToStore, String fileName) throws Exception {
        File directory = new File(".ekstazi");
        directory.mkdir();

        File txtFile = new File(directory, fileName);
        PrintWriter pw = new PrintWriter(txtFile);

        for (String s : setToStore) {
            pw.println(s);
        }
        pw.flush();
        pw.close();
    }

    public static Map<String, Set<String>> getDeps(Map<String, Set<String>> methodName2MethodNames, Set<String> testClasses){
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
                for (String invokedMethod : methodName2MethodNames.getOrDefault(currentMethod, new HashSet<>())){
                    if (!visitedMethods.contains(invokedMethod)) {
                        queue.add(invokedMethod);
                        visitedMethods.add(invokedMethod);
                    }
                }
            }
            testClass = testClass.split("\\$")[0];
            Set<String> existedMethods = test2methods.getOrDefault(testClass, new TreeSet<>());
            existedMethods.addAll(visitedMethods);
            test2methods.put(testClass, existedMethods);
        }
        return test2methods;
    }

}