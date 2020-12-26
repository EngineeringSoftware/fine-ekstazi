package org.ekstazi.methods;

import org.ekstazi.asm.ClassReader;
import org.ekstazi.asm.ClassWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
    public static void main(String... args) throws Exception {
        // We need at least the argument that points to the root
        // directory where the search for .class files will start.
        if (args.length < 1) {
            throw new RuntimeException("Incorrect arguments");
        }
        String pathToStartDir = args[0];

        List<ClassReader> classReaderList = getClassReaders(pathToStartDir);

        // find the methods that each method calls
        Map<String, Set<String>> methodName2MethodNames = findMethodsinvoked(classReaderList);

        // TODO:find the method that each test class calls

        // save into a txt file ".ekstazi/methods.txt"
        saveClasses(methodName2MethodNames);
    }

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

    public static Map<String, Set<String>> findMethodsinvoked(List<ClassReader> classReaderList){
        Map<String, Set<String>> methodName2MethodNames = new HashMap<>();
        for (ClassReader classReader : classReaderList){
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
            MethodCallCollectorCV visitor = new MethodCallCollectorCV(classWriter, methodName2MethodNames);
            classReader.accept(visitor, ClassReader.SKIP_DEBUG);
        }
        return methodName2MethodNames;
    }

    private static void saveClasses(Map<String, Set<String>> methodName2MethodNames) throws Exception {
        File directory = new File(".ekstazi");
        directory.mkdir();

        File txtFile = new File(directory, "methods.txt");
        PrintWriter pw = new PrintWriter(txtFile);

        for (Map.Entry<String, Set<String>> en : methodName2MethodNames.entrySet()) {
            String methodName = en.getKey();
            //invokedMethods saved in csv format
            String invokedMethods = String.join(",", methodName2MethodNames.get(methodName));
            pw.println(methodName + " " + invokedMethods);
        }
        pw.flush();
        pw.close();
    }
}
