package org.ekstazi.changelevel;

import org.ekstazi.Names;
import org.ekstazi.asm.ClassReader;
import org.ekstazi.util.FileUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.ekstazi.changelevel.FineTunedBytecodeCleaner.removeDebugInfo;

public class ChangeTypes implements Serializable, Comparable<ChangeTypes>{
    private static final long serialVersionUID = 1234567L;
    public transient static HashMap<String, Set<String>> hierarchyGraph;
    public transient TreeMap<String, String> instanceFieldMap;
    public transient TreeMap<String, String> staticFieldMap;
    public transient TreeMap<String, String> instanceMethodMap;
    public transient TreeMap<String, String> staticMethodMap;
    public static transient HashSet<String> testClasses;

    public TreeMap<String, String> constructorsMap;
    public TreeMap<String, String> methodMap;
    public Set<String> fieldList;
    public String curClass = "";
    public String superClass = "";
    public String urlExternalForm = "";


    public ChangeTypes(){
        constructorsMap = new TreeMap<>();
        instanceMethodMap = new TreeMap<>();
        staticMethodMap = new TreeMap<>();
        methodMap = new TreeMap<>();
        instanceFieldMap = new TreeMap<>();
        staticFieldMap = new TreeMap<>();
        fieldList = new HashSet<>();
        curClass = "";
        superClass = "";
        urlExternalForm = "";
    }

    public static ChangeTypes fromFile(String fileName){
        ChangeTypes c = null;
        FileInputStream fileIn = null;
        if (!new File(fileName).exists()) {
            return null;
        }
        try {
            fileIn = new FileInputStream(fileName);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            c= (ChangeTypes) in.readObject();
            in.close();
            fileIn.close();
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
            return c;
        }
        return c;
    }

    public static void toFile(String fileName, ChangeTypes c){
        try {
            File file = new File(fileName);
            if (!file.exists()){
                File dir = new File(file.getParent());
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            } else {
                file.delete();
            }
            FileOutputStream fileOut =
                    new FileOutputStream(file);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(c);
            out.close();
            fileOut.close();
        } catch (IOException i) {
            throw new RuntimeException(i);
        }
    }

    @Override
    public boolean equals(Object obj){
        if (obj == null) {
            return false;
        }

        if (obj.getClass() != this.getClass()) {
            return false;
        }

        final ChangeTypes other = (ChangeTypes) obj;

        if (hierarchyGraph == null){
            getHierarchyGraph();
        }

        boolean modified;

        if (fieldChange(fieldList, other.fieldList)){
            return false;
        }

        TreeMap<String, String> newConstructor = other.constructorsMap;
        TreeMap<String, String> oldConstructor = this.constructorsMap;

        if (newConstructor.size() != oldConstructor.size()){
            return false;
        }

        // constructor changes
        for (String s : newConstructor.keySet()){
            if (!oldConstructor.containsKey(s) || !newConstructor.get(s).equals(oldConstructor.get(s))){
                return false;
            }
        }

        // if there is method change
        boolean hasHierarchy = false;
        String newCurClass = other.curClass;
        String oldCurClass = this.curClass;
        if (ChangeTypes.hierarchyGraph.containsKey(newCurClass) || ChangeTypes.hierarchyGraph.containsKey(oldCurClass)){
            hasHierarchy =  true;
        }
        // if (testClasses == null){
        //     testClasses = listTestClasses();
        // }
        modified = methodChange((TreeMap<String, String>) this.methodMap.clone(), (TreeMap<String, String>) other.methodMap.clone(), hasHierarchy);
        return !modified;
    }

    // public HashSet<String> listTestClasses(){
    //     HashSet<String> testClasses = new HashSet<>();
    //     File folder = new File(System.getProperty("user.dir") + "/" + Names.EKSTAZI_ROOT_DIR_NAME);
    //     for (final File fileEntry : folder.listFiles()) {
    //         String fileName = fileEntry.getName();
    //         if (fileEntry.isFile() && fileName.endsWith(".clz")) {
    //             testClasses.add(fileName.substring(0, fileName.length()-4).replace(".", "/"));
    //         }
    //     }
    //     return testClasses;
    // }

    public static List<String> listFiles(String dir) {
        List<String> res = new ArrayList<>();
        try {
            List<Path> pathList =  Files.find(Paths.get(dir), 999, (p, bfa) -> bfa.isRegularFile())
                    .collect(Collectors.toList());
            for(Path filePath : pathList){
                if(!filePath.getFileName().toString().endsWith("class")){
                    continue;
                }
                String curClassPath = filePath.getParent().toString()+"/"+filePath.getFileName().toString();
                res.add(curClassPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    public static void getHierarchyGraph(){
        // Find all .class files in the given directory.
        try {
            List<Path> classPaths = Files.walk(Paths.get("."))
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".class") && f.toString().contains("target"))
                    .collect(Collectors.toList());
            // subclass <-> superclasses
            HashMap<String, Set<String>> graph = new HashMap<>();
                for (Path classPath : classPaths) {
                    byte[] bytes = FileUtil.readFile(classPath.toFile());
                    ClassReader reader = new ClassReader(bytes);
                    String curClassName = reader.getClassName();
                    String superClassName = reader.getSuperName();
                    if (superClassName != null && !Objects.equals(superClassName, "java/lang/Object")) {
                        Set<String> h = graph.getOrDefault(curClassName, new HashSet<>());
                        h.add(superClassName);
                        graph.put(curClassName, h);

                        h = graph.getOrDefault(superClassName, new HashSet<>());
                        h.add(curClassName);
                        graph.put(superClassName, h);
                    }
                }
                if (hierarchyGraph == null){
                    hierarchyGraph = new HashMap<>();
                }
                hierarchyGraph.putAll(graph);
            }catch(Exception e){
                e.printStackTrace();
            }
    }

    private boolean fieldChange(Set<String> newFields, Set<String> oldFields){
        Set<String> preFieldList = new HashSet<>(oldFields);
        Set<String> curFieldList = new HashSet<>(newFields);

        for (String preField : oldFields){
            curFieldList.remove(preField);
        }
        for (String curField : newFields){
            preFieldList.remove(curField);
        }

        if (preFieldList.size() == 0 || curFieldList.size() == 0){
            return false;
        }else{
            return true;
        }
    }

    private boolean methodChange(TreeMap<String, String> oldMethods, TreeMap<String, String> newMethods, boolean hasHierarchy){
        Set<String> methodSig = new HashSet<>(oldMethods.keySet());
        methodSig.addAll(newMethods.keySet());
        for (String sig : methodSig){
            if (oldMethods.containsKey(sig) && newMethods.containsKey(sig)){
                if (oldMethods.get(sig).equals(newMethods.get(sig))) {
//                    System.out.println("remove sig: " + sig);
                    oldMethods.remove(sig);
                    newMethods.remove(sig);
                }else{
//                    System.out.println("different sig: " + sig);
                    return true;
                }
            } else if (oldMethods.containsKey(sig) && newMethods.containsValue(oldMethods.get(sig))){
                newMethods.values().remove(oldMethods.get(sig));
                oldMethods.remove(sig);
//                System.out.println("sig changes but method body does not change: " + sig);
            } else if (newMethods.containsKey(sig) && oldMethods.containsValue(newMethods.get(sig))){
                oldMethods.values().remove(newMethods.get(sig));
                newMethods.remove(sig);
//                System.out.println("sig changes but method body does not change: " + sig);
            }
        }

        if (oldMethods.size() == 0 && newMethods.size() == 0){
            return false;
        }

        // one methodmap is empty then the left must be added or deleted.
        if (!hasHierarchy && (oldMethods.size() == 0 || newMethods.size() == 0)){
            // the class is test class and the test class adds/revises test methods
            // if (testClasses.contains(this.curClass) && newMethods.size()>0){
            if (this.curClass.contains("Test") && newMethods.size()>0){
                return true;
            }
            return false;
        }
        return true;
    }

    public static void main(String[] args){
        String filePath = "/Users/liuyu/pipiyu/finerts/org.ekstazi.core/src/main/java/org/ekstazi/changelevel/A.class";
        try {
            byte[] array = Files.readAllBytes(Paths.get(filePath));
            ChangeTypes preChangeTypes = removeDebugInfo(array);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int compareTo(ChangeTypes o) {
        return this.urlExternalForm.compareTo(o.urlExternalForm);
    }
}
