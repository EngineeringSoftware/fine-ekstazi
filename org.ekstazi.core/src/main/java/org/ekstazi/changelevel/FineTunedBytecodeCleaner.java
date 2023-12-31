package org.ekstazi.changelevel;

import org.ekstazi.asm.*;
import org.ekstazi.hash.Hasher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.ekstazi.util.FileUtil;

public class FineTunedBytecodeCleaner extends ClassVisitor {

    private TreeMap<String, String> constructorsMap = new TreeMap<>();
    private TreeMap<String, String> instanceMethodMap = new TreeMap<>();
    private TreeMap<String, String> staticMethodMap = new TreeMap<>();
    private TreeMap<String, String> instanceFieldMap = new TreeMap<>();
    private TreeMap<String, String> staticFieldMap = new TreeMap<>();
    private HashMap<String, String> exceptionMap = new HashMap<>();
    // Todo: If there is reflection, it is method level change
    private HashMap<String, String> annotations = new HashMap<>();
    private Set<String> fieldList = new HashSet<>();
    public HashSet<String> testMethodSig = new HashSet<>();
    private int classModifier;
    private String[] classInterfaces;
    private String superClass = "";
    private String curClass = "";

    public FineTunedBytecodeCleaner(int api, ClassVisitor cv) {
        super(api, cv);
    }

    // class name
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        classModifier = access;

        classInterfaces = interfaces;
        Arrays.sort(classInterfaces);

        curClass = name;
        superClass = superName;

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        FieldVisitor fv = cv.visitField(access, name, desc, signature, value);
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        fieldList.add(name + desc + value);
        if (isStatic) {
            staticFieldMap.put(name + desc, " ");
        } else {
            instanceFieldMap.put(name + desc, " ");
        }
        return fv;

        // todo field annotation
        // Printer p = new CleanCodeUtil(Opcodes.ASM6) {
        // @Override
        // public void visitFieldEnd() {
        // StringWriter sw = new StringWriter();
        // print(new PrintWriter(sw));
        // getText();
        // String field = sw.toString();
        // annotations.add(field);
        // super.visitFieldEnd();
        // }
        // };
        // return new FineTunedFieldVisitor(fv, p);

    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);

        Printer p = new CleanCodeUtil() {
            @Override
            public void visitMethodEnd() {
                StringWriter sw = new StringWriter();
                print(new PrintWriter(sw));
                getText().clear();

                String methodBody = sw.toString();
                String methodSignature = name + desc;

                List<String> excep = new ArrayList<>();
                if (exceptions != null) {
                    for (String el : exceptions) {
                        excep.add(el);
                    }
                }
                Collections.sort(excep);
                exceptionMap.put(name + desc, hashString(excep.toString()));

                if ((name + desc).equals("<clinit>()V")) {
                    // initialize static field
                    constructorsMap.put(methodSignature, hashSortedString(methodBody));
                    // // todo: Finding 29, change of constructor is a method level change
                    // staticMethodMap.put(methodSignature, hashSortedString(methodBody));
                } else {
                    boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                    if (isStatic) {
                        staticMethodMap.put(methodSignature, hashString(methodBody));
                    } else {
                        if (methodSignature.startsWith("<init>")) {
                            constructorsMap.put(methodSignature, hashSortedString(methodBody));
                            // // todo: Finding 29, change of constructor is a method level change
                            // instanceMethodMap.put(methodSignature, hashSortedString(methodBody));
                        } else {
                            instanceMethodMap.put(methodSignature, hashString(methodBody));
                        }
                    }
                }
            }
        };

        return new FineTunedMethodVisitor(mv, p, name + desc);
    }

    // @Override
    // public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    //     AnnotationVisitor av = cv.visitAnnotation(desc, visible);
    //     Printer p = new CleanCodeUtil() {
    //         // class annotation
    //         @Override
    //         public void visitAnnotationEnd() {
    //             if (visible) {
    //                 // todo: runtime annotation of class
    //                 // StringWriter sw = new StringWriter();
    //                 // print(new PrintWriter(sw));
    //                 // getText().clear();
    //                 // String annotation = sw.toString();
    //                 // if (!annotation.equals(""))
    //                 // annotations.add(annotation);
    //             }
    //         }
    //     };
    //     return new TraceAnnotationVisitor(av, p);
    // }

    class FineTunedFieldVisitor extends TraceFieldVisitor {
        public FineTunedFieldVisitor(FieldVisitor fv, Printer p) {
            super(fv, p);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String desc,
                final boolean visible) {
            AnnotationVisitor av = fv == null ? null
                    : fv.visitAnnotation(desc,
                            visible);
            if (visible) {
                return new FineTunedAnnotationVisitor(av, p);
            }
            return av;
        }
    }

    class FineTunedMethodVisitor extends TraceMethodVisitor {
        String sig;

        public FineTunedMethodVisitor(MethodVisitor mv, Printer p) {
            super(mv, p);
        }

        public FineTunedMethodVisitor(MethodVisitor mv, Printer p, String sig) {
            super(mv, p);
            this.sig = sig;
        }

        // @Override
        // public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        // if(visible){uyitfdd
        // if (desc.equals("Lorg/junit/Test;"))
        // testMethodSig.add(sig);
        // }
        // return super.visitAnnotation(desc, visible);
        // }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            if (visible) {
                return new FineTunedAnnotationVisitor(p);
            }
            return super.visitParameterAnnotation(parameter, desc, visible);
        }

    }

    static class FineTunedAnnotationVisitor extends TraceAnnotationVisitor {
        public Printer p;

        public FineTunedAnnotationVisitor(AnnotationVisitor av, Printer p) {
            super(av, p);
            this.p = p;
        }

        public FineTunedAnnotationVisitor(Printer p) {
            super(p);
            this.p = p;
        }

        @Override
        public void visit(String name, Object value) {
            p.buf.setLength(0);
            p.buf.append(name + value);
            p.text.add(p.buf.toString());
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    public ChangeTypes getChangeTypes() {
        ChangeTypes c = new ChangeTypes();
        c.constructorsMap = this.constructorsMap;
        c.instanceMethodMap = this.instanceMethodMap;
        c.staticMethodMap = this.staticMethodMap;
        c.instanceFieldMap = this.instanceFieldMap;
        c.staticFieldMap = this.staticFieldMap;
        c.curClass = this.curClass;
        c.superClass = this.superClass;
        c.fieldList = this.fieldList;
        return c;
    }

    public TreeMap getConstructorsMap() {
        return constructorsMap;
    }

    public TreeMap getInstanceMethodMap() {
        return instanceMethodMap;
    }

    public TreeMap getStaticMethodMap() {
        return staticMethodMap;
    }

    public String[] getClassInterfaces() {
        return classInterfaces;
    }

    public String getSuperClass() {
        return superClass;
    }

    public int getClassModifier() {
        return classModifier;
    }

    public TreeMap getInstanceFieldMap() {
        return instanceFieldMap;
    }

    public TreeMap getStaticFieldMap() {
        return staticFieldMap;
    }

    public HashMap<String, String> getAnnotations() {
        return annotations;
    }

    public HashMap getExceptionMap() {
        return exceptionMap;
    }

    private static String hashSortedString(String str){
        // hashByteArray
        byte[] bytes = str.getBytes();
        Arrays.sort(bytes);
        return String.valueOf(Hasher.hashString(new String(bytes)));
        // return str.chars() // IntStream
        //         .sorted().collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    private String hashString(String str){
        return String.valueOf(Hasher.hashString(str));
    }

    private String sortedString(String str) {
        return str.chars() // IntStream
                .sorted().collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    public static ChangeTypes removeDebugInfo(byte[] bytes) {
        ChangeTypes c = new ChangeTypes();
        if (bytes.length >= 4) {
            // Check magic number.
            int magic = ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16)
                    | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
            if (magic != 0xCAFEBABE)
                return c;
        } else {
            return c;
        }

        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            FineTunedBytecodeCleaner visitor = new FineTunedBytecodeCleaner(Opcodes.ASM6, writer);
            reader.accept(visitor, ClassReader.SKIP_DEBUG);
            c = visitor.getChangeTypes();
            return c;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static String INNER_ACCESS = "add_access"; // 1

    static String ADD_BASE_CLASS = "add_base_class"; // 2
    static String ADD_CLASS = "add_class"; // 3
    static String REMOVE_CLASS = "remove_class"; // 19
    static String CHANGE_BASE_CLASS = "change_base_class"; // 10
    static String CHANGE_CLASS_MODIFIER = "change_class_modifier"; // 11

    static String ADD_CONSTRUCTOR = "add_constructor"; // 4
    static String REMOVE_CONSTRUCTOR = "remove_constructor";// 23

    static String ADD_FIELD = "add_field"; // 5
    static String REMOVE_FIELD = "remove_field"; // 21
    static String CHANGE_MODIFIER_OF_FIELD = "change_modifier_of_field"; // 9

    static String ADD_INSTANCE_METHOD = "add_instance_method"; // 6
    static String REMOVE_INSTANCE_METHOD = "remove_instance_method"; // 22
    static String ADD_STATIC_METHOD = "add_static_method"; // 8
    static String REMOVE_STATIC_METHOD = "remove_static_method"; // 20
    static String CHANGE_INTERFACE = "change_interface"; // 16
    static String CHANGE_METHOD_EXCEPTION = "change_method_exception"; // 17
    static String PARAMETER_TYPE_SPECIALIZATION = "parameter_type_specialization"; // 18

    static String ADD_RUNTIME_ANNOTATION = "add_runtime_annotation"; // 7
    static String ADD_STATIC_INITIALIZED_BLOCK = "add_static_initialized_block";

    static String UPDATE_CONSTRUCTOR = "update_constructor_or_update_field_initialization"; // 12
    static String CHANGE_FIELD_INITIALIZATION = "change_field_initialization"; // 14
    static String CHANGE_FIELD_DECLARATION = "change_field_declaration"; // 13

    static String CHANGE_RUNTIME_ANNOTATION = "add_runtime_annotation"; // 15
    static String USE_LAMBDA = "use_lambda"; // 24
    static String CHANGE_SIGNATURE = "change_signature";
    static String METHOD = "method";
    static String OTHER = "other";
    static String NOCHANGE = "nochange";

    // invoke bash in java
    public static String bashCommand(String path, String command) {
        String[] commands = { "bash", "-c", "cd " + path + ";" + command };
        try {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec(commands);
            p.waitFor();
            BufferedReader error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String str = error.lines().collect(Collectors.joining());
            if (!str.equals("")) {
                System.out.println("error info: " + str);
            }
            BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line = "";
            while ((line = b.readLine()) != null) {
                sb.append(line + "\n");
            }
            b.close();
            return sb.toString();
        } catch (Exception e) {
            System.err.println("Failed to execute bash with command: " + command);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static Set<String> getChangeLevel(String preClassPath, String curClassPath) {
        Set<String> res = new HashSet<>();
        File pref = new File(preClassPath);
        File curf = new File(curClassPath);
        if (!pref.exists()) { // class file from previous build does not exist
            res.add(ADD_CLASS);
            return res;
        } else if (!curf.exists()) { // class file from current build does not exist
            res.add(REMOVE_CLASS);
            return res;
        } else {
            try {
                byte[] preBytes = FileUtil.readFile(new File(preClassPath));
                byte[] curBytes = FileUtil.readFile(new File(curClassPath));
                byte[] preCleanBytes = org.ekstazi.hash.BytecodeCleaner.removeDebugInfo(preBytes);
                byte[] curCleanBytes = org.ekstazi.hash.BytecodeCleaner.removeDebugInfo(curBytes);

                if (Arrays.equals(preCleanBytes, curCleanBytes)) { // if the bytecode from previous class and current
                    // class file is the same
                    res.add(OTHER);
                    return res;
                }

                ChangeTypes preChangeTypes = removeDebugInfo(preBytes);
                ChangeTypes curChangeTypes = removeDebugInfo(curBytes);

                if (!preChangeTypes.superClass.equals(curChangeTypes.superClass)) {
                    res.add(CHANGE_BASE_CLASS);
                }

                Set<String> preFieldList = new HashSet<>(preChangeTypes.fieldList);
                Set<String> curFieldList = new HashSet<>(curChangeTypes.fieldList);
                // TODO: commons-codec: b542cf9d

                for (String preField : preChangeTypes.fieldList) {
                    curFieldList.remove(preField);
                }
                for (String curField : curChangeTypes.fieldList) {
                    preFieldList.remove(curField);
                }

                System.out.println("preField: " + preFieldList);
                System.out.println("curField: " + curFieldList);
                if (preFieldList.size() > 0 || curFieldList.size() > 0) {
                    if (preFieldList.size() == 0) {
                        res.add(ADD_FIELD);
                    } else if (curFieldList.size() == 0) {
                        res.add(REMOVE_FIELD);
                    } else {
                        res.add(CHANGE_FIELD_DECLARATION);
                    }
                }

                TreeMap<String, String> newConstructor = preChangeTypes.constructorsMap;
                TreeMap<String, String> oldConstructor = curChangeTypes.constructorsMap;

                if (newConstructor.size() != oldConstructor.size()) {
                    res.add(UPDATE_CONSTRUCTOR);
                }

                for (String s : newConstructor.keySet()) {
                    if (!oldConstructor.keySet().contains(s) || !newConstructor.get(s).equals(oldConstructor.get(s))) {
                        res.add(UPDATE_CONSTRUCTOR);
                    }
                }

                System.out.println("class: " + curClassPath);

                System.out.println("pre instance method: " + preChangeTypes.instanceMethodMap.size());
                System.out.println("cur instance method: " + curChangeTypes.instanceMethodMap.size());
                List<String> instanceMethodChange = methodChange(preChangeTypes.instanceMethodMap,
                        curChangeTypes.instanceMethodMap, false);
                if (instanceMethodChange.size() > 0)
                    res.addAll(instanceMethodChange);
                System.out.println("pre static method: " + preChangeTypes.staticMethodMap.size());
                System.out.println("cur static method: " + curChangeTypes.staticMethodMap.size());
                List<String> staticMethodChange = methodChange(preChangeTypes.staticMethodMap,
                        curChangeTypes.staticMethodMap, true);
                if (staticMethodChange.size() > 0) {
                    res.addAll(staticMethodChange);
                }

            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        if (res.size() == 0) {
            res.add(NOCHANGE);
        }
        return res;
    }

    public static List<String> methodChange(TreeMap<String, String> oldMethods, TreeMap<String, String> newMethods,
            boolean isStatic) {
        List<String> res = new ArrayList<>();
        Set<String> methodSig = new HashSet<>(oldMethods.keySet());
        methodSig.addAll(newMethods.keySet());
        for (String sig : methodSig) {
            if (oldMethods.containsKey(sig) && newMethods.containsKey(sig)) {
                // if (oldMethods.get(sig).equals(newMethods.get(sig))) {
                // oldMethods.remove(sig);
                // newMethods.remove(sig);
                // }else{
                // res.add(METHOD);
                // }
                if (!oldMethods.get(sig).equals(newMethods.get(sig))) {
                    res.add(METHOD + " " + sig);
                }
                oldMethods.remove(sig);
                newMethods.remove(sig);
            } else if (oldMethods.containsKey(sig) && newMethods.containsValue(oldMethods.get(sig))) {
                newMethods.values().remove(oldMethods.get(sig));
                oldMethods.remove(sig);
                System.out.println("change sig: " + sig);
                res.add(CHANGE_SIGNATURE);
            } else if (newMethods.containsKey(sig) && oldMethods.containsValue(newMethods.get(sig))) {
                oldMethods.values().remove(newMethods.get(sig));
                newMethods.remove(sig);
                System.out.println("change sig: " + sig);
                res.add(CHANGE_SIGNATURE);
            }
        }

        if (oldMethods.size() == 0 && newMethods.size() == 0) {
            return res;
        }

        System.out.println("isStatic: " + isStatic);
        System.out.println("old method size: " + oldMethods.size());
        System.out.println("new method size: " + newMethods.size());

        // one methodmap is empty then the left must be added or deleted.
        if (oldMethods.size() == 0) {
            if (!isStatic) {
                res.add(ADD_INSTANCE_METHOD);
                return res;
            } else {
                res.add(ADD_STATIC_METHOD);
                return res;
            }
        }

        if (newMethods.size() == 0) {
            if (!isStatic) {
                res.add(REMOVE_INSTANCE_METHOD);
                return res;
            } else {
                res.add(REMOVE_STATIC_METHOD);
                return res;
            }
        }

        if (oldMethods.size() > 0 && newMethods.size() > 0) {
            if (!isStatic) {
                System.out.println("change instance method");
                res.add(REMOVE_INSTANCE_METHOD);
                res.add(ADD_INSTANCE_METHOD);
                return res;
            } else {
                res.add(REMOVE_STATIC_METHOD);
                res.add(ADD_STATIC_METHOD);
                return res;
            }
        }

        return res;
    }

    // this method is used to classify change levels automatically
    // in folderorg.ekstazi.core: mvn exec:java -Dexec.mainClass="org.ekstazi.changelevel.FineTunedBytecodeCleaner"
    public static void main(String[] args) {
        for (String project : Macros.projectList) {
            String folderName = Macros.projectFolderPath + "/" + "_downloads";
            File folder = new File(folderName);
            if (!folder.isDirectory()) {
                bashCommand(".", "mkdir " + folderName);
            }
            String projectPath = folderName + "/" + project.replace("/", "_");
            System.out.println(projectPath);
            File projectFolder = new File(projectPath);
            if (!projectFolder.isDirectory()) {
                bashCommand(folderName, "git clone https://github.com/" + project + " " + projectPath);
            }

            File resultFolder = new File(Macros.resultFolderPath);
            if (!resultFolder.exists() || !resultFolder.isDirectory()) {
                resultFolder.mkdirs();
            }
            // use json to store the result, here use the library Gson
            Map<String, Map<String, Set<String>>> resLinkedHashMap = new LinkedHashMap<>();
            Gson gson = new GsonBuilder()
                    .disableHtmlEscaping()
                    .setPrettyPrinting()
                    .create();
            // get previous shas
            String filename = project.replace("/", "_") + ".json";
            String path = Macros.resultFolderPath + "/shas_with_java_files_change/" + filename;
            BufferedReader bufferedReader = null;
            String[] shalist = null;
            try {
                bufferedReader = new BufferedReader(new FileReader(path));
                shalist = gson.fromJson(bufferedReader, String[].class);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            // String sha = Macros.projectList.get(project);
            // String[] shalist = bashCommand(projectPath, "git rev-list --first-parent -n "
            // + (Macros.numSHA + 1) + " " + sha + " | tac").split("\n");
            String preSHA = shalist[0];

            for (int i = 1; i < shalist.length; i++) {
                Map<String, Set<String>> jsonObject = new LinkedHashMap<>();
                String curSHA = shalist[i];
                System.out.println(curSHA);
                // compare the difference of previous class file and current class file
                bashCommand(projectPath, "git checkout " + preSHA);
                String preCompileResult = bashCommand(projectPath, "mvn test-compile -Drat.skip");
                bashCommand(projectPath, "cp -rf target/classes preclasses");
                bashCommand(projectPath, "cp -rf target/test-classes pretestclasses");
                bashCommand(projectPath, "git clean -f");
                bashCommand(projectPath, "rm -rf target");
                bashCommand(projectPath, "git checkout " + curSHA);
                String curCompileResult = bashCommand(projectPath, "mvn clean test-compile " + Macros.SKIPS);
                if (curCompileResult.contains("BUILD FAILURE")) {
                    bashCommand(projectPath,
                            preSHA + " " + curSHA + "\n >> " + Macros.resultFolderPath + "/build_failure.log");
                    bashCommand(projectPath, "rm -rf preclasses");
                    bashCommand(projectPath, "rm -rf pretestclasses");
                    bashCommand(projectPath, "rm -rf target");
                    continue;
                }

                File curClassFolder = new File(projectPath + "/target/classes");
                File curTestFolder = new File(projectPath + "/target/test-classes");

                if (ChangeTypes.hierarchyGraph == null) {
                    ChangeTypes.initHierarchyGraph(FileUtil.listFiles(projectPath));
                    // System.out.println(ChangeTypes.hierarchyGraph);
                }

                for (String curClassPath : FileUtil.listFiles(curClassFolder.getAbsolutePath())) {
                    int index = curClassPath.lastIndexOf("target/classes");
                    String fileRelativePath = curClassPath.substring(index + "target/classes/".length());
                    String preClassPath = projectPath + "/preclasses/" + fileRelativePath;
                    Set<String> level = getChangeLevel(preClassPath, curClassPath);
                    if (level.contains(OTHER)) {
                        continue;
                    }
                    jsonObject.put(fileRelativePath, level);
                }

                for (String curClassPath : FileUtil.listFiles(curTestFolder.getAbsolutePath())) {
                    int index = curClassPath.lastIndexOf("target/test-classes");
                    String fileRelativePath = curClassPath.substring(index + "target/test-classes/".length());
                    String preClassPath = projectPath + "/pretestclasses/" + fileRelativePath;
                    Set<String> level = getChangeLevel(preClassPath, curClassPath);
                    if (level.contains(OTHER)) {
                        continue;
                    }
                    jsonObject.put(fileRelativePath, level);
                }

                resLinkedHashMap.put(curSHA, jsonObject);
                System.out.println(jsonObject);
                bashCommand(projectPath, "rm -rf preclasses");
                bashCommand(projectPath, "rm -rf pretestclasses");
                bashCommand(projectPath, "rm -rf target");
                preSHA = curSHA;
            }
            // generate json file
            String res = gson.toJson(resLinkedHashMap, LinkedHashMap.class);
            System.out.println(res);
            try {
                Files.write(Paths.get(Macros.resultFolderPath + "/change_levels/" + filename), res.getBytes());
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

}