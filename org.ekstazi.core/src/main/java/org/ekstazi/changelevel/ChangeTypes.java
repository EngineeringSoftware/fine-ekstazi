package org.ekstazi.changelevel;

import org.ekstazi.asm.ClassReader;
import org.ekstazi.util.FileUtil;

import java.io.*;
import java.util.*;

public class ChangeTypes implements Serializable, Comparable<ChangeTypes>{
    private static final long serialVersionUID = 1234567L;
    public transient static HashMap<String, Set<String>> hierarchyGraph;
    public transient TreeMap<String, String> instanceFieldMap;
    public transient TreeMap<String, String> staticFieldMap;

    public TreeMap<String, String> constructorsMap;
    public Set<String> fieldList;
    public TreeMap<String, String> instanceMethodMap;
    public TreeMap<String, String> staticMethodMap;
    public String curClass = "";
    public String superClass = "";
    public String urlExternalForm = "";


    public ChangeTypes(){
        constructorsMap = new TreeMap<>();
        instanceMethodMap = new TreeMap<>();
        staticMethodMap = new TreeMap<>();
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
            throw new RuntimeException(e);
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
        modified = methodChange(this.instanceMethodMap, other.instanceMethodMap, hasHierarchy) || methodChange(this.staticMethodMap, other.staticMethodMap, hasHierarchy);
        return !modified;
    }

    public static void initHierarchyGraph(Set<String> classPaths){
        // subclass <-> superclasses
        HashMap<String, Set<String>> graph = new HashMap<>();
        try {
            for (String classPath : classPaths) {
                byte[] bytes = FileUtil.readFile(new File(classPath));
                ClassReader reader = new ClassReader(bytes);
                String curClassName = reader.getClassName();
                String superClassName = reader.getSuperName();
                if (superClassName == null || superClassName.equals("java/lang/Object")){
                    continue;
                }else{
                    Set<String> h = graph.getOrDefault(curClassName, new HashSet<>());
                    h.add(superClassName);
                    graph.put(curClassName, h);

                    h = graph.getOrDefault(superClassName, new HashSet<>());
                    h.add(curClassName);
                    graph.put(superClassName, h);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        if (hierarchyGraph == null){
            hierarchyGraph = new HashMap<>();
        }
        hierarchyGraph.putAll(graph);
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

    private boolean methodChange(TreeMap<String, String> oldMethodsPara, TreeMap<String, String> newMethodsPara, boolean hasHierarchy){
        TreeMap<String, String> oldMethods = new TreeMap<>(oldMethodsPara);
        TreeMap<String, String> newMethods = new TreeMap<>(newMethodsPara);
       
        Set<String> methodSig = new HashSet<>(oldMethods.keySet());
        methodSig.addAll(newMethods.keySet());
        for (String sig : methodSig){
            if (oldMethods.containsKey(sig) && newMethods.containsKey(sig)){
                if (oldMethods.get(sig).equals(newMethods.get(sig))) {
                    // remove sig
                    oldMethods.remove(sig);
                    newMethods.remove(sig);
                }else{
                    // different body
                    return true;
                }
            } else if (oldMethods.containsKey(sig) && newMethods.containsValue(oldMethods.get(sig))){
                // sig changes but method body does not change
                newMethods.values().remove(oldMethods.get(sig));
                oldMethods.remove(sig);
            } else if (newMethods.containsKey(sig) && oldMethods.containsValue(newMethods.get(sig))){
                // sig changes but method body does not change
                oldMethods.values().remove(newMethods.get(sig));
                newMethods.remove(sig);
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

    @Override
    public int compareTo(ChangeTypes o) {
        return this.urlExternalForm.compareTo(o.urlExternalForm);
    }

    @Override
    public String toString() {
        return "ChangeTypes{" +
                ", curClass='" + curClass + '\'' +
                ", constructorsMap=" + constructorsMap +
                ", instanceMethodsMap=" + instanceMethodMap +
                ", staticMethodMap=" + staticMethodMap +
                '}';
    }
}
