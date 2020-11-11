package org.ekstazi.changelevel;

import org.ekstazi.asm.ClassReader;
import org.ekstazi.data.RegData;
import org.ekstazi.util.FileUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ChangeTypes implements Serializable, Comparable<ChangeTypes>{
    private static final long serialVersionUID = 1234567L;
    public transient static HashMap<String, Set<String>> hierarchyGraph;

    public TreeMap<String, String> constructorsMap;
    public TreeMap<String, String> instanceMethodMap;
    public TreeMap<String, String> staticMethodMap;
    public TreeMap<String, String> methodMap;
    public TreeMap<String, String> instanceFieldMap;
    public TreeMap<String, String> staticFieldMap;
    public HashMap<String, String> exceptionMap;
    public HashMap<String, String> annotations;
    public int classModifier;
    public String[] classInterfaces;
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
        exceptionMap = new HashMap<>();
        annotations = new HashMap<>();
        classInterfaces = new String[0];
        curClass = "";
        superClass = "";
        urlExternalForm = "";
    }

    /** Read the object from Base64 string. */
    public static Object fromString(String s) throws IOException,
            ClassNotFoundException {
        byte [] data = Base64.getDecoder().decode( s );
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(  data ) );
        Object o  = ois.readObject();
        ois.close();
        return o;
    }

    /** Write the object to a Base64 string. */
    public static String toString( Serializable o ) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream( baos );
        oos.writeObject( o );
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
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
            getHierarchyGraph(listFiles(System.getProperty("user.dir")+"/target/classes"));
            getHierarchyGraph(listFiles(System.getProperty("user.dir")+"/target/test-classes"));
        }

        boolean modified;

        TreeMap<String, String> newConstructor = this.constructorsMap;
        TreeMap<String, String> oldConstructor = other.constructorsMap;

        if (newConstructor.size() != oldConstructor.size()){
            return true;
        }

        for (String s : newConstructor.keySet()){
            if (!oldConstructor.keySet().contains(s) || !newConstructor.get(s).equals(oldConstructor.get(s))){
                return true;
            }
        }
        boolean hasHierarchy = false;
        String newCurClass = this.curClass;
        String oldCurClass = other.curClass;
        if (ChangeTypes.hierarchyGraph.containsKey(newCurClass) || ChangeTypes.hierarchyGraph.containsKey(oldCurClass)){
            hasHierarchy =  true;
        }
        modified = methodChange((TreeMap<String, String>) this.methodMap.clone(), (TreeMap<String, String>) other.methodMap.clone(), hasHierarchy);
        return modified;

    }

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

    public static void getHierarchyGraph(List<String> classPaths){
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

        }
        if (hierarchyGraph == null){
            hierarchyGraph = new HashMap<>();
        }
        hierarchyGraph.putAll(graph);

//        System.out.println("[log]hierarchyGraph: "+hierarchyGraph.keySet());
    }

    private boolean methodChange(TreeMap<String, String> newMethods, TreeMap<String, String> oldMethods, boolean hasHierarchy){
        Iterator<Map.Entry<String, String>> iterator = newMethods.entrySet().iterator();
        // Iterate over the HashMap
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (oldMethods.containsKey(entry.getKey())){
                if (!entry.getValue().equals( oldMethods.get(entry.getKey()) )){
                    return true;
                }else{
                    iterator.remove();
                    oldMethods.remove(entry.getKey());
                }
            } else{
                // rename
                if (oldMethods.containsValue(entry.getValue())){
                    iterator.remove();
                    AtomicReference<String> oldSig = null;
                    oldMethods.forEach((key, value) -> {
                        if (value.equals(entry.getValue())) {
                            oldSig.set(key);
                        }
                    });
                    oldMethods.remove(oldSig.get());
                }
            }
        }

        if (oldMethods.size() == 0 && newMethods.size() == 0){
            return false;
        }

        // one methodmap is empty then the left must be added or deleted.
        if (!hasHierarchy && (oldMethods.size() == 0 || newMethods.size() == 0)){
            return false;
        }

        return true;
    }

    public static void main(String[] args){
//        System.out.println("test");
//        ChangeTypes c1 = new ChangeTypes();
//        c1.methodMap.put("a", "a");
//        ChangeTypes c2 = new ChangeTypes();
//        c2.methodMap.put("b", "b");
//        Set<RegData> s = new TreeSet<>();
//        s.add(new RegData("a", c1));
//        s.add(new RegData("b", c2));
//        try {
//            FileOutputStream fos = new FileOutputStream("pipi.txt");
//            ObjectOutputStream oos= new ObjectOutputStream(fos);
//            oos.writeObject(s);
//            System.out.println("num of changetypes: "+s.size());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        try {
            FileInputStream fis = new FileInputStream("/Users/liuyu/pipiyu/ctaxonomy/ekstazi/_downloads/commons-codec/.ekstazi/org.apache.commons.codec.digest.B64Test.clz");
            Set<RegData> s1;
            ObjectInputStream ois=new ObjectInputStream(fis);
            s1=(TreeSet<RegData>)ois.readObject();
            System.out.println("num of changetypes: "+s1.size());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int compareTo(ChangeTypes o) {
        return this.urlExternalForm.compareTo(o.urlExternalForm);
    }
}