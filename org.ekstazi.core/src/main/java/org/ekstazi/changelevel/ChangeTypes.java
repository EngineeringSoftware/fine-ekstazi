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

import static org.ekstazi.changelevel.FineTunedBytecodeCleaner.removeDebugInfo;

public class ChangeTypes implements Serializable, Comparable<ChangeTypes>{
    private static final long serialVersionUID = 1234567L;
    public transient static HashMap<String, Set<String>> hierarchyGraph;
    public transient TreeMap<String, String> instanceFieldMap;
    public transient TreeMap<String, String> staticFieldMap;
    public transient TreeMap<String, String> instanceMethodMap;
    public transient TreeMap<String, String> staticMethodMap;

    public TreeMap<String, String> constructorsMap;
    public TreeMap<String, String> methodMap;
    public Set<String> fieldList;
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
        fieldList = new HashSet<>();
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

    public static ChangeTypes fromFile(String fileName) throws IOException,ClassNotFoundException{
        ChangeTypes c = null;
        FileInputStream fileIn = new FileInputStream(fileName);
        ObjectInputStream in = new ObjectInputStream(fileIn);
        c= (ChangeTypes) in.readObject();
        in.close();
        fileIn.close();
        return c;
    }

    public static void toFile(String fileName, ChangeTypes c){
        try {
            File file = new File(fileName);
            if (!file.exists()){
                File dir = new File(file.getParent());
                dir.mkdirs();
                file.createNewFile();
            }
            FileOutputStream fileOut =
                    new FileOutputStream(file);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(c);
            out.close();
            fileOut.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
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

        // field changes
        if (!sortedString(fieldList.toString()).equals(sortedString(other.fieldList.toString()))){
            return false;
        }

        TreeMap<String, String> newConstructor = this.constructorsMap;
        TreeMap<String, String> oldConstructor = other.constructorsMap;

        if (newConstructor.size() != oldConstructor.size()){
            return false;
        }

        for (String s : newConstructor.keySet()){
            if (!oldConstructor.keySet().contains(s) || !sortedString(newConstructor.get(s)).equals(sortedString(oldConstructor.get(s)))){
                return false;
            }
        }

        // if there is method change
        boolean hasHierarchy = false;
        String newCurClass = this.curClass;
        String oldCurClass = other.curClass;
        if (ChangeTypes.hierarchyGraph.containsKey(newCurClass) || ChangeTypes.hierarchyGraph.containsKey(oldCurClass)){
            hasHierarchy =  true;
        }
        modified = methodChange((TreeMap<String, String>) this.methodMap.clone(), (TreeMap<String, String>) other.methodMap.clone(), hasHierarchy);
        return !modified;

    }

    public String sortedString(String str){
        return  str.chars() // IntStream
                .sorted().collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
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
        Set<String> methodSig = new HashSet<>(oldMethods.keySet());
        methodSig.addAll(newMethods.keySet());
        for (String sig : methodSig){
            if (oldMethods.containsKey(sig) && newMethods.containsKey(sig)){
                if (oldMethods.get(sig).equals(newMethods.get(sig))) {
                    oldMethods.remove(sig);
                    newMethods.remove(sig);
                }else{
                    return true;
                }
            } else if (oldMethods.containsKey(sig) && newMethods.containsValue(oldMethods.get(sig))){
                newMethods.values().remove(oldMethods.get(sig));
                oldMethods.remove(sig);
            } else if (newMethods.containsKey(sig) && oldMethods.containsValue(newMethods.get(sig))){
                oldMethods.values().remove(newMethods.get(sig));
                newMethods.remove(sig);
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
        String filePath = "/Users/liuyu/pipiyu/finerts/org.ekstazi.core/src/main/java/org/ekstazi/changelevel/A.class";
        try {
            byte[] array = Files.readAllBytes(Paths.get(filePath));
            ChangeTypes preChangeTypes = removeDebugInfo(array);
            System.out.println(preChangeTypes.methodMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int compareTo(ChangeTypes o) {
        return this.urlExternalForm.compareTo(o.urlExternalForm);
    }
}
